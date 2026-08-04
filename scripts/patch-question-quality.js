#!/usr/bin/env node
/**
 * Patch known seed/user DB quality issues and rewrite openexam.seed.db.gz.
 */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Database = require('better-sqlite3');
const { patchQuestionDatabase } = require('../src/shared/questionQualityRepair');

const ROOT = path.join(__dirname, '..');
const SEED_GZ = path.join(ROOT, 'data/openexam.seed.db.gz');
const SEED_DB = path.join(ROOT, 'data/openexam.seed.db');

function patchSeedGzip() {
  if (!fs.existsSync(SEED_GZ)) {
    throw new Error(`缺少种子库: ${SEED_GZ}`);
  }
  const raw = zlib.gunzipSync(fs.readFileSync(SEED_GZ));
  fs.writeFileSync(SEED_DB, raw);
  const db = new Database(SEED_DB);
  let stats;
  try {
    stats = patchQuestionDatabase(db);
    db.pragma('journal_mode = DELETE');
    db.exec('VACUUM');
  } finally {
    db.close();
  }
  const compressed = zlib.gzipSync(fs.readFileSync(SEED_DB), { level: 9 });
  fs.writeFileSync(SEED_GZ, compressed);
  return stats;
}

function main() {
  const target = process.argv[2];
  if (target && target !== '--seed') {
    const db = new Database(target);
    try {
      const stats = patchQuestionDatabase(db);
      console.log('Patched', target, stats);
    } finally {
      db.close();
    }
    return;
  }

  const stats = patchSeedGzip();
  console.log('Patched seed gzip', SEED_GZ, stats);
}

if (require.main === module) {
  main();
}

module.exports = { patchSeedGzip, patchQuestionDatabase };
