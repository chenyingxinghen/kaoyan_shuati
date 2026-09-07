#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""考研政治种子库导入。
输入:
  - data2/questions.json   历年真题 559 (source/type/stem/options/answer/kaodian/jiexi/…)
  - data3/parsed/xiao1000_questions.json  肖1000(2026) 客观题(disc/type/content/options/answer/analysis)
输出: data/kaoyan/openexam.kaoyan-politics.seed.db.gz
schema 与考公一致(papers/questions)，subject='kaoyan'，category=政治学科。
"""
import json
import gzip
import os
import re
import sqlite3

DATA = "data/kaoyan"
XIAO = os.path.join(DATA, "习题/parsed/xiao1000_questions.json")
PAST = os.path.join(DATA, "真题/网页抓取/questions.json")
RAW_DB = os.path.join(DATA, "openexam.kaoyan-politics.db")
GZ_DB = os.path.join(DATA, "openexam.kaoyan-politics.seed.db.gz")

# 学科统一标签(含 习思想并入 毛泽东思想与中国特色社会主义，供真题归并)
DISC_LABEL = {
    "mayuan": "马克思主义基本原理",
    "maozhongte": "毛泽东思想和中国特色社会主义理论体系概论",
    "xinsi": "习近平新时代中国特色社会主义思想概论",
    "shigang": "中国近现代史纲要",
    "sixiu": "思想道德与法治",
}
DISC_FULL = {
    "马原": "马克思主义基本原理",
    "毛中特": "毛泽东思想和中国特色社会主义理论体系概论",
    "习思想": "习近平新时代中国特色社会主义思想概论",
    "史纲": "中国近现代史纲要",
    "思修": "思想道德与法治",
}
# 真题 kaodian/题干 → 学科标签
CAT_RULES = [
    (("习近平", "新时代"), "习近平新时代中国特色社会主义思想概论"),
    (("毛泽东", "中国特色社会主义", "新民主主义", "中国化"), "毛泽东思想和中国特色社会主义理论体系概论"),
    (("马克思主义基本原理", "马原", "物质", "辩证", "认识论", "剩余价值", "资本"), "马克思主义基本原理"),
    (("近代史", "纲要", "史纲", "鸦片战争", "革命"), "中国近现代史纲要"),
    (("道德", "思想修养", "思修", "法治", "思法"), "思想道德与法治"),
    (("形势", "时政", "当代世界经济"), "形势与政策以及当代世界经济与政治"),
]


def map_kaodian(cat, stem=""):
    if cat:
        t = cat
    else:
        t = stem
    for keys, label in CAT_RULES:
        if any(k in t for k in keys):
            return label
    return "综合"


def open_db(path):
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    db.executescript("""
    CREATE TABLE papers (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      year INTEGER NOT NULL,
      type TEXT NOT NULL,
      subject TEXT NOT NULL,
      province TEXT,
      question_count INTEGER DEFAULT 0,
      duration INTEGER DEFAULT 120,
      difficulty INTEGER DEFAULT 3,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE questions (
      id TEXT PRIMARY KEY,
      paper_id TEXT NOT NULL,
      order_num INTEGER NOT NULL,
      type TEXT DEFAULT 'single',
      category TEXT,
      sub_category TEXT,
      content TEXT NOT NULL,
      content_html TEXT,
      material_html TEXT,
      material_group_id TEXT,
      options TEXT NOT NULL,
      answer TEXT NOT NULL,
      analysis TEXT,
      analysis_html TEXT,
      difficulty INTEGER DEFAULT 2,
      tags TEXT,
      quality_flag TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (paper_id) REFERENCES papers(id)
    );
    CREATE INDEX idx_questions_paper_id ON questions(paper_id);
    CREATE UNIQUE INDEX idx_questions_paper_order_unique ON questions(paper_id, order_num);
    """)
    return db


def clean_analysis(s):
    if not s:
        return ""
    # 简单清洗：移除常见站点噪声标记/长尾部关键词
    s = s.strip()
    return s


def opt_list(opt, stem=""):
    """真题 options 为 dict{A:...} 或 list；输出 [{key,content}]。"""
    if isinstance(opt, dict):
        out = []
        for k in "ABCD":
            if k in opt and opt[k] not in (None, ""):
                content = str(opt[k]).strip()
                # 去掉内容自带前缀如 “A.”
                import re
                content = re.sub(r"^[A-Z]\s*[.、．，]", "", content).strip()
                out.append({"key": k, "content": content})
        return out
    if isinstance(opt, list):
        out = []
        for k, o in zip("ABCD", opt):
            content = str(o).strip()
            out.append({"key": k, "content": content})
        return out
    return []


def normalize_type(t):
    t = str(t or "").strip()
    if "多" in t:
        return "multiple"
    if "判" in t:
        return "judge"
    return "single"


def sanitize_options(opts):
    """清理选项内容：截断到下一题锚点(换行+数字+点)等跨题污染，去空白。"""
    out = []
    for o in opts:
        c = o["content"]
        # 遇到 “换行 数字. ” 之类(误并入下一题)即截断
        cut = re.split(r"[\n]\s*\d{1,3}\s*[.、．，]", c)
        c = cut[0]
        # 去掉尾随的下一题片段(如直接跟在后的 "7.“四个" 无换行) —— 保守: 仅清理明显换行污染
        c = re.sub(r"\s+", " ", c).strip()
        out.append({"key": o["key"], "content": c})
    return out


def valid_q(content, opts, answer):
    if not content or not content.strip():
        return False
    if len(opts) < 2 or not all(o["content"].strip() for o in opts):
        return False
    if not answer:
        return False
    return True


def main():
    xiao = json.load(open(XIAO, encoding="utf-8"))
    past = json.load(open(PAST, encoding="utf-8"))
    print("xiao json:", len(xiao), " past:", len(past))

    papers = []          # {id,title,year,difficulty,category_prefix?, questions:[{..}]}
    # ---------- 肖1000: 按学科一篇 ----------
    by_disc = {}
    for x in xiao:
        by_disc.setdefault(x["disc"], []).append(x)
    for disc, qs in by_disc.items():
        good = []
        for x in qs:
            opts = sanitize_options(x.get("options") or [])
            if valid_q(x.get("content"), opts, x.get("answer")):
                x = dict(x, options=opts)
                good.append(x)
        if not good:
            continue
        lab = DISC_LABEL.get(disc, disc)
        papers.append({
            "id": "xiao1000_2026_%s" % disc,
            "title": "2026肖秀荣1000题·%s" % lab,
            "year": 2026, "difficulty": 2, "cat": lab,
            "questions": [{
                "type": "multiple" if q["type"] == "multiple" else "single",
                "category": lab,
                "content": q["content"],
                "options": q["options"],
                "answer": q["answer"],
                "analysis": q.get("analysis") or "",
                "tags": "xiao1000",
            } for q in good],
            "kept": len(good), "total": len(qs),
        })

    # ---------- 真题: 按年份一篇 ----------
    by_year = {}
    for q in past:
        by_year.setdefault(str(q.get("year")), []).append(q)
    for yr in sorted(by_year):
        qs = by_year[yr]
        good = []
        for q in qs:
            cat = map_kaodian(q.get("kaodian"), q.get("stem") or "")
            opts = sanitize_options(opt_list(q.get("options"), q.get("stem") or ""))
            ans = str(q.get("answer") or "").strip().upper()
            content = (q.get("stem") or "").strip()
            t = normalize_type(q.get("type"))
            # 防御: type 与答案字母数不符 → 大概率源数据答错/漏字，剔除
            if (t == "multiple" and len(ans) < 2) or (t == "single" and len(ans) != 1):
                continue
            if valid_q(content, opts, ans):
                good.append({"type": t,
                             "category": cat,
                             "content": content, "options": opts,
                             "answer": ans,
                             "analysis": clean_analysis(q.get("jiexi") or ""),
                             "tags": "past:%s" % q.get("source")})
        if not good:
            continue
        papers.append({
            "id": "politics_past_%s" % yr, "title": "%s考研政治真题" % yr,
            "year": int(yr), "difficulty": 3, "cat": None,
            "questions": good, "kept": len(good), "total": len(qs),
        })

    # ---------- 建库 ----------
    db = open_db(RAW_DB)
    ins_p = db.executemany  # placeholder
    cp = db.cursor()
    cs = db.cursor()
    tot_q = 0
    for p in papers:
        cp.execute("""INSERT OR REPLACE INTO papers
            (id,title,year,type,subject,province,question_count,duration,difficulty)
            VALUES (?,?,?,?,?,?,?,?,?)""",
            (p["id"], p["title"], p["year"], "national", "kaoyan", None,
             len(p["questions"]), 120, p.get("difficulty", 3)))
        for i, q in enumerate(p["questions"], 1):
            cs.execute("""INSERT OR REPLACE INTO questions
            (id,paper_id,order_num,type,category,sub_category,content,content_html,
             material_html,material_group_id,options,answer,analysis,analysis_html,
             difficulty,tags,quality_flag)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            ("%s_q%d" % (p["id"], i), p["id"], i, q["type"], q["category"],
             None, q["content"], None, None, None,
             json.dumps(q["options"], ensure_ascii=False), q["answer"],
             q["analysis"], None, 2, q["tags"], None))
            tot_q += 1
    db.commit()
    db.execute("PRAGMA journal_mode=DELETE")
    db.execute("VACUUM")
    db.close()

    # gzip
    with open(RAW_DB, "rb") as f, gzip.open(GZ_DB, "wb") as g:
        g.write(f.read())
    os.remove(RAW_DB)
    for ext in ("-wal", "-shm"):
        if os.path.exists(RAW_DB + ext):
            os.remove(RAW_DB + ext)

    print("\n=== 导入汇总 ===")
    print("papers:", len(papers), " questions:", tot_q)
    for p in papers:
        print("  paper", p["id"], " kept=%d/%d" % (p["kept"], p["total"]), "title=", p["title"])
    print("seed:", GZ_DB, os.path.getsize(GZ_DB), "bytes")


if __name__ == "__main__":
    main()
