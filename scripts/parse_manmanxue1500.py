#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""解析"漫漫学"高分密训1500题(2027) 两册PDF → 客观题规范JSON。

两册(均为 OCR "识别文本" PDF):
  试题册 ...高分密训1500题-漫漫学.pdf(283p): 题干 + 4 选项。
      题号紧贴题干开头(如 "1马克思…" / "2 1848年…"),选项各占一行 "A. …"。
  精析册 ...高分密训1500题精析-漫漫学.pdf(357p): 逐题 "N【正确选项】X" + 【名师精析】/【错项辨识】。

结构一致: 第X部分(学科) → 第X章 → 第X节 → 一、单项选择题 / 二、多项选择题。
part(第X部分) 边界在两册都干净(各 5 处),作为硬锚。以 (part, type) 阅读顺序把
试题册的 ABCD 四选项块 与 精析册的答案/解析按序对齐;题号(可辨认时)用于纠偏。

输出: manmanxue1500_questions.json + manmanxue1500_audit.csv
"""
import re
import os
import csv
import glob
import json

BOOK_DIR = os.environ.get("KAOYAN_BOOK_DIR", "data/kaoyan/习题/books")


def _find(*must):
    """在 BOOK_DIR 里找同时包含所有 must 子串的 PDF(避开 Windows 中文路径字面量编码问题)。"""
    for p in glob.glob(os.path.join(BOOK_DIR, "*.pdf")):
        base = os.path.basename(p)
        if all(m in base for m in must):
            return p
    raise FileNotFoundError("no pdf matching %r in %s" % (must, BOOK_DIR))


# 试题册: 含 "识别文本" 且 "1500题-" (不含 "精析")
# 精析册: 含 "识别文本" 且 "1500题精析"
SHITI = _find("识别文本", "1500题-")
JIEXI = _find("识别文本", "1500题精析")
OUT_DIR = "data/kaoyan/习题/ocr/漫漫学1500"

_CN = "一二三四五六七八九十"
# 第X部分 → 学科
PART_MAP = {1: "mayuan", 2: "maozhongte", 3: "xinsi", 4: "shigang", 5: "sixiu"}
DISC_LABEL = {"mayuan": "马原", "maozhongte": "毛中特", "xinsi": "习思想",
              "shigang": "史纲", "sixiu": "思修"}

SHITI_MIN_PI = 9   # 试题册正文起始页(0-based;前 6-7 页为目录)
JIEXI_MIN_PI = 5   # 精析册正文起始页(0-based;前 3-4 页为目录)


def norm(s):
    return re.sub(r"\s+", "", s)


def cn2int(g):
    if g in _CN:
        return _CN.index(g) + 1
    return int(g) if g.isdigit() else None


def part_idx(line):
    s = line.strip()
    m = re.search(r"第([" + _CN + r"\d]+)部分", s)
    if m and len(s) < 28:
        return cn2int(m.group(1))
    return None


def detect_type(line):
    """仅识别小节标题式题型切换。"""
    s = re.sub(r"^[^一-鿿]+", "", line.strip())
    m = re.match(r"^([" + _CN + r"]|[（(][" + _CN + r"][）)])\s*[、.，．,·]*", s)
    if m:
        s = s[m.end():]
    s = re.sub(r"^[、.，．,·\s]+", "", s)
    for key, w in [("single", "单项选择题"), ("multiple", "多项选择题")]:
        if s.startswith(w) and len(s) <= 12:
            return key
    return None


def is_chapter(line):
    """第X章 / 导论 / 绪论 章级标题(其后一行为章标题正文,需丢弃)。"""
    s = line.strip()
    if re.match(r"^第[" + _CN + r"\d]{1,2}章", s) and len(s) < 45:
        return True
    if re.match(r"^导\s*[论言]$", s) or re.match(r"^绪\s*论$", s):
        return True
    return False


def is_section(line):
    """第X节 节级标题(其后一行为节标题正文,需丢弃)。"""
    s = line.strip()
    return bool(re.match(r"^第[" + _CN + r"\d]{1,2}节", s) and len(s) < 45)


_ORD = re.compile(r"^[（(]?[" + _CN + r"][）)]?\s*[、.，．,·]?\s*$")   # 孤立的 "一、"/"二、" 行


_OPT = re.compile(r"^\s*([A-D])\s*[.、．，·]\s*")
# 选项字母标记(行内任意位置): 书本常"一行两个选项"(如 "A.普遍性B.客观性…"),
# 不能只认行首。拆分用;字母后接 . 、 ． 视为选项起始。
_OPTM = re.compile(r"([A-D])\s*[.、．，·]")

# 跨题污染: 选项 D 常把下一题号+题干吞进来(如 "…登上历史舞台2 1848年…")。
# 用与 xiao1000 相同的思路把选项截到下一题号处。
_Q1 = re.compile(r"(?<![0-9A-Za-z.，、．])(\d{1,3})\s*[.、．，,•・·](?=\s)")
_Q2 = re.compile(r"(?<![0-9A-Za-z.，、．])(\d{1,3})\s*[.、．，,•・·](?=[一-鿿“”‘’（(【〔《「])")
_Q3 = re.compile(r"(?<![0-9A-Za-z.，、．])(\d{1,3})\s*[.、．，,•・·](?=\d{4})")


def cut_next_question(s):
    cuts = [m.start() for m in _Q1.finditer(s)]
    cuts += [m.start() for m in _Q2.finditer(s)]
    cuts += [m.start() for m in _Q3.finditer(s)]
    return s[:min(cuts)] if cuts else s


# ===================== 页面抽取(词级坐标重建视觉行;去页眉/页脚水印) =====================
def _page_lines(pdf_path):
    """返回 [ [line,...] , ...] 逐页正文行。

    原实现按 block 文本流 + splitlines() 抽取,会把同一视觉行里的共线碎块
    (引号/选项两列排版的右半)按块顺序打乱、换行切断 → 题干污染严重。
    改为: 用 get_text('words') 取词级 bbox,按 y 聚成视觉行、行内按 x 排序,
    重建真实阅读顺序(选项"一行两个"等排版得以保持),再按 y 滤掉页眉/页脚水印。
    """
    import pymupdf
    d = pymupdf.open(pdf_path)
    TOL = 0.010          # 同视觉行的 y 容差(页高比例);~3-4px 的 OCR 引号抖动可并入
    pages = []
    for i in range(d.page_count):
        pg = d[i]
        H = pg.rect.height or 1.0
        ws = []
        for w in pg.get_text("words"):   # (x0,y0,x1,y1,word,block,line,no)
            t = w[4].strip()
            if not t:
                continue
            cy = (w[1] + w[3]) / 2.0
            if cy / H < 0.06 or cy / H > 0.945:   # 滤页眉/页脚/页码水印
                continue
            ws.append((cy, w[0], t))
        ws.sort()
        rows = []
        cur, cb = [], None
        for cy, x0, t in ws:
            if cb is None or abs(cy - cb) <= TOL * H:
                if cb is None:
                    cb = cy
                cur.append((x0, t))
            else:
                rows.append("".join(x for _, x in sorted(cur)))
                cur, cb = [(x0, t)], cy
        if cur:
            rows.append("".join(x for _, x in sorted(cur)))
        pages.append([r.strip() for r in rows if r.strip()])
    d.close()
    return pages


# ===================== 试题册: 题干 + 4 选项 =====================
def scan_shiti(page_lines):
    """按 (part,type) 阅读顺序扫描;以 ABCD 四选项块为锚。
    返回 [{part,type,qno,stem,options:[4]}]  (阅读顺序)。
    结构行(部分/章/节/题型/孤立序号) 记为 bar 哨兵;章/节标题正文行丢弃。"""
    part, ct = None, None
    chap = 0            # 章出现序号(当前 part 内从 0 计;导论=0)
    flat = []           # ("opt",letter,text) / ("txt",text) / ("bar",) 携带 part,ct
    flat_chap = []      # 与 flat 对齐的章序号
    drop_title = 0
    for pno in range(SHITI_MIN_PI, len(page_lines)):
        for ln in page_lines[pno]:
            p = part_idx(ln)
            if p is not None:
                part, ct, chap = p, None, 0
                flat.append((part, ct, "bar", None)); flat_chap.append(chap)
                drop_title = 0
                continue
            if is_chapter(ln):
                chap += 1
                flat.append((part, ct, "bar", None)); flat_chap.append(chap)
                drop_title = 1   # 下一非结构行 = 章标题正文,丢弃
                continue
            if is_section(ln):
                flat.append((part, ct, "bar", None)); flat_chap.append(chap)
                drop_title = 1   # 下一非结构行 = 节标题正文,丢弃
                continue
            t = detect_type(ln)
            if t is not None:
                ct = t
                flat.append((part, ct, "bar", None)); flat_chap.append(chap)
                drop_title = 0
                continue
            if _ORD.match(ln):           # 孤立的 "一、"/"二、"
                flat.append((part, ct, "bar", None)); flat_chap.append(chap)
                continue
            if drop_title:
                drop_title = 0
                continue
            # 内容行: 按选项字母标记切分(可"一行多个选项"),无标记则为纯正文/选项续行。
            marks = [(m.start(), m.group(1), m.end()) for m in _OPTM.finditer(ln)]
            if not marks:
                flat.append((part, ct, "txt", ln)); flat_chap.append(chap)
                continue
            head = ln[:marks[0][0]].strip()
            if head:
                flat.append((part, ct, "txt", head)); flat_chap.append(chap)
            for i, (start, let, end) in enumerate(marks):
                nxt = marks[i + 1][0] if i + 1 < len(marks) else len(ln)
                content = ln[end:nxt].strip()
                flat.append((part, ct, "opt", let, content)); flat_chap.append(chap)

    def is_qstart(x):
        """txt token 以题号开头(下一题起点),用于截断选项 D 的跨题吞并。
        题号 = 行首 1-3 位数字,其后为 行尾/空白/非数字(如 "1马克思"/"2 世情"/"2 1848年"/"6")。
        4 位数(年份如 "1848年")不算。"""
        if flat[x][2] != "txt":
            return False
        return bool(re.match(r"^\s*\d{1,3}(?:\s|$|[^\d])", flat[x][3]))

    optidx = [i for i, f in enumerate(flat) if f[2] == "opt"]
    qs = []
    j = 0
    while j <= len(optidx) - 4:
        a, b, c, d = optidx[j], optidx[j + 1], optidx[j + 2], optidx[j + 3]
        if [flat[x][3] for x in (a, b, c, d)] == ["A", "B", "C", "D"]:
            part_a, ct_a = flat[a][0], flat[a][1]
            # 题干 = A 之前紧邻的 txt(向前回溯到上一个 opt 或 bar)
            s = a - 1
            stem_toks = []
            while s >= 0 and flat[s][2] == "txt":
                stem_toks.append(flat[s][3])
                s -= 1
            stem_toks.reverse()
            stem = "".join(stem_toks)
            # 选项 A/B/C: 到下一字母前的 txt 续行
            opts = []
            quad = (a, b, c, d)
            for oi in range(3):
                x = quad[oi]
                otext = flat[x][4]
                y = x + 1
                while y < quad[oi + 1] and flat[y][2] == "txt":
                    otext += flat[y][3]
                    y += 1
                opts.append(otext)
            # 选项 D: 从 d 到下一题干起点(首个 qstart)或 bar 或下一题 A,截断避免吞下一题
            nextA = optidx[j + 4] if j + 4 < len(optidx) else len(flat)
            otext = flat[d][4]
            y = d + 1
            while y < nextA and flat[y][2] != "bar" and not is_qstart(y):
                if flat[y][2] == "txt":
                    otext += flat[y][3]
                y += 1
            opts.append(otext)
            mnum = re.match(r"^\s*(\d{1,3})", stem)
            qno = int(mnum.group(1)) if mnum else None
            stem2 = re.sub(r"^\s*\d{1,3}\s*", "", stem)
            qs.append({"part": part_a, "chap": flat_chap[a], "type": ct_a, "qno": qno,
                       "stem": stem2, "options": opts})
            j += 4
        else:
            j += 1
    return qs


# ===================== 精析册: 答案 + 解析 =====================
_ANS = re.compile(r"(\d{1,3})?\s*[【\[]正确选[项顶][】\]]\s*[lJ|]?\s*([A-D]{1,4})")


def scan_jiexi(page_lines):
    """返回 [{part,chap,type,qno,answer,analysis_lines}] (阅读顺序)。"""
    part, ct = None, None
    chap = 0
    flat = []          # (part, chap, ct, ln)
    for pno in range(JIEXI_MIN_PI, len(page_lines)):
        for ln in page_lines[pno]:
            p = part_idx(ln)
            if p is not None:
                part, ct, chap = p, None, 0
                continue
            if is_chapter(ln):
                chap += 1
                continue
            t = detect_type(ln)
            if t is not None:
                ct = t
                continue
            flat.append((part, chap, ct, ln))
    ents = []
    for i, (p, ch, c, ln) in enumerate(flat):
        m = _ANS.search(ln)
        if m and p and c:
            ents.append({"part": p, "chap": ch, "type": c,
                         "qno": int(m.group(1)) if m.group(1) else None,
                         "answer": m.group(2), "idx": i})
    for k, e in enumerate(ents):
        en = ents[k + 1]["idx"] if k + 1 < len(ents) else len(flat)
        e["analysis_lines"] = [flat[x][3] for x in range(e["idx"], en)]
    return ents


# ===================== 解析清洗 =====================
_SENT_END = "。！？；…"


def tidy(s):
    def line(x):
        x = re.sub(r"[ 　 \t]+", " ", x)
        x = re.sub(r"(?<=[一-鿿])\s+(?=[一-鿿])", "", x)
        return x.strip()
    return "\n".join(line(l) for l in s.split("\n"))


def clean_analysis(lines):
    """精析册每题原始行 → 清洗后的解析正文(去答案头/版边噪声,重排断句)。"""
    frag = []
    for raw in lines:
        s = re.sub(r"[\x00-\x1f]", "", raw).strip()
        if not s:
            continue
        # 去掉 "N【正确选项】X" 头(容忍 】 前后 OCR 杂字符,如 "S【正确选项】C")
        s = re.sub(r"^[^【\[]{0,4}[【\[]正确选[项顶][】\]]\s*[lJ|]?\s*[A-D]{1,4}\s*", "", s)
        s = re.sub(r"[【\[]\s*名师[精耦糯枂雨]?[析枂]\s*[】\]]", "【名师精析】", s)
        s = re.sub(r"[【\[]\s*错项辨识\s*[】\]]", "【错项辨识】", s)
        if not re.search(r"[一-鿿]", s):
            continue
        frag.append(s)
    parts, cur = [], ""
    for s in frag:
        if not cur:
            cur = s
        elif cur[-1] in _SENT_END or s.startswith("【"):
            parts.append(cur)
            cur = s
        else:
            cur += s
    if cur:
        parts.append(cur)
    out = [p.strip("、，。． ") for p in parts if p.strip("、，。． ")]
    return "\n".join(out)


# ===================== 主流程: 按 (part,type) 序列对齐 =====================
def _nw_align(S, J):
    """Needleman-Wunsch 全局比对两串题号(S=试题册, J=精析册)。
    两册题目同序、题号模式一致(每节从 1 重排),仅个别位置试题册多出杂块。
    返回配对列表 [(i,k)|(i,None)|(None,k)],i=S 下标,k=J 下标。"""
    n, m = len(S), len(J)
    NEG = float("-inf")
    GAP = -2

    def sc(i, k):
        a, b = S[i], J[k]
        if a is None or b is None:
            return -1
        return 3 if a == b else -3

    # dp[i][k] = best score aligning S[:i], J[:k]
    dp = [[0.0] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        dp[i][0] = i * GAP
    for k in range(1, m + 1):
        dp[0][k] = k * GAP
    for i in range(1, n + 1):
        Si = i - 1
        row, prow = dp[i], dp[i - 1]
        for k in range(1, m + 1):
            diag = prow[k - 1] + sc(Si, k - 1)
            up = prow[k] + GAP
            left = row[k - 1] + GAP
            row[k] = diag if (diag >= up and diag >= left) else (up if up >= left else left)
    # traceback
    i, k = n, m
    pairs = []
    while i > 0 and k > 0:
        cur = dp[i][k]
        if cur == dp[i - 1][k - 1] + sc(i - 1, k - 1):
            pairs.append((i - 1, k - 1)); i -= 1; k -= 1
        elif cur == dp[i - 1][k] + GAP:
            pairs.append((i - 1, None)); i -= 1
        else:
            pairs.append((None, k - 1)); k -= 1
    while i > 0:
        pairs.append((i - 1, None)); i -= 1
    while k > 0:
        pairs.append((None, k - 1)); k -= 1
    pairs.reverse()
    return pairs


def align(shiti_qs, jiexi_es):
    """每个 (part,type) 内用题号做全局比对;精析册答案权威,逐条配一题干。"""
    from collections import defaultdict
    SB, JB = defaultdict(list), defaultdict(list)
    for q in shiti_qs:
        SB[(q["part"], q["type"])].append(q)
    for e in jiexi_es:
        JB[(e["part"], e["type"])].append(e)

    merged = []
    stats = {}
    for key in sorted(JB):
        js = JB[key]
        ss = SB.get(key, [])
        pairs = _nw_align([q["qno"] for q in ss], [e["qno"] for e in js])
        # 建立 k(jiexi下标) -> i(shiti下标) 映射
        k2i = {k: i for (i, k) in pairs if k is not None and i is not None}
        paired = 0
        for k, e in enumerate(js):
            i = k2i.get(k)
            q = ss[i] if i is not None else None
            if q is not None:
                paired += 1
            merged.append({
                "part": key[0], "type": key[1],
                "qno": e["qno"] if e["qno"] else (q["qno"] if q else None),
                "stem": q["stem"] if q else "",
                "options": q["options"] if q else [],
                "answer": e["answer"],
                "analysis_lines": e["analysis_lines"],
                "has_stem": q is not None,
            })
        stats[key] = (len(js), len(ss), paired)
    return merged, stats


def main():
    shiti_qs = scan_shiti(_page_lines(SHITI))
    jiexi_es = scan_jiexi(_page_lines(JIEXI))
    print("试题册 题干块:", len(shiti_qs), " 精析册 答案:", len(jiexi_es))

    merged, stats = align(shiti_qs, jiexi_es)
    print("\n(part,type)  jiexi  shiti  paired")
    for key in sorted(stats):
        j, s, p = stats[key]
        print("  %-20s %4d %5d %6d" % (str(key), j, s, p))

    out = []
    kept = 0
    for i, m in enumerate(merged):
        disc = PART_MAP[m["part"]]
        opts = []
        for k in range(min(len(m["options"]), 4)):
            c = tidy(cut_next_question(m["options"][k]))
            opts.append({"key": chr(65 + k), "content": c})
        stem = tidy(m["stem"])
        analysis = tidy(clean_analysis(m["analysis_lines"]))
        ok = bool(stem) and len(opts) == 4 and all(o["content"] for o in opts) and bool(m["answer"])
        if ok:
            kept += 1
        out.append({
            "order": i, "disc": disc, "disc_label": DISC_LABEL[disc],
            "type": m["type"], "qno": m["qno"],
            "content": stem, "options": opts,
            "answer": m["answer"], "analysis": analysis,
            "ok": ok, "has_stem": m["has_stem"],
        })

    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, "manmanxue1500_questions.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    with open(os.path.join(OUT_DIR, "manmanxue1500_audit.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["order", "disc", "type", "qno", "answer", "ok", "has_stem"])
        w.writeheader()
        for q in out:
            w.writerow({k: q[k] for k in ["order", "disc", "type", "qno", "answer", "ok", "has_stem"]})
    print("\ntotal:", len(out), " ok(完整):", kept)
    print("WROTE", OUT_DIR)


if __name__ == "__main__":
    main()
