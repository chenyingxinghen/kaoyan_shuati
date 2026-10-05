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
MANMAN = os.path.join(DATA, "习题/ocr/漫漫学1500/manmanxue1500_questions.json")
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


# 解析正文开头的 OCR 残留头标(肖1000 解析册把每题 【名师精析】 认成裸"简析"/"知识储备题简析")
# 以及 OCR 把"简析"识别成乱码(置近/箍近/道近/…，2026-09 整库枚举 45+ 种双字损坏 + 单字 迪)，
# 整段剥掉。仅命中解析开头，不影响正文。
_ANA_GARB = (
    "置近|箍近|道近|简近|置也|宜近|置虹|黄虹|箍板|匿近|道板|董虹|偏近|筒析|蜀机|直扬|"
    "董近|直机|他近|置扬|直近|值板|置疝|葡近|苞近|画也|直板|登机|道虹|超虹|阐近|感运|"
    "尊板|也机|但近|登近|知近|理近|道机|也近|阑近|葛虹|良近|宣近|蜀近|地机|迪"
)
_ANA_HEAD = re.compile(
    r"^\s*(?:知识储备题\s*)?(?:简析|精析|解析|析枂|名师简析|名师精析|名师解析|" + _ANA_GARB + r")\s*"
)


# 解析册印刷把“…加快发展。正确。因此,本题选X”这类收尾按栏宽折到下一行；parse 层
# clean_analysis 见到该行以句末标点(。)结束就另起一段、并把段尾 。 剥掉，于是在库/App 里
# 变成一个“正确。/错误。”孤行 + 上一句缺句号悬空(反馈 q35 新思想: 换行引起的错误解析)。
# 凡下一行以裸判定词“正确/错误/误”开头(其后无选项字母，是上句收尾的换行延续)，把它接回
# 上一行并补回句号，整段还原成连贯正文。整库仅 xiao1000 命中此形态(5 处)，其余来源为 0。
_VERDICT_LINE = re.compile(r"^(正确|错误|误)\s*[，。]")


def _rejoin_verdict_lines(s):
    out = []
    for ln in s.split("\n"):
        t = ln.strip()
        if t and out and _VERDICT_LINE.match(t):
            prev = out[-1].rstrip()
            if not re.search(r"[。！？；，、:：…]$", prev):
                prev += "。"
            out[-1] = prev + t
        else:
            out.append(ln)
    return "\n".join(out)


def clean_analysis(s):
    if not s:
        return ""
    # 简单清洗：剥解析正文开头的残留头标(简析/误字)与前导 HTML 标记(真题站内 <p> 等)
    s = re.sub(r"^\s*<[^>]*>\s*", "", s)
    s = re.sub(_ANA_HEAD, "", s)
    s = _rejoin_verdict_lines(s)
    return s.strip()


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
    """清理选项内容：截断到下一题锚点(换行+数字+点)/粘上的章节目录标题等跨题污染。"""
    _CN = "一二三四五六七八九十"
    out = []
    for o in opts:
        c = o["content"]
        # 遇到 “换行 数字. ” 之类(误并入下一题)即截断
        cut = re.split(r"[\n]\s*\d{1,3}\s*[.、．，]", c)
        c = cut[0]
        # 剥粘在选项尾部的"下一章/节/导论标题"(反馈: 选项D后误接下一章章节标题);
        # 只要"第X章/节/导论"不在选项开头即视作粘上的标题, 一刀切掉(其后的 ] 、 等一并去)
        m = re.search(r"(第[" + _CN + r"]{1,2}章|第[" + _CN + r"]{1,2}节|导论)", c)
        if m and m.start() > 0:
            c = c[:m.start()]
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


# ---------- 反馈点修: 源 PDF 文本层 OCR 损坏的题干/选项真文覆盖 ----------
# 26肖1000-试题册.pdf 的文本层对个别字识别错(如 “。”+“这句话” 认成 “J 这句话”)或在选项字间
# 塞入乱符(. •)，印刷页其实是干净的(已对该页渲染 OCR 核对真文)。损坏在 PDF 文本层,重跑
# parse_xiao1000 会原样带出,故真文覆盖落在 import 层(不改 parse 输出、保持其 byte 重现不变式)。
# key=(disc, type, qno); content_fixes 按 (坏文→真文) 顺序执行; option_fixes 按选项字母覆盖。
XIAO_CURATED = {
    ("mayuan", "single", 15): {
        "content_fixes": [
            ("存在的只有动作J 这句话的簿用在于下不了",
             "存在的只有动作。”这句话的错误在于否认了"),
        ],
        "option_fixes": {
            "A": "世界万物是永恒发展的",
        },
    },
    # 组合式单选(xinsi 单选36 / App 序35): 试题册把 4 个 ①②③④ 陈述印成 2×2 宫格、A-D 是
    # 组合对且 PDF 文本层丢了 ①②③④ 字形, 于是题干被截断、续文漏进选项 A/B。按反馈真文整段固化。
    ("xinsi", "single", 36): {
        "content": "新质生产力是创新起主导作用，摆脱传统经济增长方式、生产力发展路径，具有高科技、高效能、高质量特征，符合新发展理念的先进生产力质态。新质生产力的显著特点是创新，这种创新包括：\n①生产资料所有制和产品分配层面的创新\n②技术和业态模式层面的创新\n③管理和制度层面的创新\n④价值观和意识形态层面的创新",
        "option_fixes": {
            "A": "①②",
            "B": "①③",
            "C": "②④",
            "D": "②③",
        },
        "analysis_fixes": [("加快发展。\n正确。", "加快发展。\n②③正确。")],
    },
    # ---- 组合式单选批量点修(2026-09 反馈: 这类 ①…④ + 选项为组合对 的题, PDF 文本层丢 ①字形,
    # 宫格排版把题干截断进选项, 整批塌成错误单选)。答案字母来自解析册已正确; 只整段固题干+按字母重写选项。
    ("mayuan", "single", 1): {
        "content": "马克思、恩格斯并不是先知先觉的圣人，他们从小面对的也是一个充满矛盾的现实世界，在家庭、学校和社会的影响下，也曾接受过那个时代的唯心主义和资产阶级民主主义思想。但是资本主义制度的弊端和劳动群众渴求解放的呼声，促使他们立志进行社会变革，并走上求索科学真理的道路。1844年2月，马克思、恩格斯发表在《德法年鉴》上的论文表明，他们\n①完成了从唯心主义向唯物主义的转变\n②完成了从革命民主主义向共产主义的转变\n③实现了历史观上的伟大变革\n④为创立马克思主义奠定了思想前提",
        "option_fixes": {"A": "①②③", "B": "②③④", "C": "①③④", "D": "①②④"},
        "analysis_fixes": [("奠定了思想前提。\n正确。", "奠定了思想前提。\n①②④正确。"),
                           ("伟大变革。\n误。因此", "伟大变革。\n③错误。因此")],
    },
    ("maozhongte", "single", 3): {
        "content": "马克思主义中国化时代化的理论成果是一脉相承又与时俱进的关系。它们都是\n①关于中国革命、建设、改革的正确的理论原则和经验总结\n②马克思列宁主义在中国的运用和发展\n③全国各族人民团结奋斗的共同思想基础\n④党和国家必须长期坚持的指导思想",
        "option_fixes": {"A": "①②③", "B": "①②④", "C": "①③④", "D": "②③④"},
        "analysis_fixes": [("共同思想基础。 正确。", "共同思想基础。②③④正确。"),
                           ("与革命无关。 错误。", "与革命无关。①错误。")],
    },
    ("xinsi", "single", 9): {
        "content": "习近平总书记指出：“中国式现代化，是中国共产党领导的社会主义现代化，既有各国现代化的共同特征，更有基于自己国情的中国特色。”党的二十大集中概括了中国式现代化五个方面的中国特色，深刻揭示了中国式现代化的科学内涵，这是理论概括，也是实践要求。关于中国式现代化，以下内容正确的是\n①人口规模巨大的现代化是中国式现代化的显著特征\n②全体人民共同富裕的现代化是中国式现代化的本质特征\n③既要物质富足、也要精神富有，是中国式现代化的崇高追求\n④促进人与自然和谐共生，是中国式现代化区别于西方现代化的显著标志",
        "option_fixes": {"A": "①②③", "B": "①②④", "C": "①③④", "D": "②③④"},
    },
    ("shigang", "single", 1): {
        "content": "从1840年鸦片战争开始到1949年中华人民共和国成立之前的近代中国，是半殖民地半封建社会。所谓“半殖民地”，是指\n①中国有一半的国土成了殖民地\n②中国出现了资本主义经济\n③中国在实际上已经丧失拥有完整主权的独立国的地位\n④中国仍然维持着独立国家和政府的名义，还有一定的主权",
        "option_fixes": {"A": "①②③", "B": "②④", "C": "③④", "D": "②③④"},
        "analysis_fixes": [("被称作半殖民地。 正确。", "被称作半殖民地。③④正确。")],
    },
    ("shigang", "single", 2): {
        "content": "从鸦片战争开始，到1949年中华人民共和国成立前，中国都属于半殖民地半封建社会。半殖民地半封建社会性质，决定了近代中国社会矛盾呈现错综复杂的状况。在诸多社会矛盾中，贯穿中国半殖民地半封建社会的始终，并对近代中国社会的发展变化起着决定性作用的矛盾是\n①帝国主义和中华民族的矛盾\n②工人阶级和资产阶级的矛盾\n③封建主义和人民大众的矛盾\n④各帝国主义国家在中国争夺的矛盾",
        "option_fixes": {"A": "①②", "B": "②③", "C": "①③", "D": "③④"},
    },
    ("shigang", "single", 35): {
        "content": "中国资产阶级革命派与改良派的根本不同之处在于，资产阶级革命派\n①在踏上革命道路之时就高举举起民主革命的旗帜\n②始终重视广泛动员人民群众的力量\n③最先制定了明确的反帝反封建的革命纲领\n④选择了以武装起义推翻清朝统治的斗争方式",
        "option_fixes": {"A": "①②", "B": "②③", "C": "③④", "D": "①④"},
        "analysis_fixes": [("根本不同之处。 正确。", "根本不同之处。①④正确。"),
                           ("原因。\n错误，也不符合题意。", "原因。\n②③错误，也不符合题意。")],
    },
    ("shigang", "single", 48): {
        "content": "十月革命发生在其国情与中国相同或近似的俄国，因而对中国的先进分子具有特殊吸引力。青年毛泽东说，“我看俄国式的革命，是无可如何的山穷水尽诸路皆走不通了的一个变计”，“只此方法较之别的改造方法所含可能的性质为多”。十月革命发生时的俄国国情与中国“相同或近似”是指\n①半殖民地半封建的社会性质\n②“三座大山”阻碍了社会进步和发展\n③封建压迫严重\n④经济文化落后",
        "option_fixes": {"A": "①②", "B": "①④", "C": "②④", "D": "③④"},
        "analysis_fixes": [("特殊吸引力。\n正确。", "特殊吸引力。\n③④正确。"),
                           ("三座大山”。 错误。", "三座大山”。①②错误。")],
    },
    ("shigang", "single", 56): {
        "content": "1924年1月，中国国民党第一次全国代表大会由孙中山主持在广州举行。大会审议通过的《中国国民党第一次全国代表大会宣言》，对三民主义作出新的解释，即“新三民主义”。新三民主义的“新”体现在\n①在民族主义中突出了反对帝国主义的内容\n②在民权主义中强调民主权利应“为一般平民所共有”\n③在民生主义中强调只有“兴民权改民主”才是中国的唯一出路\n④把民生主义概括为“平均地权”和“节制资本”两大原则",
        "option_fixes": {"A": "①②③", "B": "②③④", "C": "①②④", "D": "①③④"},
    },
    ("shigang", "single", 62): {
        "content": "土地革命战争时期，党从残酷的现实中认识到，没有革命的武装就无法战胜武装的反革命，就无法夺取中国革命胜利，就无法改变中国人民和中华民族的命运，必须以武装的革命反对武装的反革命。党领导举行了南昌起义、秋收起义、广州起义和其他许多地区起义。其中，秋收起义不同于南昌起义的特点有\n①以攻占敌人控制比较薄弱的农村为目标\n②公开打出了“工农革命军”的旗帜\n③是中国共产党独立领导的革命战争\n④不仅是军队的行动，而且有数量众多的工农武装参加",
        "option_fixes": {"A": "①②", "B": "①③", "C": "②④", "D": "③④"},
        "analysis_fixes": [("旗帜）。 正确。", "旗帜）。②正确。"),
                           ("工农武装参加。\n确。", "工农武装参加。\n④正确。"),
                           ("这一点与南昌起义是相同的。 错误。", "这一点与南昌起义是相同的。①错误。"),
                           ("独立领导的革命战争。 错误。因此,本题选c。",
                            "独立领导的革命战争。③错误。因此,本题选C。")],
    },
    ("shigang", "single", 106): {
        "content": "《论人民民主专政》是毛泽东在中国新民主主义革命取得决定性胜利、全国性政权即将建立的时刻，为纪念中国共产党成立28周年而撰写的文章。文章论述了即将成立的中华人民共和国的国家性质，各阶级在国家中的地位及其相互关系，国家对内、对外政策等。《论人民民主专政》这篇文章\n①奠定了新中国国家政权的理论基础和一定发展阶段上的政策基础\n②为即将成立的新中国作了政治理论准备\n③与中共七届二中全会的决议一起构成了《中国人民政治协商会议共同纲领》的基础\n④是中国共产党人开始探索适合中国国情的社会主义建设道路的标志",
        "option_fixes": {"A": "①②③", "B": "①②④", "C": "①③④", "D": "②③④"},
    },
    ("sixiu", "single", 13): {
        "content": "走向绞刑架的李大钊，发出了“共产主义在中国必然得到光辉的胜利”的坚贞誓言。面对敌人屠刀的夏明翰，写下“砍头不要紧，只要主义真。杀了夏明翰，还有后来人”的雄壮诗篇。面对敌人6天内9次劝降，瞿秋白作出了“人爱自己的历史，比鸟爱自己的翅膀更厉害，请勿撕破我的历史”的铿锵回答。理想信念是精神之“钙”。信念是认知、情感和意志的有机统一体。信念的特征包括\n①超越性\n②支撑性\n③执着性\n④多样性",
        "option_fixes": {"A": "①②③", "B": "①②④", "C": "②③④", "D": "①③④"},
        "analysis_fixes": [("最大的统摄力。 正确。", "最大的统摄力。②③④正确。"),
                           ("实践性和时代性。 符合题意。", "实践性和时代性。①不符合题意。")],
    },
    # ---- 2026-09 反馈点修(六条) ----
    # sixiu 66(反馈 order134): 试题册 2×2 宫格选项被 layout 并成一行(字母序 A,B,D,C)，anchor_fail 后
    # default 回退把 下一题(67)的题干+选项 整段抓进本题。真文整段固化：题干=66 题干、选项=职业道德规范，
    # 答案 ABCD 保持正确(解析册已核对)。
    ("sixiu", "multiple", 66): {
        "content": "奉献社会是社会主义职业生活中的基本道德规范之一。此外，社会主义职业生活中的基本道德规范还有",
        "option_fixes": {"A": "爱岗敬业", "B": "诚实守信", "C": "办事公道", "D": "热情服务"},
    },
    # mayuan 120(反馈 order305): 解析册该题无简析正文(只有题头+出处)，旧解析被 121・答案BC 串题覆盖，
    # 用户反馈"参考答案有误"。答案 ACD 已核对正确；按教材标准写法补解析，避免空解析。
    ("mayuan", "multiple", 120): {
        "analysis": "价值规律的作用表现在：第一，自发地调节生产资料和劳动力在社会各生产部门之间的分配比例；第二，自发地刺激社会生产力的发展；第三，自发地调节社会收入的分配。A、C、D正确。B项“自发地调节雇佣工人与资本家之间的利益再分配”不属于价值规律的作用。",
    },
    # sixiu 28(反馈 order28): 解析册该题无简析正文(只有题头+出处+"第五章")，旧解析被章节标记填满。
    # 答案 A 已核对正确；按教材标准写法补解析。
    ("sixiu", "single", 28): {
        "analysis": "社会主义核心价值观的先进性，集中体现在它是社会主义所坚持和追求的价值理念，反映了广大人民群众的根本利益和社会发展的正确方向。A正确。B体现的是社会主义核心价值观的开放包容性，C体现的是其现实性，D体现的是其民族性，均不是“先进性”集中体现的表述。",
    },
    # sixiu 18: 点拨前缀 OCR 损坏 "迹" → "点拨"。
    ("sixiu", "single", 18): {
        "analysis_fixes": [("迹当代", "点拨当代")],
    },
    # ---- 2026-09 反馈同期补缺: 试题册选项字母 OCR 乱码导致 anchor_fail → 空题干被过滤、题缺失。
    # 题干/选项按试题册原版真文整段固化(OCR 损坏字已人工校正), 答案/解析已从解析册正常取到。
    ("mayuan", "single", 28): {
        "content": "民间有一种迷信说法叫作“喜鹊叫喜，乌鸦叫丧”。这句话的错误在于否认了联系的",
        "options": {"A": "普遍性", "B": "多样性", "C": "客观性", "D": "条件性"},
    },
    ("sixiu", "single", 65): {
        "content": "权利公平是公平正义的重要内容之一。以下不属于权利公平内容的是",
        "options": {"A": "权利主体平等", "B": "发展机会和发展前景平等",
                    "C": "权利保护和权利救济平等", "D": "享有的权利特别是基本权利平等"},
    },
    # shigang 72(2×2 宫格恢复): 宫格第 3 格 OCR 丢字 "军" → 真文 "红军"(其余三格 layout 已干净)。
    # 2026-09-13: 该题实为组合式单选，已在下文整批点修中整段重建，此处的部分修补随之作废。
    # ---- 2026-09-09 反馈点修 ----
    # mayuan 62(反馈 order255): 解析册 PDF 文本层在句中断行("…颠倒了认"后直接是下一题头)，
    # 尾句缺失；按教材真文补全(C 项错误在颠倒二者关系)。
    ("mayuan", "multiple", 62): {
        "analysis_fixes": [("颠倒了认", "颠倒了感性认识和理性认识的关系，C错误")],
    },
    # ---- 2026-09-11 反馈点修 ----
    # mayuan 31(反馈 order224): 试题册 PDF 文本层题干尾部乱码("的错误在于割裂了"被认成
    # "的曾族在于朝.1. \x7f \x7f")，选项 B "防交系"="的关系"。按印刷真文覆盖。
    ("mayuan", "multiple", 31): {
        "content_fixes": [("的曾族在于朝.1. \x7f \x7f", "的错误在于割裂了")],
        "option_fixes": {"B": "共性与个性的关系"},
    },
    # ---- 2026-09-13 反馈点修: 组合式单选"①②③④ 陈述 + A-D 组合对"整批补齐 ----
    # 试题册把 4 条陈述印成 2×2 宫格(或每行一条)，A-D 组合对另起一行。该 PDF 是扫描页 + OCR 文本层，
    # 扫描页上的 ①②③④ 圈码字形不在文本层里，于是那一整行只剩 "A.  B.  C.  D."，split_grid2 /
    # split_letterrow 便把宫格里的"陈述"当成了 A-D 选项 → 全库 22 题塌成"选项=陈述"。组合对真文无法
    # 从文本层恢复，故按渲染原页逐题核对后固化(题干=原题干+①②③④ 陈述，选项=组合对，答案沿用解析册)。
    # 以下 10 题为 2026-09-13 补齐(另 12 题更早已点修)。
    ("mayuan", "single", 63): {
        "content": "“调查研究是从实际出发的中心一环。没有调查就没有发言权，没有调查也没有决策权。”调查才能获得第一手材料，获得感性认识。感性认识的突出特点是\n①直接性\n②深刻性\n③具体性\n④间接性",
        "option_fixes": {"A": "①③", "B": "①②", "C": "②③", "D": "②④"},
        "analysis_fixes": [("突出特点。 正确。", "突出特点。①③正确。")],
    },
    ("xinsi", "single", 25): {
        "content": "当前和今后一个时期是以中国式现代化全面推进强国建设、民族复兴伟业的关键时期。中国式现代化是在改革开放中不断推进的，也必将在改革开放中开辟广阔前景。进一步全面深化改革，必须\n①以推进中国式现代化为主题\n②以深化经济体制改革为重点\n③以文化体制改革为牵引\n④以制度建设为主线",
        "option_fixes": {"A": "①②③", "B": "①②④", "C": "①③④", "D": "②③④"},
        "analysis_fixes": [("制度建设。\n③错误。", "制度建设。①②④正确，③错误。")],
    },
    ("xinsi", "single", 44): {
        "content": "教育是国之大计、党之大计，是功在当代、利在千秋的德政工程。建成教育强国是近代以来中华民族梦寐以求的美好愿望，是实现以中国式现代化全面推进强国建设、民族复兴伟业的\n①先导任务\n②坚实基础\n③战略支撑\n④鲜明标识",
        "option_fixes": {"A": "①②④", "B": "②③④", "C": "①③④", "D": "①②③"},
        "analysis_fixes": [("扎实迈进。 ，④错误。", "扎实迈进。①②③正确，④错误。")],
    },
    ("xinsi", "single", 84): {
        "content": "国家安全是民族复兴的根基，是中国式现代化行稳致远的重要基础。实现中华民族伟大复兴的中国梦，保证人民安居乐业，国家安全是头等大事。保障国家安全的治本之策在于\n①提高防范化解重大风险能力\n②健全国家安全体系\n③建设更高水平的平安中国\n④加强国家安全制度建设",
        "option_fixes": {"A": "①②", "B": "①④", "C": "②③", "D": "②④"},
        "analysis_fixes": [("重要支撑。 正确。", "重要支撑。②④正确。")],
    },
    ("shigang", "single", 42): {
        "content": "新文化运动的倡导者猛烈抨击以孔子为代表的“往圣先贤”。通过批判孔学，动摇了封建正统思想的统治地位，在中国社会掀起一股思想解放的潮流。五四前新文化运动的倡导者所使用的主要武器是\n①马克思主义的民主与科学理论\n②个性解放思想\n③中华优秀传统文化的民本思想\n④进化论观点",
        "option_fixes": {"A": "①②", "B": "②③", "C": "③④", "D": "②④"},
    },
    ("shigang", "single", 68): {
        "content": "1929年12月，红四军党的第九次代表大会在福建上杭县古田村召开（史称古田会议）。大会通过了毛泽东起草的古田会议决议。古田会议决议是中国共产党和红军建设的纲领性文献，是党和人民军队建设史上的重要里程碑。古田会议创造性地解决了在农村环境中、在党组织和军队以农民为主要成分的条件下\n①如何保持党的无产阶级先锋队性质的重大问题\n②如何建设党领导的新型人民军队的重大问题\n③如何建设革命根据地的重大问题\n④如何找到新的革命道路的重大问题",
        "option_fixes": {"A": "②③", "B": "①②④", "C": "①②", "D": "②③④"},
        "analysis_fixes": [("重大问题。\n③④错误。", "重大问题。①②正确，③④错误。")],
    },
    ("shigang", "single", 72): {
        "content": "1934年10月，中共中央、中革军委率中央红军主力等8.6万余人踏上战略转移的漫漫征程，开始了世界历史上前所未有的壮举——长征。中央红军进行长征的直接原因是\n①民族矛盾已经上升为主要矛盾\n②停止内战，北上抗日\n③红军在第五次反“围剿”作战中遭到失败\n④红军已无在原地扭转战局的可能",
        "options": {"A": "①②", "B": "①③", "C": "②③", "D": "③④"},
        "analysis_fixes": [("壮举。 正确。", "壮举。③④正确。")],
    },
    ("shigang", "single", 75): {
        "content": "1935年1月7日，红军攻克黔北重镇遵义。1月15日至17日，中共中央在此召开政治局扩大会议，即遵义会议。遵义会议的鲜明特点是\n①实事求是\n②坚持真理\n③守正创新\n④修正错误",
        "option_fixes": {"A": "①②", "B": "②③", "C": "①③", "D": "②④"},
    },
    ("shigang", "single", 110): {
        "content": "新中国成立初期，以毛泽东为主要代表的中国共产党人提出了三大外交方针，为独立自主的新中国外交关系奠定了基础。三大外交方针是指\n①“另起炉灶”\n②“一边倒”\n③“真正不结盟”\n④“打扫干净屋子再请客”",
        "option_fixes": {"A": "①②③", "B": "②③④", "C": "①②④", "D": "①③④"},
        "analysis_fixes": [("和有关协定。\n“真正不结盟”", "和有关协定。①②④正确。“真正不结盟”")],
    },
    ("shigang", "single", 114): {
        "content": "20世纪50年代，我国在对资本主义工商业进行社会主义改造的过程中，创造了一系列国家资本主义具体形式，包括\n①加工订货\n②统购包销\n③经销代销\n④统购统销",
        "option_fixes": {"A": "①②④", "B": "②③④", "C": "①③④", "D": "①②③"},
        "analysis_fixes": [("公私合营。 正确。", "公私合营。①②③正确。"),
                           ("被取消。 不符合题意。", "被取消。④不符合题意。")],
    },
    # ---- 2026-09-15 反馈点修 ----
    # shigang 4(反馈"题干错漏"): 旧解析把题干、本题选项、下一题(5)题干+选项全揉进 content，选项则是
    # 第 5 题的。根因是 `C 《天津条约》` 的 C 后随 《 不在 _OPT_LETTER 的允许集里 → anchor_fail →
    # default 回退抓错区域；已在 parse 层修掉(字母后接受 CJK 起始标点)，此处仅订正解析里的 OCR 乱码：
    # 印刷真文是 "点拨 1901年《辛丑条约》…"，文本层认成了 "邀J901年"。
    ("shigang", "single", 4): {
        "analysis_fixes": [("邀J901年", "点拨 1901年")],
    },
    # xinsi 多1(反馈"选项有误"): 旧选项 D 吞掉了下一题(2)的题号+题干+四个选项，根因是下一题头写作
    # "2- 习近平…"(OCR 把 "." 认成 "-")、不在题号分隔符集里 → 未被切成新题；已在 parse 层修掉
    # (_SEP 纳入 "-"/"－"，cut_next_question 同步覆盖)。解析册该题只印了 "1.答案ABCD + 出处"、
    # 本就无简析正文，按选项自述补一段知识储备型解析，避免空解析。
    ("xinsi", "multiple", 1): {
        "analysis": "知识储备题习近平新时代中国特色社会主义思想是在世界百年未有之大变局加速演进、中华民族伟大复兴进入关键时期、科学社会主义在21世纪的中国焕发出新的蓬勃生机、中国共产党自我革命开辟新的境界的时代背景下创立并不断丰富发展的。A、B、C、D均正确。",
    },
    # ---- 2026-10-04 反馈点修(6 题，页码均为 PDF 页序、从 1 起) ----
    # 章节头在 OCR 中缺失或变成“笛一音/雷一音”，通用“第X章”清洗无法命中。
    # 按试题册原页仅覆盖 D，不放宽全库截断规则，以免误删正常选项；不增删题，保持 App qid 稳定。
    # shigang 单16(App q16): 试题册 p123；解析册 p128 末尾也混入“第二章”的 OCR 残片。
    ("shigang", "single", 16): {
        "option_fixes": {"D": "梁启超"},
        "analysis_fixes": [("、第一音", ""), ("爱国志土", "爱国志士")],
    },
    # shigang 多23(App q157): 试题册 p142，D 后粘上第三章“辛亥革命与君主专制制度的终结”。
    ("shigang", "multiple", 23): {
        "option_fixes": {"D": "使得资产阶级新文化开始打破封建文化独占文化阵地的局面"},
    },
    # sixiu 单46(App q46): 试题册 p166，D 后粘上第六章“学习法治思想 提升法治素养”。
    ("sixiu", "single", 46): {
        "option_fixes": {"D": "奉献"},
    },
    # xinsi 多10(App q110): 试题册 p100，D 后粘上第一章标题。
    ("xinsi", "multiple", 10): {
        "option_fixes": {"D": "是党和人民应对一切不确定性的最大确定性、最大底气、最大保证"},
    },
    # shigang 多16(App q151): 解析册 p147，“17.答案”被 OCR 识别成“亿答案”，
    # 导致下一题的答案和简析并入；保留本题简析及比较两次论战的完整“点拨”。
    ("shigang", "multiple", 16): {
        "analysis_fixes": [
            ("\n亿答案BCD置物B、C、D正确。19世纪90年代以后中国民族资本主义有了初步发展，但并没有成为中国经济的主要形式。事实上，中国民族资本主义后来也没有成为中国经济的主要形式。A 错误。", ""),
        ],
    },
    # xinsi 多103(App q202): 解析册 p119，右栏第104题的题头在文本层缺失，
    # 其“社会保障”简析串入本题；只移除串题段，不改本题的就业方针解析或答案 ACD。
    ("xinsi", "multiple", 103): {
        "analysis_fixes": [
            ("\n趋近A、B、D正确。国家安全是民族复兴的根基，社会稳定是国家强盛的前提。C错误。", ""),
        ],
    },
}


def apply_xiao_curated(disc, qtype, qno, content, opts, analysis=""):
    """对命中的题套用 import 层真文覆盖(幂等: 只替换仍存在的坏文)。返回 (content, opts, analysis)。

    entry 支持:
      "content_fixes"  [(坏文→真文), ...] 在 content 内逐条替换
      "content"        整段覆盖 content(用于题干被换行/排版截断的题型, 如 组合式单选 题干被切进选项)
      "option_fixes"   {字母: 真文} 按字母整段覆盖选项(仅对已有选项生效)
      "options"        {字母: 真文} 整段重建选项列表(用于题干/选项整体锚定失败、options 为空的题)
      "analysis"       整段覆盖 analysis(解析册无简析正文、旧解析被串题覆盖的题, 按教材真文补写)
      "analysis_fixes" [(坏文→真文), ...] 在 analysis 内逐条替换
    """
    cur = XIAO_CURATED.get((disc, qtype, qno))
    if not cur:
        return content, opts, analysis
    if "content" in cur:
        content = cur["content"]
    for bad, good in cur.get("content_fixes", []):
        if bad in (content or ""):
            content = content.replace(bad, good)
    if "options" in cur:
        opts = [{"key": k, "content": v} for k, v in cur["options"].items()]
    for key, txt in (cur.get("option_fixes") or {}).items():
        for o in opts:
            if o["key"] == key:
                o["content"] = txt
    if "analysis" in cur:
        analysis = cur["analysis"]
    for bad, good in cur.get("analysis_fixes", []):
        if bad in (analysis or ""):
            analysis = analysis.replace(bad, good)
    return content, opts, analysis


def main():
    xiao = json.load(open(XIAO, encoding="utf-8"))
    manman = json.load(open(MANMAN, encoding="utf-8"))
    past = json.load(open(PAST, encoding="utf-8"))
    print("xiao json:", len(xiao), " manman json:", len(manman), " past:", len(past))

    papers = []          # {id,title,year,difficulty,category_prefix?, questions:[{..}]}
    # ---------- 肖1000: 按学科一篇 ----------
    by_disc = {}
    for x in xiao:
        by_disc.setdefault(x["disc"], []).append(x)
    for disc, qs in by_disc.items():
        good = []
        for x in qs:
            opts = sanitize_options(x.get("options") or [])
            content, opts, analysis = apply_xiao_curated(x.get("disc"), x.get("type"), x.get("qno"),
                                                          x.get("content") or "", opts,
                                                          x.get("analysis") or "")
            if valid_q(content, opts, x.get("answer")):
                x = dict(x, content=content, options=opts, analysis=analysis)
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
                "analysis": clean_analysis(q.get("analysis") or ""),
                "tags": "xiao1000",
            } for q in good],
            "kept": len(good), "total": len(qs),
        })

    # ---------- 漫漫学高分密训1500题(2027): 按学科一篇 ----------
    mm_by_disc = {}
    for x in manman:
        if not x.get("ok"):
            continue          # 仅入结构完整(题干+4选项+答案)的题,初步入库后靠 App 反馈点对点修
        mm_by_disc.setdefault(x["disc"], []).append(x)
    for disc, qs in mm_by_disc.items():
        good = []
        for x in qs:
            opts = sanitize_options(x.get("options") or [])
            if not valid_q(x.get("content"), opts, x.get("answer")):
                continue
            t = "multiple" if x["type"] == "multiple" else "single"
            ans = x["answer"].strip().upper()
            # 防御: type 与答案字母数不符 → 多选答案被OCR截断/单选被误标, 避免入错题
            if (t == "multiple" and len(ans) < 2) or (t == "single" and len(ans) != 1):
                continue
            good.append({"type": t,
                         "category": x.get("disc_label") or DISC_LABEL.get(disc, disc),
                         "content": x["content"], "options": opts,
                         "answer": ans,
                         "analysis": x.get("analysis") or "",
                         "tags": "manmanxue"})
        if not good:
            continue
        lab = DISC_LABEL.get(disc, disc)
        papers.append({
            "id": "manmanxue2027_%s" % disc,
            "title": "2027漫漫学高分密训1500题·%s" % lab,
            "year": 2027, "difficulty": 2, "cat": lab,
            "questions": good, "kept": len(good), "total": len(qs),
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
