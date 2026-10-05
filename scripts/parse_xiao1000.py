#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""解析肖秀荣1000题(2026) 两册PDF → 客观题规范JSON。

解析册(default文本,权威): 逐题 “N答案X” + 简析 → answer/analysis 与顺序/题型。
试题册: layout文本可靠给出 学科/题型/题号(段), 但个别选项被layout截断 → 用同页 default 文本修补。
输出: xiao1000_questions.json + xiao1000_audit.csv
"""
import re
import os
import csv
import json
import sys

BOOK_DIR = "data/kaoyan/习题/books"
SHITI = BOOK_DIR + "/26肖1000-试题册.pdf"
JIEXI = BOOK_DIR + "/26肖秀荣《1000题》解析册.pdf"
OUT_DIR = "data/kaoyan/习题/parsed"
_CN = "一二三四五六七八九十"
PART_MAP = {1: "mayuan", 2: "maozhongte", 3: "xinsi", 4: "shigang", 5: "sixiu"}
DISC_LABEL = {"mayuan": "马原", "maozhongte": "毛中特", "xinsi": "习思想",
              "shigang": "史纲", "sixiu": "思修", None: None}
DISC_PHRASES = [
    ("xinsi", "习近平新时代中国特色社会主义思想概论"),
    ("maozhongte", "毛泽东思想和中国特色社会主义理论体系概论"),
    ("mayuan", "马克思主义基本原理"),
    ("shigang", "中国近现代史纲要"),
    ("sixiu", "思想道德与法治"),
    ("sixiu", "思想道德修养与法律基础"),
]


def norm(s):
    return re.sub(r"\s+", "", s)


# 解析册题号头标分隔符: "." 之外还有 中文点 "、"/"．"、全/半角逗号 ",，"、日文中点 "・"/"·"。
# OCR 损坏头标则另行修复(_fix_ocr_hdr)：如 "4s. 答案ACD"(s=OCR误识的5) → "45. 答案ACD"、"1亿答案ABD"(亿=OCR误识的17) → "117. 答案ABD"。
_HEAD_SEP = r"[.、．,，·・]?"


def _fix_ocr_hdr(s):
    """修复解析册题号头标的 OCR 损坏(仅行首、仅后随"答案"才替换)。返回修正后的字符串。"""
    s = re.sub(r"^(\d{1,2})s\.(?=\s*答案)", lambda m: str(int(m.group(1)) * 10 + 5) + ".", s)
    s = re.sub(r"^1亿(?=\s*答案)", "117", s)
    return s


# 题号分隔符：除 "." 外还有 中文点 "、"/"．"、全/半角逗号 "，,"、中点 "•・·"，以及 OCR 把
# "." 认成短横的情形（xinsi多1 的下一题头 "2- 习近平…" 因此未被切成新题，整道下一题被并进
# 上一题块 → 选项 D 吞掉下一题的题干+选项）。全库题号分隔符以此为准，勿再各处各写一份。
_SEP = r"[.、．，,•・·\-－]"

# 跨题污染边界：版式/回退文本里上一题的某个选项可能一直吞到页面末尾，把下一题的
# “题号+题干”并了进来（多为粘连在选项后的裸数字题号，如 “…创造历史10. 唯物主义…”）。
# 选项正文不应含 “裸数字＋分隔符＋汉字/引文/年份”，据此把选项截到下一题开始处。
# 三个判据对应不同粘连形态：分隔符后跟空白(…) 或 汉字/引文，或跟 ≥4 位数字(题干以年份开头)。
_Q1 = re.compile(r"(?<![0-9A-Za-z.，、．])(\d{1,3})\s*" + _SEP + r"(?=\s)")
_Q2 = re.compile(r"(?<![0-9A-Za-z.，、．])(\d{1,3})\s*" + _SEP + r"(?=[一-鿿“”‘’（(【〔《「])")
_Q3 = re.compile(r"(?<![0-9A-Za-z.，、．])(\d{1,3})\s*" + _SEP + r"(?=\d{4})")
# OCR 把数字误识成字母的情形，如 “4s. 我国…”(=44.)：数字+零星字母+分隔符
_Q4 = re.compile(r"(?<![0-9A-Za-z.，、．])(\d{1,3})[A-Za-z]{1,2}" + _SEP + r"(?=\s|[一-鿿“”])")


def cut_next_question(s):
    cuts = [m.start() for m in _Q1.finditer(s)]
    cuts += [m.start() for m in _Q2.finditer(s)]
    cuts += [m.start() for m in _Q3.finditer(s)]
    cuts += [m.start() for m in _Q4.finditer(s)]
    return s[:min(cuts)] if cuts else s


def part_index(line):
    m = re.search(r"第([" + _CN + r"\d]+)部分", line)
    if not m:
        return None
    g = m.group(1)
    return (_CN + "0123456789").index(g) + 1 if g in _CN else (int(g) if g.isdigit() else None)


def detect_discipline(line):
    n = norm(line)
    for slug, phrase in DISC_PHRASES:
        p = norm(phrase)
        if p in n and len(n) <= len(p) + 14:
            return slug
    return None


def detect_type(line):
    # 仅识别“小节标题”式题型切换，避免题干/简析正文里出现“多项选择题/分析题”等词误触发。
    s = re.sub(r"^[^一-鿿]+", "", line.strip())   # 去掉前导非汉字(标点/乱码字母)
    m = re.match(r"^([一二三四五六七八九十]|[（(][一二三四五六七八九十][）)])\s*[、.，．,·]*", s)
    if m:
        s = s[m.end():]
    s = re.sub(r"^[、.，．,·\s]+", "", s)
    for key, w in [("single", "单项选择题"), ("multiple", "多项选择题"),
                   ("analysis", "材料分析题"), ("analysis", "分析题")]:
        if s.startswith(w) and len(s) <= 16:
            return key
    return None


def is_page_no(s):
    return re.fullmatch(r"\s*[-\d\s·.]+\s*", s) is not None and len(s.strip()) < 9


# 章节版式标题行(非题目内容): "第五章 全面深化改革开放"、"第二章 新民主主义革命理论"、
# 光杆 "第二章"，以及 OCR 把 "章" 误识成 "童" 的 "第一童 世界的物质性及发展规律"。
# 版式上它排在页眉/页脚位置，旧逻辑按普通行并进当前题块 → 粘到选项 D 尾部(整库 76 题)。
_CHAPTER_LINE = re.compile(r"^\s*第\s*[一二三四五六七八九十百0-9]{1,3}\s*[章节课童]")
# 章节标题偶有换行，续行以 "、" 等标点开头(如 "第十三章" + "、  维护和塑造国家安全")。
_CH_TITLE_CONT = re.compile(r"^\s*[、，,·•・］】\]]")


# ===================== 试题册 layout 分段 =====================
def scan_stems_layout(reader):
    """按 layout 文本把客观题切块。q={disc,type,num,lines,pages:[pno]}。分析题排除。"""
    qs, disc, ct, oq = [], None, None, None
    pages = []
    ch_cont = False
    for pno in range(len(reader.pages)):
        txt = reader.pages[pno].extract_text(extraction_mode="layout") or ""
        for line in txt.splitlines():
            prev_ch, ch_cont = ch_cont, False
            s = line.strip()
            if not s or is_page_no(s):
                continue
            d = detect_discipline(line)
            if d is not None:
                disc, ct, oq, pages = d, None, None, []
                continue
            t = detect_type(line)
            if t is not None:
                ct, oq, pages = t, None, []
                continue
            if ct not in ("single", "multiple"):
                continue
            # 章节版式标题行与其换行续行: 属版面标题、不是题目内容, 跳过(见 _CHAPTER_LINE)。
            if _CHAPTER_LINE.match(line):
                ch_cont = True
                continue
            if prev_ch and _CH_TITLE_CONT.match(line):
                continue
            # 题号分隔符：除 . 和、／． 外，还可能用中文逗号 “7，习近平总书记指出…”、
            # 中点 “63•调查研究…”/“56・1956年底…”(题号后的点被 OCR 认成 •/・)。若漏认会被
            # 并进上一题的块里、污染其最后一个选项(D)，故一并作为新题起点。
            # 但上一题块里一个选项字母都没有 ⇒ 该块是被截半的题干，此行行首的 “数字.”
            # 是材料里的小数(如 “…升至2023年的\n66.1%…” / “13.26亿人…”)，不是新题号：
            # 并回上一块，否则整题被误切成两半(题干缺尾 + 尾巴冒充新题、选项/解析错配)。
            m = re.match(r"^\s*(\d{1,3})\s*" + _SEP + r"(?=\S)", line)
            if m and oq is not None and not re.search(_OPT_LETTER, "\n".join(oq["lines"])):
                oq["lines"].append(line)
                if pages[-1] != pno:
                    pages.append(pno)
            elif m:
                if oq is not None:
                    oq["pages"] = pages
                    qs.append(oq)
                oq = {"disc": disc, "type": ct, "num": int(m.group(1)), "lines": [line]}
                pages = [pno]
            elif oq is not None:
                oq["lines"].append(line)
                if pages[-1] != pno:
                    pages.append(pno)
    if oq is not None:
        oq["pages"] = pages
        qs.append(oq)
    return qs


# 选项字母: 允许后随 分隔符(. 、 ， 、, 等)、中文引号(“”‘’，选项正文以引号开头如
# A.“不积跬步…”)、或 空格+汉字/内容。OCR 常丢分隔符, 如 "C  持续健康发展的内在要求"(C 后仅空格)。
# 不强制分隔符, 否则一行两选项/丢点会 anchor_fail 而错误回退到 default(可能抓下一题的选项)。
_OPT_LETTER = r"(?<![A-Za-z0-9])([A-D])\s*(?=[.、．，,·－\-“”‘’（(【〔《「]|[一-鿿0-9])"


def split_stem_options(lines):
    """切题干+4选项. 返回 (ok, stem, opts, why)。"""
    txt = re.sub(r"^\s*\d{1,3}\s*" + _SEP + r"\s*", "", "\n".join(lines))
    txt = txt.replace("\n", "")
    pos = list(re.finditer(_OPT_LETTER, txt))
    letters = [m.group(1) for m in pos]
    for i in range(len(pos) - 3):
        if letters[i:i + 4] == ["A", "B", "C", "D"]:
            b = pos[i:i + 4]
            stem = txt[:b[0].start()].strip()
            opts = []
            for k in range(4):
                s0, s1 = b[k].end(), (b[k + 1].start() if k < 3 else len(txt))
                opts.append(re.sub(_TRIM, "", txt[s0:s1]))
            return True, stem, opts, "ok"
    return False, "", [], "anchor_fail:" + ("".join(letters) if letters else "")


_LETTER_ROW = re.compile(r"^\s*A\s*[.、．，•・·]?\s*B\s*[.、．，•・·]?\s*C\s*[.、．，•・·]?\s*D\s*[.、．，•・·]?\s*$")


def split_grid2(lines):
    """2×2 宫格选项 + 尾置 'A. B. C. D.' 字母行的排版(如 全面深化改革/三大外交方针/遵义会议特点)。
    字母行前 2 个内容行各含 2 格(以 ≥2 空格的列距分隔)，按 行序 A B / C D 映射成 4 个选项；
    题干=其前所有行。反馈暴露: 这类题字母行在尾部、选项内容在字母前，旧逻辑 anchor 到尾部字母
    使整段选项粘进题干 + options 为空 → 被 valid_q 过滤、整题缺失。"""
    ls = [l for l in lines if l.strip()]
    if len(ls) < 4 or not _LETTER_ROW.match(ls[-1].strip()):
        return False, "", [], "no_grid2"
    row1 = re.split(r"\s{2,}", ls[-3].strip())   # 第 1 行宫格 → A B
    row2 = re.split(r"\s{2,}", ls[-2].strip())   # 第 2 行宫格 → C D
    if len(row1) != 2 or len(row2) != 2:
        return False, "", [], "grid2_cells"
    cells = row1 + row2
    # 格内不得带选项字母(避免误伤常规"每行一选项+尾置字母行"排版)
    if any(re.match(r"^[A-D]\s*[.、．，•・·]", c) for c in cells):
        return False, "", [], "grid2_letter"
    stem = re.sub(r"^\s*\d{1,3}\s*" + _SEP + r"\s*", "", "\n".join(ls[:-3]))
    return True, stem, [c.strip() for c in cells], "grid2"


_TRIM = r"^[\s,，.、\-－·]+|[\s,，.、\-－]+$"


def split_letterrow(lines):
    """处理“选项正文每行无内联字母 + 尾部 A. B. C. D.”的排版：尾部字母行前的4行=选项。"""
    for idx, l in enumerate(lines):
        if _LETTER_ROW.match(l.strip()):
            pre = [x for x in lines[:idx] if x.strip()]
            if len(pre) >= 4:
                opts = [pre[-4], pre[-3], pre[-2], pre[-1]]
                for k in range(4):
                    opts[k] = re.sub(r"^\s*[A-D]\s*[.、．，•・·]", "", opts[k]).strip()
                stem_lines = pre[:-4]
                stem = re.sub(r"^\s*\d{1,3}\s*" + _SEP + r"\s*", "", "\n".join(stem_lines)).strip()
                return True, stem, opts, "letterrow"
    return False, "", [], "no_letterrow"


def repair_from_default(default_pages, q):
    """从 q 所在页面(default 全文，选项完整)重取题干+选项。返回 (stem, opts) 或 None。"""
    txts = [default_pages[pno] for pno in q["pages"] if pno < len(default_pages)]
    if not txts:
        return None
    txt = "\n".join(txts)
    lstem = re.sub(r"^\s*\d{1,3}\s*" + _SEP + r"\s*", "", "\n".join(q["lines"]))
    key = None
    frag = re.findall(r"[一-鿿]{6,}", lstem)
    for cand in frag:
        key = cand
        break
    if not key:
        return None
    i = txt.find(key)
    if i < 0:
        return None
    tail = txt[i:]
    pos = list(re.finditer(_OPT_LETTER, tail))
    letters = [m.group(1) for m in pos]
    for k in range(len(pos) - 3):
        if letters[k:k + 4] == ["A", "B", "C", "D"]:
            b = pos[k:k + 4]
            stem = tail[:b[0].start()].strip()
            opts = []
            for j in range(4):
                s0, s1 = b[j].end(), (b[j + 1].start() if j < 3 else len(tail))
                opts.append(re.sub(_TRIM, "", tail[s0:s1]))
            return stem, opts
    return None


def _hdr_parts(s):
    """一行并排 2 个题号头标时拆开(解析册 2 栏排版, 如 '142. 答案ABC 146. 答案ABCD')。
    以 "答案+字母 之后 数字+答案" 为界断开；非头标段(纯正文)保持原样。"""
    parts, pos = [], 0
    for m in re.finditer(r"(答案[A-D]{1,4})(\s+)(?=\d{1,3}\s*" + _HEAD_SEP + r"\s*答案)", s):
        parts.append(s[pos:m.end(1)])
        pos = m.end(2)
    parts.append(s[pos:])
    return parts


# ===================== 解析册 =====================
def scan_answers(reader):
    """逐题 answer/analysis；顺序=全局客观题顺序。分析题排除。"""
    entries, part, ct, oe = [], None, None, None
    for pno in range(len(reader.pages)):
        txt = reader.pages[pno].extract_text() or ""
        for line in txt.splitlines():
            s = line.strip()
            if not s:
                continue
            pi = part_index(line)
            if pi is not None:
                part = pi
            t = detect_type(line)
            if t is not None:
                if oe is not None:          # 类型切换时先收尾未完成 entry，避免丢题
                    entries.append(oe)
                ct, oe = t, None
                continue
            if ct not in ("single", "multiple"):
                continue
            m = re.match(r"^\s*(\d{1,3})\s*" + _HEAD_SEP + r"\s*答案\s*[:：]?\s*([A-D]{1,4})\b",
                         _fix_ocr_hdr(s))
            if m:
                # 一行并排 2 个头标(2 栏排版)：第一个头标保持打开、承接后续正文行(正文末判定词
                # 与第一个答案一致，可核验)；其余头标作为仅含题头的独立 entry(其正文在别栏或缺失)。
                segs = _hdr_parts(s)
                heads = []
                for seg in segs:
                    mm = re.match(r"^\s*(\d{1,3})\s*" + _HEAD_SEP + r"\s*答案\s*[:：]?\s*([A-D]{1,4})\b",
                                  _fix_ocr_hdr(seg))
                    if mm:
                        heads.append((_fix_ocr_hdr(seg), int(mm.group(1)), mm.group(2)))
                if len(heads) >= 2 and len(heads) == len(segs):
                    if oe is not None:
                        entries.append(oe)
                    for j, (hseg, num, ans) in enumerate(heads):
                        e = {"part": part, "type": ct, "num": num, "answer": ans, "lines": [hseg]}
                        if j == 0:
                            oe = e
                        else:
                            entries.append(e)
                    continue
                if oe is not None:
                    entries.append(oe)
                s = _fix_ocr_hdr(s)
                oe = {"part": part, "type": ct, "num": int(m.group(1)),
                      "answer": m.group(2), "lines": [s]}
            elif oe is not None:
                oe["lines"].append(s)
    if oe is not None:
        entries.append(oe)
    return entries


_SENT_END = "。！？；…"


def tidy(s):
    """按行清理：去掉版式造成的多余空白(双栏间距/行尾)，消去汉字之间的空格，保留换行。"""
    def line(x):
        x = re.sub(r"[ 　 \t]+", " ", x)          # 多空格 -> 单
        x = re.sub(r"(?<=[一-鿿])\s+(?=[一-鿿])", "", x)  # 汉字间空格删掉
        return x.strip()
    return "\n".join(line(l) for l in s.split("\n"))
# 解析册里随题出现的版边标注噪声：出处/考点 行，以及被损坏的段落引导词前缀
_NOISE_TOK = ("置机", "置板", "题近", "循近", "宣虹", "直提", "隔并", "精丽浮西",
              "精球西", "精近国", "精讲预", "精面", "精丽", "精球", "精近", "置板", "II")


def clean_analysis(lines):
    """lines: 该题 raw 文本行(含 "N答案X" 头、出处/考点噪声、正文)。清洗+重排成段。"""
    frag = []
    for raw in lines:
        s = re.sub(r"[\x00-\x1f]", "", raw)          # 控制字符(\x00 等)
        s = _fix_ocr_hdr(s.strip())
        if not s:
            continue
        s = re.sub(r"^\s*\d{1,3}\s*" + _HEAD_SEP + r"\s*答案\s*[:：]?\s*[A-D]{1,4}\s*", "", s)
        # 解析册在题块之间/每章开头印章节分隔行("第X章"，OCR 有时带噪声如 "第四章i"/"第八章第七章")，
        # 会被并入上一题 entry 尾部。整行丢弃(已验证解析册无任何解析正文以"第X章"开头)。
        if re.match(r"^第[一二三四五六七八九十]{1,2}章", s):
            continue
        # 出处/考点 噪声行 整行丢弃
        if re.search(r"考点\s*[0-9０-９]", s) or s.startswith("出处"):
            continue
        s = re.sub(r"考点\s*\d{1,3}(?:\s*[-—–（(]\s*\d*[）)]?)?", "", s)
        s = re.sub(r"[❶❷❸❹❺❻❼❽❾❿]", "", s)
        s = re.sub(r"^\s*[、。，．·\-—:：]\s*", "", s)
        if not re.search(r"[一-鿿]", s):
            continue
        frag.append(s)
    # 把被印刷换行切断的句子重排回去；遇到句末标点才断段
    parts, cur = [], ""
    for s in frag:
        if not cur:
            cur = s
        elif cur[-1] in _SENT_END:
            parts.append(cur)
            cur = s
        else:
            cur += s
    if cur:
        parts.append(cur)
    # 清理段首损坏前缀
    out = []
    for p in parts:
        for tok in _NOISE_TOK:
            if p.startswith(tok):
                p = p[len(tok):]
                break
        # 形如 "地A正确。" 的单个损坏前缀(1-2字)+[A-D]正确
        m = re.match(r"^([一-鿿]{1,2})[A-D]正确", p)
        if m:
            p = p[m.end(1):]
        # 剥段首/段尾的残破标点，但保留句号 —— 段尾没有句号会让完整解析看起来“被截断”
        # (反馈: maozhongte 45 “第三步…实行定股定息” 后其实原文有句号)。
        p = p.strip("、，． ")
        if p:
            out.append(p)
    return "\n".join(out)


DISC_PART = {v: k for k, v in PART_MAP.items()}


def build_answer_index(reader):
    """一次性把解析册每页(part, 行)索引出来，供按 qno 二次回收。返回 [(part, text)].  + 页文本缓存列表"""
    pages = []
    cur = None
    for pno in range(len(reader.pages)):
        txt = reader.pages[pno].extract_text() or ""
        for line in txt.splitlines():
            pi = part_index(line)
            if pi is not None:
                cur = pi
        pages.append((cur, txt))
    return pages


def recover_answer(pages, s, exp_len):
    """pages: [(part, fullpage_text)]. 按学科(part)页内 qno+单/多字母数回收。返回 {answer,text} 或 None。"""
    target_part = DISC_PART.get(s["disc"])
    if target_part is None:
        return None
    cands = []
    for part, txt in pages:
        if part != target_part:
            continue
        lines = txt.splitlines()
        for idx, line in enumerate(lines):
            ss = _fix_ocr_hdr(line.strip())
            m = re.match(r"^\s*(\d{1,3})\s*" + _HEAD_SEP + r"\s*答案\s*[:：]?\s*([A-D]{1,4})\b", ss)
            if not m or int(m.group(1)) != s["num"]:
                continue
            L = len(m.group(2))
            if (L == 1) != (exp_len == 1):
                continue
            body = []
            for nxt in lines[idx:idx + 60]:
                n2 = _fix_ocr_hdr(nxt.strip())
                if body and re.match(r"^\s*\d{1,3}\s*" + _HEAD_SEP + r"\s*答案\s*[:：]?\s*[A-D]{1,4}\b", n2):
                    break
                body.append(n2)
            cands.append({"answer": m.group(2), "text": "\n".join(body)})
    return cands[0] if cands else None


def main():
    import pypdf
    jx = pypdf.PdfReader(JIEXI)
    sh = pypdf.PdfReader(SHITI)

    ans = scan_answers(jx)
    stems = scan_stems_layout(sh)
    print("解析册 entries:", len(ans), " 试题册 stems:", len(stems))

    # 以试题册为master；按 (run,type,qno) 到解析册找答案/解析(避免中间缺题导致错位)
    alu = {}
    for a in ans:
        rk = (PART_MAP.get(a["part"]), a["type"], a["num"])
        alu.setdefault(rk, a)
    plu = {}
    for s in stems:
        plu.setdefault((s["disc"], s["type"], s["num"]), s)

    matched = 0
    qs, audit, unmatched = [], [], []
    jx_pages = build_answer_index(jx)
    sh_pages = [sh.pages[pno].extract_text() or "" for pno in range(len(sh.pages))]
    for s in stems:
        rk = (s["disc"], s["type"], s["num"])
        a = alu.get(rk)
        # 题干来源=layout(完整、不跨题截断)；default 仅在 layout 选项不完整时用来补 OPTIONS，
        # 绝不用 default 覆盖题干(其锚点定位会截掉题干开头)。
        # 尾置字母行排版(2×2 宫格 / 4 行选项)优先识别：其选项内容在字母行之前，常规锚定会
        # 把整段选项粘进题干、甚至被 default 回退抓到下一题选项(反馈 q66/q134 同类)。
        g2ok, g2stem, g2opts, g2why = split_grid2(s["lines"])
        if g2ok and g2stem and len(g2opts) == 4 and all(c.strip() for c in g2opts):
            stem, opts, ok, why = g2stem, g2opts, True, "grid2"
        else:
            lrok, lstem, lopts, lwhy = split_letterrow(s["lines"])
            if lrok and lstem and len(lopts) == 4 and all(c.strip() for c in lopts):
                stem, opts, ok, why = lstem, lopts, True, "letterrow"
            else:
                ok, stem, opts, why = split_stem_options(s["lines"])
                rep = repair_from_default(sh_pages, s)
                rok = False
                if rep is not None:
                    rst, rop = rep
                    rok = bool(rst.strip()) and len(rop) == 4 and all(c.strip() for c in rop)
                if ok:
                    # layout 已锚到 A-D：题干取 layout。选项若 layout 不全则用 default 的选项补。
                    if not (len(opts) == 4 and all(c.strip() for c in opts)) and rok:
                        opts = rop
                        why = "layout_stem+default_opts"
                elif rok:
                    stem, opts, why = rst, rop, "default"   # layout 完全没锚到，整体退回 default
        if a is None:
            exp_len = 1 if s["type"] == "single" else 2
            rec = recover_answer(jx_pages, s, exp_len)   # 解析册该学科页内按 qno 二次回收
            if rec is not None:
                a = {"answer": rec["answer"], "lines": rec["text"].splitlines()}
                why = "recovered"
        if a is None:
            unmatched.append(rk)
        audit.append({"order": len(qs), "disc": s["disc"], "type": s["type"], "qno": s["num"],
                      "stem_len": len(stem), "n_opt": len(opts),
                      "answer": a["answer"] if a else "", "split": why, "ok": ok, "has_ans": a is not None})
        qs.append({"disc": s["disc"], "type": s["type"], "qno": s["num"],
                   "content": stem, "options": opts, "answer": a["answer"] if a else "",
                   "analysis": clean_analysis(a["lines"]) if a else "", "ok": ok,
                   "has_ans": a is not None})
        if a is not None and ok:
            matched += 1

    # ---- 回补缺题：部分题目因跨页粘连未被 scan 切成独立块而缺失。
    # 内容来自 试题册 原文(人工核对过、存于 xiao1000_recovered.json)；答案+解析在运行期
    # 按 (学科→分册, 单选/多选, 题号) 从 解析册 现场补回，故重跑稳定且带解析。
    try:
        with open(os.path.join(OUT_DIR, "xiao1000_recovered.json"), encoding="utf-8") as _f:
            recovered = json.load(_f)
    except Exception:
        recovered = []
    if recovered:
        present = {(q["disc"], q["type"], q["qno"]) for q in qs}
        _TORD = {"single": 0, "multiple": 1, "judge": 2}
        for r in recovered:
            if (r["disc"], r["type"], r["qno"]) in present:
                continue
            a = alu.get((r["disc"], r["type"], r["qno"]))
            if a is None:
                exp_len = 1 if r["type"] == "single" else 2
                rec = recover_answer(jx_pages, {"disc": r["disc"], "type": r["type"],
                                                "num": r["qno"]}, exp_len)
                if rec is not None:
                    a = {"answer": rec["answer"], "lines": rec["text"].splitlines()}
            qs.append({"disc": r["disc"], "type": r["type"], "qno": r["qno"],
                       "content": r["content"],
                       "options": [o["content"] if isinstance(o, dict) else o for o in r["options"]],
                       "answer": a["answer"] if a else "",
                       "analysis": clean_analysis(a["lines"]) if a else "",
                       "ok": a is not None, "has_ans": a is not None})
            present.add((r["disc"], r["type"], r["qno"]))
        # 按 (学科出现顺序, 单选前/多选后, 题号) 稳定排序，使回补题落在正确缺口位置
        dord = {}
        for q in qs:
            dord.setdefault(q["disc"], len(dord))
        qs.sort(key=lambda q: (dord.get(q["disc"], 99), _TORD.get(q["type"], 1), q["qno"]))

    from collections import OrderedDict
    def dist():
        c = OrderedDict()
        for q in qs:
            c[(q["disc"], q["type"])] = c.get((q["disc"], q["type"]), 0) + 1
        return c

    print("total stems:", len(qs), " per(disc,type):", dict(dist()))
    print(f"OK且有答案: {matched}/{len(qs)}   无解析册答案: {len(unmatched)}")
    print("unmatched qnos 首 20:", unmatched[:20])

    os.makedirs(OUT_DIR, exist_ok=True)
    out = [{"order": i, "disc": q["disc"], "disc_label": DISC_LABEL.get(q["disc"]),
            "type": q["type"], "qno": q["qno"], "content": tidy(q["content"]),
            "options": [{"key": chr(65 + k), "content": tidy(cut_next_question(q["options"][k]))}
                        for k in range(min(len(q["options"]), 4))],
            "answer": q["answer"], "analysis": tidy(q["analysis"])}
            for i, q in enumerate(qs)]
    with open(os.path.join(OUT_DIR, "xiao1000_questions.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    with open(os.path.join(OUT_DIR, "xiao1000_audit.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["order", "disc", "type", "qno", "stem_len", "n_opt", "answer", "split", "ok", "has_ans"])
        w.writeheader()
        w.writerows(audit)
    print("WROTE", OUT_DIR)


if __name__ == "__main__":
    main()
