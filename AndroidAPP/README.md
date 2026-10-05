# AndroidAPP — 考研政治刷题（Compose）

目录：`AndroidAPP/`（Kotlin + Jetpack Compose，包 `com.example.kaoshishuati`）
一期范围：核心刷题 MVP，仅接入考研政治种子。

## 已实现
- `data/BankDb.kt`：首启把 `assets/openexam_kaoyan.db` 拷到 files 目录并**只读**打开 SQLite；提供 `papers()` / `questions(paperId)` / `questionById(id)`，含跨卷取题与 asset 逐字节自同步。
- `data/Models.kt`：`Paper` / `Question` / `Option`。
- `data/BankScope.kt`：题库作用域枚举，区分真题卷与习题册。
- `data/ProgressStore.kt` / `DoneStore.kt`：答题进度与「已刷」标记，跨卷共享。
- `data/WrongStore.kt`：错题本（SharedPreferences 持久化错题 id 集合）。
- `data/DrillRound.kt` / `DrillRoundStore.kt` / `DrillSettingsStore.kt`：专项练习轮次与设置记忆，支持断点续做。
- `data/FeedbackStore.kt` / `FeedbackReport.kt`：题面点修反馈与导出。
- `ui/MainScreen.kt` + `MainActivity.kt`：
  - 题库列表：真题/习题册分册聚合，可折叠分组，分层返回栈带返回记忆，附题数与已刷标记；错题本与专项练习入口。
  - 逐题练习：单选点选即判、多选勾选后"提交"判分；作答后显示对/错、正确答案与解析；进度条；完成页给得分。
  - 错题本：列出错题（题干/选项/答案/解析）。
- 数据资产：`app/src/main/assets/openexam_kaoyan.db`（由政治种子库解压生成，~5.5 MB）。

## 数据来源
`data/kaoyan/openexam.kaoyan-politics.seed.db.gz`（仓库根的 `data/kaoyan/`，**25 卷 / 3322 题**，subject=kaoyan）。
真题 15 卷（2010–2024）+ 肖1000（2026）5 册 + 漫漫学1500（2027）5 册；单选 1583 / 多选 1739。
如需换/增题库：把新的 `.db` 放 `app/src/main/assets/` 并同步改 `BankDb.ASSET_NAME`。
换库后建议跑 `python scripts/audit_kaoyan_politics.py` 与 `scripts/test_kaoyan_feedback.py`。

## 构建
本环境访问 mavenCentral/plugins.gradle.org 被 403 阻断；已把依赖解析改为**优先走阿里云镜像**（`settings.gradle.kts` 加了 aliyun google/public/gradle-plugin，末尾仍保留 google()/mavenCentral() 兜底）。
在可联网机器可直接：

```bash
cd AndroidAPP
./gradlew :app:assembleDebug        # 或 Android Studio: Open 后 Run ▶
```
**已实测通过**：`assembleDebug` BUILD SUCCESSFUL，产物
`app/build/outputs/apk/debug/app-debug.apk`（约 10.8 MB），Compose/Kotlin 编译零错误。

## 备注
- 已通过 `assembleDebug` 编译（Compose BOM 2024.09 / material3 / AGP 8.11.2 / Kotlin 2.0.21）。
- 政治题仅为单选/多选；代码已兼容 judge 兜底，可扩展接入考公行测等多科目种子。
