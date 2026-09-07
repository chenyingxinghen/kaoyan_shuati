package com.example.kaoshishuati.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 把本地反馈汇总导出为一份 JSON 报告并走系统分享面板发出。
 *
 * 报告 JSON 是一份面向「开发者点对点修复 → 终极题库维护」的稳定格式：
 * 顶层固定 schema 号，reports 按 qid 聚合排序，便于脚本比对/去重。
 */
object FeedbackReport {

    /** 格式版本号；改动字段语义时递增，供下游消费脚本判断兼容性。 */
    const val SCHEMA = "kaoyan-question-feedback/1"

    fun build(entries: List<FeedbackEntry>): JSONObject {
        val sorted = entries.sortedWith(compareBy({ it.qid }, { it.createdAt }))
        val reports = JSONArray()
        sorted.forEach { reports.put(it.toJson()) }
        return JSONObject().apply {
            put("app", "考研刷题")
            put("schema", SCHEMA)
            put("exportedAt", System.currentTimeMillis())
            put("count", sorted.size)
            put("uniqueQuestions", sorted.map { it.qid }.distinct().size)
            put("reports", reports)
        }
    }

    /** 写报告到 cache/feedback/question_feedback.json，返回文件。 */
    fun write(context: Context, store: FeedbackStore): File {
        val dir = File(context.cacheDir, "feedback").apply { mkdirs() }
        val f = File(dir, FILE_NAME)
        f.writeText(build(store.entries()).toString(2))
        return f
    }

    /** 通过系统分享面板把报告发出去（发邮箱/存到磁盘等）。 */
    fun share(context: Context, store: FeedbackStore) {
        val file = write(context, store)
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("feedback", uri)
        }
        context.startActivity(Intent.createChooser(send, "导出题库问题反馈"))
    }

    private const val FILE_NAME = "question_feedback.json"
}
