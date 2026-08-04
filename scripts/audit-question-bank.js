#!/usr/bin/env node
/**
 * Question bank quality gate.
 * Usage:
 *   node scripts/audit-question-bank.js
 *   node scripts/audit-question-bank.js /path/to/openexam.db
 *   node scripts/audit-question-bank.js --json
 */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Database = require('better-sqlite3');
const {
  hasUsableMaterial,
  isAnswerCompatible,
  isPracticeableQuestion,
  parseOptions,
} = require('../src/shared/questionQuality');

const ROOT = path.join(__dirname, '..');
const SEED_GZ = path.join(ROOT, 'data/openexam.seed.db.gz');
const ASSET_DIR = path.join(ROOT, 'data/question-assets');
const ASSET_RE = /openexam-asset:\/\/question-assets\/([A-Za-z0-9._\-]+)/gi;

function openSeedDb() {
  const tmp = path.join(ROOT, 'data/.audit-openexam.seed.db');
  fs.writeFileSync(tmp, zlib.gunzipSync(fs.readFileSync(SEED_GZ)));
  return { db: new Database(tmp, { readonly: true }), tmp };
}

function resolveAsset(name) {
  const exact = path.join(ASSET_DIR, name);
  if (fs.existsSync(exact)) return exact;
  const stem = path.parse(name).name;
  const found = fs.readdirSync(ASSET_DIR).find((file) => path.parse(file).name === stem);
  return found ? path.join(ASSET_DIR, found) : '';
}

function collectAssetRefs(text = '') {
  const refs = new Set();
  String(text || '').replace(ASSET_RE, (_, file) => {
    refs.add(file);
    return _;
  });
  return refs;
}

function auditDatabase(db) {
  const rows = db.prepare('SELECT * FROM questions').all();
  const report = {
    totals: {
      papers: db.prepare('SELECT COUNT(*) AS c FROM papers').get().c,
      questions: rows.length,
      ziliao: 0,
      ziliaoPracticeable: 0,
      withMathMl: 0,
      badAnswer: 0,
      badOptions: 0,
      missingAssets: 0,
      tinyAssets: 0,
    },
    thresholds: {
      ziliaoPracticeableRatio: 0,
      targetZiliaoPracticeableRatio: 0.95,
      pass: false,
    },
    samples: {
      badAnswer: [],
      badOptions: [],
      missingMaterial: [],
      missingAssets: [],
    },
  };

  const assetRefs = new Set();
  rows.forEach((row) => {
    ['content_html', 'material_html', 'analysis_html', 'options'].forEach((field) => {
      collectAssetRefs(row[field]).forEach((ref) => assetRefs.add(ref));
    });

    if (row.category === 'ziliao') {
      report.totals.ziliao += 1;
      if (isPracticeableQuestion(row)) report.totals.ziliaoPracticeable += 1;
      else if (report.samples.missingMaterial.length < 8) {
        report.samples.missingMaterial.push(row.id);
      }
    }

    if (/<math[\s>]/i.test(row.content_html || '') || /<math[\s>]/i.test(row.analysis_html || '')) {
      report.totals.withMathMl += 1;
    }

    const options = parseOptions(row.options);
    const trueFalse = options.length === 2 && options.every((item) => {
      const text = String(item?.content || '').replace(/<[^>]+>/g, '').trim();
      return /^(正确|错误|对|错|是|否|true|false)$/i.test(text);
    });
    if (!options.length || (options.length < 4 && row.type !== 'multiple' && !trueFalse)) {
      report.totals.badOptions += 1;
      if (report.samples.badOptions.length < 8) report.samples.badOptions.push(row.id);
    }
    if (!isAnswerCompatible(row.answer, options, row.type)) {
      report.totals.badAnswer += 1;
      if (report.samples.badAnswer.length < 8) report.samples.badAnswer.push(row.id);
    }
  });

  let missingAssets = 0;
  let tinyAssets = 0;
  assetRefs.forEach((ref) => {
    const filePath = resolveAsset(ref);
    if (!filePath) {
      missingAssets += 1;
      if (report.samples.missingAssets.length < 8) report.samples.missingAssets.push(ref);
      return;
    }
    const size = fs.statSync(filePath).size;
    if (size < 80) tinyAssets += 1;
  });
  report.totals.missingAssets = missingAssets;
  report.totals.tinyAssets = tinyAssets;
  report.totals.assetRefs = assetRefs.size;

  report.thresholds.ziliaoPracticeableRatio = report.totals.ziliao
    ? report.totals.ziliaoPracticeable / report.totals.ziliao
    : 1;
  report.thresholds.pass = (
    report.totals.missingAssets === 0
    && report.totals.badAnswer === 0
    && report.thresholds.ziliaoPracticeableRatio >= report.thresholds.targetZiliaoPracticeableRatio
  );

  return report;
}

function main() {
  const asJson = process.argv.includes('--json');
  const dbPath = process.argv.find((arg) => arg.endsWith('.db'));
  let db;
  let tmp = '';
  if (dbPath) {
    db = new Database(dbPath, { readonly: true });
  } else {
    const opened = openSeedDb();
    db = opened.db;
    tmp = opened.tmp;
  }

  try {
    const report = auditDatabase(db);
    if (asJson) {
      console.log(JSON.stringify(report, null, 2));
    } else {
      console.log('=== OpenExam question bank audit ===');
      console.log(`papers: ${report.totals.papers}`);
      console.log(`questions: ${report.totals.questions}`);
      console.log(`ziliao: ${report.totals.ziliao} (practiceable ${report.totals.ziliaoPracticeable}, ratio ${(report.thresholds.ziliaoPracticeableRatio * 100).toFixed(1)}%)`);
      console.log(`mathml rows: ${report.totals.withMathMl}`);
      console.log(`bad answers: ${report.totals.badAnswer}`);
      console.log(`bad options: ${report.totals.badOptions}`);
      console.log(`asset refs: ${report.totals.assetRefs}, missing ${report.totals.missingAssets}, tiny ${report.totals.tinyAssets}`);
      console.log(`gate pass (>=95% ziliao practiceable, 0 missing assets, 0 bad answers): ${report.thresholds.pass ? 'YES' : 'NO'}`);
      if (report.samples.missingMaterial.length) {
        console.log('missing material samples:', report.samples.missingMaterial.join(', '));
      }
      if (report.samples.badAnswer.length) {
        console.log('bad answer samples:', report.samples.badAnswer.join(', '));
      }
    }
    process.exitCode = report.thresholds.pass ? 0 : 2;
  } finally {
    db.close();
    if (tmp && fs.existsSync(tmp)) fs.unlinkSync(tmp);
  }
}

if (require.main === module) {
  main();
}

module.exports = { auditDatabase };
