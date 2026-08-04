const Database = require('better-sqlite3');
const path = require('path');
const os = require('os');
const { hasUsableMaterial, isPracticeableQuestion } = require('../src/shared/questionQuality');

const dbPath = path.join(os.homedir(), 'Library/Application Support/openexam/openexam.db');
const db = new Database(dbPath, { readonly: true });

const report = {
  dbPath,
  papers: db.prepare('SELECT COUNT(*) AS c FROM papers').get().c,
  questions: db.prepare('SELECT COUNT(*) AS c FROM questions').get().c,
  materialColumn: db.pragma('table_info(questions)').some((c) => c.name === 'material_html'),
  emptyOptions: db.prepare("SELECT COUNT(*) AS c FROM questions WHERE options IS NULL OR options = '[]'").get().c,
  emptyAnswers: db.prepare("SELECT COUNT(*) AS c FROM questions WHERE answer IS NULL OR TRIM(answer) = ''").get().c,
  cats: db.prepare('SELECT category, COUNT(*) AS c FROM questions GROUP BY category ORDER BY c DESC').all(),
};

const ziliao = db.prepare("SELECT * FROM questions WHERE category = 'ziliao'").all();
report.ziliao = {
  total: ziliao.length,
  practiceable: ziliao.filter(isPracticeableQuestion).length,
  withMaterial: ziliao.filter(hasUsableMaterial).length,
};

report.sample = db.prepare(`
  SELECT id, length(material_html) AS ml, substr(content, 1, 48) AS content
  FROM questions
  WHERE category = 'ziliao' AND material_html LIKE '%<img%'
  LIMIT 1
`).get();

report.capabilities = {
  practiceRecords: !!db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='practice_records'").get(),
  wrongBook: !!db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='wrong_questions'").get(),
  aiSessions: !!db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='ai_chat_sessions'").get(),
  achievementsSeed: require('fs').existsSync(path.join(__dirname, 'src/shared/achievementCatalog.json')),
  questionAssets: require('fs').readdirSync(path.join(__dirname, 'data/question-assets')).length,
  viteDev: false,
};

db.close();
console.log(JSON.stringify(report, null, 2));
