# AndroidAPP — 考研政治刷题 MVP（Compose）

目录：`AndroidAPP/`（Kotlin + Jetpack Compose，包 `com.example.kaoshishuati`）
一期范围（用户确认）：核心刷题 MVP，仅接入考研政治种子。

## 已实现
- `data/BankDb.kt`：首启把 `assets/openexam_kaoyan.db` 拷到 files 目录并**只读**打开 SQLite；提供 `papers()` / `questions(paperId)` / `questionById(id)`。
- `data/Models.kt`：`Paper` / `Question` / `Option`。
- `data/WrongStore.kt`：错题本（SharedPreferences 持久化错题 id 集合）。
- `ui/MainScreen.kt` + `MainActivity.kt`：
  - 题库列表（按题型/年份分组列出 22 卷，附题数）→ 错题本入口。
  - 逐题练习：单选点选即判、多选勾选后“提交”判分；作答后显示对/错、正确答案与解析；进度条；完成页给得分。
  - 错题本：列出错题（题干/选项/答案/解析）。
- 数据资产：`app/src/main/assets/openexam_kaoyan.db`（由政治种子库解压生成，~3 MB）。

## 数据来源
`data/kaoyan/openexam.kaoyan-politics.seed.db.gz`（上一阶段构建，22 卷 / 1687 题，subject=kaoyan）。
如需换/增题库：把新的 `.db` 放 `app/src/main/assets/` 并同步改 `BankDb.ASSET_NAME`。

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
