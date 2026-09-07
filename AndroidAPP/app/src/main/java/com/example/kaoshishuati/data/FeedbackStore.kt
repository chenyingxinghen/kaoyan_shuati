package com.example.kaoshishuati.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一道题的一条问题反馈（用户刷题时点「反馈」上报）。
 * 只记录定位所需的最小字段，便于开发者据此在种子库中做点对点修复：
 *  qid + 来源卷(papers 行) + 问题类型 + 用户备注。不含题干/作答快照。
 */
data class FeedbackEntry(
    val qid: String,          // questions.id —— 种子库全局稳定主键
    val paperId: String,      // papers.id
    val paperTitle: String,   // 来源卷标题（人读方便）
    val paperYear: Int,       // 来源卷年份
    val type: String,         // FeedbackTypes 中的 code
    val typeLabel: String,    // 类型中文标签（直接入报告，省得还原）
    val note: String,         // 用户补充说明（可空串）
    val createdAt: Long,      // epoch millis
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("qid", qid)
        put("paperId", paperId)
        put("paperTitle", paperTitle)
        put("paperYear", paperYear)
        put("type", type)
        put("typeLabel", typeLabel)
        put("note", note)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = FeedbackEntry(
            o.optString("qid"),
            o.optString("paperId"),
            o.optString("paperTitle"),
            o.optInt("paperYear"),
            o.optString("type"),
            o.optString("typeLabel"),
            o.optString("note"),
            o.optLong("createdAt"),
        )
    }
}

/** 预置的问题类型（code -> 中文标签）。 */
object FeedbackTypes {
    const val ANSWER = "answer"
    const val ANALYSIS = "analysis"
    const val STEM = "stem"
    const val OPTION = "option"
    const val OTHER = "other"

    /** 按展示顺序排列。 */
    val ALL: List<Pair<String, String>> = listOf(
        ANSWER to "参考答案有误",
        ANALYSIS to "解析不清 / 有误",
        STEM to "题干错漏 / 表述不清",
        OPTION to "选项 / 配图有误",
        OTHER to "其他问题",
    )

    fun label(code: String): String =
        ALL.firstOrNull { it.first == code }?.second ?: "其他问题"
}

/**
 * 问题反馈本地存储：SharedPreferences 存一条 JSON 数组，
 * 与 WrongStore / ProgressStore 保持一致的文件内惯用法。
 */
class FeedbackStore(context: Context) {
    private val sp = context.getSharedPreferences("feedback", Context.MODE_PRIVATE)

    fun entries(): List<FeedbackEntry> {
        val raw = sp.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { FeedbackEntry.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun count(): Int = entries().size

    /** 是否已对该题反馈过（用于刷题页展示「已反馈」态）。 */
    fun has(qid: String): Boolean = entries().any { it.qid == qid }

    fun add(e: FeedbackEntry) {
        val arr = JSONArray()
        entries().forEach { arr.put(it.toJson()) }
        arr.put(e.toJson())
        sp.edit().putString(KEY, arr.toString()).apply()
    }

    fun clear() = sp.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "entries"
    }
}
