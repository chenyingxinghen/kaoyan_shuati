<p align="center">
  <img src="src/renderer/assets/openexam-app-icon.png" width="96" alt="OpenExam App Icon" />
</p>

<h1 align="center">考研刷题 · kaoyan_shuati</h1>

<p align="center">
  考研政治题库 + Android 刷题应用 · 基于 OpenExam 桌面端代码的衍生仓库
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-Compose-24292F?style=flat-square&logo=android&logoColor=3DDC84" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-24292F?style=flat-square&logo=kotlin&logoColor=7F52FF" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Python-题库流水线-24292F?style=flat-square&logo=python&logoColor=3776AB" alt="Python" />
  <img src="https://img.shields.io/badge/SQLite-Local_Data-24292F?style=flat-square&logo=sqlite&logoColor=74C0FC" alt="SQLite" />
  <img src="https://img.shields.io/badge/Electron-上游桌面端-24292F?style=flat-square&logo=electron&logoColor=9FEAF9" alt="Electron" />
  <img src="https://img.shields.io/badge/License-GPL--3.0--or--later-24292F?style=flat-square" alt="GPL-3.0-or-later" />
</p>

---

## 这个仓库是什么

**一句话**：以 [OpenExam](https://github.com/lmk1010/OpenExam)（GPL-3.0-or-later）的桌面端代码为基底，**新增 Android 刷题端 + 考研政治题库生产流水线**的衍生仓库。

必须先讲清楚仓库之间的关系，否则容易误读 README：

| 仓库 | 归属 | 与本仓库的关系 |
| --- | --- | --- |
| [lmk1010/OpenExam](https://github.com/lmk1010/OpenExam) | 上游作者 lmk1010 | **代码基底**。GPL-3.0-or-later 授权，版权归原作者 |
| [chenyingxinghen/kaoyan_shuati](https://github.com/chenyingxinghen/kaoyan_shuati) | 本仓库 | 衍生开发。上游保留完整提交历史（`upstream/main..HEAD` 领先 17 个提交） |

上游 OpenExam 的定位是「公考 / 考证通用刷题桌面应用」，官网 [openexam.cc](https://openexam.cc)。**本仓库不下载、不分发、不替上游做宣传**，只做两件事：

1. 在其基础上做**考研政治**方向的题库与移动端；
2. 沉淀题库解析、清洗、校对、种子库构建的完整流水线。

上游桌面端代码（`src/`、`main.js`、`preload.js`、`package.json`）在本仓库**未做任何功能修改**，属于随上游继承的存量代码。因此下文凡标注「上游继承」的章节，描述的是上游能力，不代表本仓库的独立成果。

## 本仓库的实际增量

### 1. Android 刷题端（`AndroidAPP/`）

Kotlin + Jetpack Compose，原生应用，非 Web 套壳。appId `com.example.kaoshishuati`，17 个 Kotlin 源文件。

- `data/BankDb.kt`：首启把 `assets/openexam_kaoyan.db` 释放到应用私有目录并**只读**打开；提供 `papers()` / `questions(paperId)` / `questionById(id)`，含跨卷取题与 asset 逐字节自同步。
- `data/BankScope.kt`：题库作用域枚举，区分真题卷与习题册。
- `data/ProgressStore.kt` / `DoneStore.kt`：答题进度与「已刷」标记，跨卷共享。
- `data/WrongStore.kt`：错题本。
- `data/DrillRound.kt` / `DrillRoundStore.kt` / `DrillSettingsStore.kt`：专项练习的轮次与设置记忆，支持从断点续做。
- `data/FeedbackStore.kt` / `FeedbackReport.kt`：题面点修反馈与导出。
- `ui/MainScreen.kt`：Compose UI。题库列表按「真题 / 习题册」分册聚合 + 分层返回栈（带返回记忆），练习页单选点选即判、多选提交判分，作答后展示对错、正确答案与解析，结束页给得分；错题本、专项练习入口、已完成标记。
- 状态持久化基于 SharedPreferences，数据只留在本机。

构建与更多细节见 [`AndroidAPP/README.md`](AndroidAPP/README.md)。

### 2. 考研政治题库流水线（`scripts/*.py` + `data/kaoyan/`）

本仓库的**主要工作量在这里**，是一条从 PDF/网页原始素材到可发布 SQLite 种子库的可复跑流水线：

| 脚本 | 职责 |
| --- | --- |
| `scripts/import_kaoyan_politics.py` | 考研政治种子库总装：真题 + 肖1000 + 漫漫学1500 归并、学科标签映射、写入 `papers`/`questions` 并 gzip 输出 |
| `scripts/parse_xiao1000.py` | 肖1000 试题册/解析册 PDF 解析，跨页题干截断修复、2-across 版面还原、选项锚定 |
| `scripts/parse_manmanxue1500.py` | 漫漫学 1500 题解析，坐标感知抽取题干/选项/解析 |
| `scripts/audit_kaoyan_politics.py` | 题库审计：答案一致性、选项数、解析残留等 |
| `scripts/test_kaoyan_feedback.py` | 点修反馈回归测试 |

配套的 `scripts/*.js`（`crawl-saduck` / `import-saduck` / `audit-question-bank` 等）属**上游继承**的公考抓取链路。

数据目录约定：

- `data/kaoyan/真题/10-24年/`、`data/kaoyan/真题/网页抓取/` —— 历年真题原始素材与抓取产物。
- `data/kaoyan/习题/books/`（PDF 原件）、`ocr/`（OCR 中间产物）、`parsed/`（结构化 JSON + 审计 CSV）—— 肖1000、漫漫学 1500。
- `data/kaoyan/openexam.kaoyan-politics.seed.db.gz` —— 构建产物，schema 与上游一致（`papers` / `questions`），`subject='kaoyan'`。
- `data/kaogong/` —— **上游继承**的公考种子库与题图资源，本仓库未改动内容，只在 `89302df` 中迁移了存放位置。

## 题库现状

`data/kaoyan/openexam.kaoyan-politics.seed.db.gz`（约 1.3 MB）解压后同步至 `AndroidAPP/app/src/main/assets/openexam_kaoyan.db`（约 5.5 MB）：

- **25 卷 / 3322 题**，全部 `subject=kaoyan`
- 单选 1583 题、多选 1739 题
- 真题 15 卷：2010–2024 考研政治真题（每年 33 或 46 题）
- 习题 10 册，按学科分册
  - 肖秀荣 1000 题（2026）5 册：马原 406 / 毛中特 143 / 习思想 236 / 史纲 261 / 思修法治 179
  - 漫漫学高分密训 1500 题（2027）5 册：马原 331 / 毛中特 184 / 习思想 410 / 史纲 413 / 思修法治 238
- 学科分类：马克思主义基本原理、毛泽东思想和中国特色社会主义理论体系概论、习近平新时代中国特色社会主义思想概论、中国近现代史纲要、思想道德与法治、形势与政策以及当代世界经济与政治

题面质量问题有反馈闭环：`data/kaoyan/question_feedback.json` 记录 App 内点修结果，由 `test_kaoyan_feedback.py` 回归，再经 `import_kaoyan_politics.py` 覆盖重建种子库。

## 界面预览

以下截图来自**上游 OpenExam 桌面端**（`docs/assets/readme/`），本仓库未改动桌面端代码，仅作背景参考；本仓库 Android 端界面请直接看 [`AndroidAPP/README.md`](AndroidAPP/README.md)。

<p align="center">
  <img src="docs/assets/readme/demo1.png" alt="OpenExam Demo 1" width="49%" />
  <img src="docs/assets/readme/demo2.png" alt="OpenExam Demo 2" width="49%" />
</p>
<p align="center">
  <img src="docs/assets/readme/demo3.png" alt="OpenExam Demo 3" width="49%" />
  <img src="docs/assets/readme/demo4.png" alt="OpenExam Demo 4" width="49%" />
</p>
<p align="center">
  <img src="docs/assets/readme/demo5.png" alt="OpenExam Demo 5" width="100%" />
</p>

<p align="center">
  <sub>上游 OpenExam 桌面端 · 学习中心 / 我的成长 / AI 智能导师 / 成就系统 / 深色模式</sub>
</p>

## 目录结构

```
.
├── AndroidAPP/            # Android 端（Kotlin + Compose），本仓库新增
├── data/
│   ├── kaogong/           # 上游继承：公考种子库与题图（内容未改，仅迁移位置）
│   └── kaoyan/            # 本仓库新增：考研政治原始素材、OCR、解析结果、种子库
├── scripts/               # 题库流水线（*.py 为本仓库新增；*.js 多为上游继承）
├── docs/                  # 上游继承文档 + 素材
├── src/ main.js preload.js package.json   # 上游继承：Electron 桌面端，功能未改
└── .github/workflows/     # 上游继承 CI
```

## 快速开始

### Android 端（推荐，本仓库主入口）

```bash
cd AndroidAPP
./gradlew :app:assembleDebug     # Windows: gradlew.bat :app:assembleDebug
```

产物：`AndroidAPP/app/build/outputs/apk/debug/app-debug.apk`。依赖走阿里云镜像（`settings.gradle.kts`），已实测 `assembleDebug` 通过。

### 桌面端（上游继承，当前不可直接构建）

```bash
npm install
npm run dev
```

> ⚠️ **已知问题**：提交 `89302df` 把桌面端种子库与题图从 `data/` 迁到了 `data/kaogong/`，但 `package.json` 的 `extraResources`、`src/main/database.js:72` 的开发态路径、以及 `scripts/*.js` 中的默认种子库路径**都仍指向旧的 `data/openexam.seed.db.gz` 与 `data/question-assets`**，而这两个路径现已不存在。
>
> 后果：`npm run dev` 首次启动时种子库找不到；`npm run pack` / `dist:mac` / `dist:win` 会因 `extraResources` 源文件缺失而失败。修法是把上述引用统一改到 `data/kaogong/`，尚未实施 —— 见 [已知问题](#已知问题)。

### 桌面端常用命令（上游继承）

```bash
npm run build            # 构建前端
npm run rebuild:electron # 重建 Electron 原生依赖
npm run crawl:saduck     # 抓取公考题库（上游链路）
npm run build:saduck-seed
npm run sync:saduck-seed
npm run audit:question-bank
```

### 考研题库流水线（本仓库）

```bash
python scripts/parse_xiao1000.py           # 解析肖1000
python scripts/parse_manmanxue1500.py      # 解析漫漫学1500
python scripts/import_kaoyan_politics.py   # 归并并重建种子库
python scripts/audit_kaoyan_politics.py    # 审计
python scripts/test_kaoyan_feedback.py     # 点修反馈回归
```

改完种子库后需同步到 Android asset：解压 `data/kaoyan/openexam.kaoyan-politics.seed.db.gz`，覆盖 `AndroidAPP/app/src/main/assets/openexam_kaoyan.db`。

## 已知问题

- **桌面端构建链断裂**：`data/` → `data/kaogong/` 迁移后未同步 `package.json` / `src/main/database.js` / `scripts/*.js` 中的路径，桌面端 `dev` 与打包均受影响（详见[快速开始](#快速开始)）。
- **`AndroidAPP/README.md` 中的数据已过期**：仍写「22 卷 / 1687 题 / ~3 MB」，实际为 25 卷 / 3322 题 / ~5.5 MB；文件列表也未覆盖后新增的 `DrillRound*` / `DrillSettingsStore` / `FeedbackStore`。
- **Release 链路指向位置存疑**：`package.json` 的 `build.publish` 仍写 `owner: lmk1010 / repo: OpenExam`。`v*` 标签触发的 workflow 走 `softprops/action-gh-release` 发布到**当前仓库**，但 electron-builder 自身配置的发布目标仍是上游，推送标签前需确认。
- **上游自动更新通道**：`electron-updater` 指向 `lmk1010/OpenExam` 的 Release，本仓库若发布桌面端需重配。
- **题库版权**：肖1000、漫漫学 1500 均为商业出版物，题面内容不随 GPL 授权。

## 维护：修改 GitHub 仓库描述

仓库简介（GitHub 页面右上角的 About 描述）需要单独同步，README 改完不会自动更新。用 `gh` 直接改：

```bash
# 注意：必须显式 -R <owner>/<repo>
# 本仓库同时配置了 upstream 远程，gh 在此目录会默认解析成上游
# lmk1010/OpenExam 而非本仓库，省略 -R 会改错仓库。
gh repo edit chenyingxinghen/kaoyan_shuati \
  --description "考研政治题库 + Android 刷题应用，OpenExam (GPL-3.0) 衍生仓库"

# 验证
gh repo view chenyingxinghen/kaoyan_shuati --json description
```

## 提交基线

- 上游基线：`upstream/main` @ `f01e834`（2026-08-10）
- 本仓库领先上游 17 个提交，改动集中在 `data/`（4602 个文件）、`AndroidAPP/`（54）、`scripts/`（4）；`src/`、`main.js`、`preload.js`、`package.json` 相对上游无改动。

## License

本仓库代码为上游 OpenExam 的衍生作品，沿用 **GPL-3.0-or-later**：

- 源代码继承自上游 OpenExam，版权归原作者 lmk1010 所有，本仓库修改部分同样按 GPL v3+ 开放。
- 题库数据、PDF 原件、题面图片、网页抓取内容**不**归入 GPL 授权范围，遵循各自来源与许可说明。
- 商业出版物题面（肖1000、漫漫学 1500 等）仅供本地个人学习使用，不得再分发。

详见 [`LICENSE`](LICENSE) 与 [`THIRD_PARTY.md`](THIRD_PARTY.md)。
