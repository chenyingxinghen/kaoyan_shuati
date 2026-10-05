"""2026-10-04 用户反馈回归：PDF 核对后的定点修复及已打包题库。

在仓库根目录运行：python -m unittest scripts.test_kaoyan_feedback -v
"""
import copy
import gzip
import json
from pathlib import Path
import sqlite3
import unittest

from scripts import import_kaoyan_politics as importer


ROOT = Path(__file__).resolve().parents[1]
# App qid 不等于书内题号：入库前会过滤无效题，单选和多选再连续编号。
CASES = {
    "xiao1000_2026_shigang_q151": ("shigang", "multiple", 16),
    "xiao1000_2026_shigang_q157": ("shigang", "multiple", 23),
    "xiao1000_2026_shigang_q16": ("shigang", "single", 16),
    "xiao1000_2026_sixiu_q46": ("sixiu", "single", 46),
    "xiao1000_2026_xinsi_q110": ("xinsi", "multiple", 10),
    "xiao1000_2026_xinsi_q202": ("xinsi", "multiple", 103),
}
EXPECTED_D = {
    ("shigang", "multiple", 23): "使得资产阶级新文化开始打破封建文化独占文化阵地的局面",
    ("shigang", "single", 16): "梁启超",
    ("sixiu", "single", 46): "奉献",
    ("xinsi", "multiple", 10): "是党和人民应对一切不确定性的最大确定性、最大底气、最大保证",
}


class FeedbackFixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        data = json.loads((ROOT / importer.XIAO).read_text(encoding="utf-8"))
        cls.raw = {(q["disc"], q["type"], q["qno"]): q for q in data}

    def fixed(self, key):
        q = self.raw[key]
        content, options, analysis = importer.apply_xiao_curated(
            *key, q["content"], importer.sanitize_options(copy.deepcopy(q["options"])), q["analysis"],
        )
        return content, options, importer.clean_analysis(analysis)

    def test_chapter_titles_are_removed_from_four_d_options(self):
        for key, expected in EXPECTED_D.items():
            with self.subTest(key=key):
                _, options, _ = self.fixed(key)
                self.assertEqual(expected, options[3]["content"])
                self.assertEqual(self.raw[key]["options"][:3], options[:3])

    def test_shigang_analysis_keeps_own_explanation_and_comparison(self):
        key = ("shigang", "multiple", 16)
        analysis = self.fixed(key)[2]
        expected = importer.clean_analysis(self.raw[key]["analysis"].split("\n亿答案BCD", 1)[0])
        self.assertEqual(expected, analysis)
        self.assertIn("A、B、C 正确", analysis)
        self.assertIn("不涉及革命问题。D 错误。", analysis)
        self.assertIn("点拨1905年至1907年间", analysis)
        self.assertTrue(analysis.endswith("第259页表格。"))
        self.assertNotIn("中国民族资本主义", analysis)

    def test_employment_analysis_does_not_include_next_question(self):
        key = ("xinsi", "multiple", 103)
        analysis = self.fixed(key)[2]
        expected = importer.clean_analysis(self.raw[key]["analysis"].split("\n趋近", 1)[0])
        self.assertEqual(expected, analysis)
        self.assertIn("劳动者自主就业、市场调节就业、政府促进就业和鼓励创业", analysis)
        self.assertTrue(analysis.endswith("A、C、D正确,B 错误。"))
        self.assertNotIn("国家安全", analysis)

    def test_shigang_single_analysis_has_no_chapter_fragment(self):
        analysis = self.fixed(("shigang", "single", 16))[2]
        self.assertTrue(analysis.endswith("A正确。"))
        self.assertIn("爱国志士", analysis)
        self.assertNotIn("第一音", analysis)

    def test_fixes_are_idempotent_and_preserve_stems(self):
        for key in CASES.values():
            with self.subTest(key=key):
                once = self.fixed(key)
                twice = importer.apply_xiao_curated(*key, *copy.deepcopy(once))
                self.assertEqual(once, twice)
                self.assertEqual(self.raw[key]["content"], once[0])
                if key not in EXPECTED_D:
                    self.assertEqual(self.raw[key]["options"], once[1])

    def test_seed_and_android_asset_contain_fixes_under_same_ids(self):
        seed = gzip.decompress((ROOT / importer.GZ_DB).read_bytes())
        asset = (ROOT / "AndroidAPP/app/src/main/assets/openexam_kaoyan.db").read_bytes()
        self.assertEqual(seed, asset)
        db = sqlite3.connect(":memory:")
        db.row_factory = sqlite3.Row
        try:
            db.deserialize(seed)
            self.assertEqual("ok", db.execute("PRAGMA integrity_check").fetchone()[0])
            for qid, key in CASES.items():
                with self.subTest(qid=qid):
                    row = db.execute("SELECT * FROM questions WHERE id=?", (qid,)).fetchone()
                    self.assertIsNotNone(row)
                    content, options, analysis = self.fixed(key)
                    self.assertEqual(content, row["content"])
                    self.assertEqual(options, json.loads(row["options"]))
                    self.assertEqual(analysis, row["analysis"])
                    self.assertEqual(self.raw[key]["answer"], row["answer"])
                    self.assertEqual(key[1], row["type"])
        finally:
            db.close()


if __name__ == "__main__":
    unittest.main()
