#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""审计政治种子库：answer与options兼容、type与答案字母数一致、subject/category/year分布、重复等。"""
import gzip, os, sys, sqlite3, json, tempfile
from collections import Counter

GZ = "data/kaoyan/openexam.kaoyan-politics.seed.db.gz"

def load_db():
    tmp = tempfile.mktemp(suffix=".db")
    with gzip.open(GZ, "rb") as f:
        open(tmp, "wb").write(f.read())
    return sqlite3.connect(tmp), tmp

def main():
    con, tmp = load_db()
    try:
        papers = con.execute("SELECT id,title,year,type,subject,question_count,difficulty FROM papers").fetchall()
        print("papers:", len(papers))
        subs = Counter(p[4] for p in papers)
        print("subject分布:", dict(subs))
        types = Counter(p[3] for p in papers)
        print("type分布:", dict(types))
        tot = 0
        qrows = con.execute("""SELECT q.id,q.type,q.category,q.options,q.answer,q.content,q.analysis
                               FROM questions q JOIN papers p ON p.id=q.paper_id""").fetchall()
        bad_compat = []
        bad_type = []
        no_analysis = 0
        cat = Counter(); qtype = Counter(); short=0
        for qid, qtype_, cat_, opts, ans, content, ana in qrows:
            tot += 1
            qtype[qtype_]+=1; cat[cat_]+=1
            if not (content or "").strip():
                short += 1
            try:
                keys = [o["key"] for o in json.loads(opts)]
            except Exception:
                keys=[]
            ans=(ans or "").strip().upper()
            if ans and not all(a in keys for a in ans):
                bad_compat.append((qid, ans, keys))
            n=len(ans)
            if qtype_=="single" and n!=1: bad_type.append((qid,"single",ans))
            elif qtype_=="multiple" and n<2: bad_type.append((qid,"multiple",ans))
            if not (ana or "").strip(): no_analysis+=1
        print("questions:", tot)
        print("题型分布:", dict(qtype))
        print("category(学科)分布:")
        for c,n in cat.most_common():
            print("   ", c, n)
        print("content为空:", short)
        print("answer越界(不在选项键):", len(bad_compat), bad_compat[:8])
        print("type与答案字母数不一致:", len(bad_type), bad_type[:8])
        print("analysis为空:", no_analysis)
        # years of past papers
        yr=Counter(p[2] for p in papers if p[0].startswith("politics_past"))
        print("真题年份 papers:", dict(sorted(yr.items())))
    finally:
        con.close(); os.remove(tmp)

if __name__ == "__main__":
    main()
