package com.example.kaoshishuati.data

import android.content.Context

/**
 * 专项刷题的上次勾选设置（题库来源/学科/刷题模式/只刷新题），
 * 用 SharedPreferences 持久化：下次进入专项刷题时还原上次的选择，
 * 免得每次都重新勾。值用 "|" 连接的名称串，与 UI 的可保存状态同构。
 */
class DrillSettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("drill_settings", Context.MODE_PRIVATE)

    data class Settings(
        val sources: Set<QuestionSource>,
        val courses: Set<Course>,
        val mode: DrillMode,
        val newOnly: Boolean,
    ) {
        companion object {
            val DEFAULT = Settings(emptySet(), emptySet(), DrillMode.SEQUENTIAL, false)
        }
    }

    fun load(): Settings {
        val src = sp.getString("src", "")!!.split("|").filter { it.isNotBlank() }
            .mapNotNull { n -> QuestionSource.values().find { it.name == n } }.toSet()
        val cou = sp.getString("cou", "")!!.split("|").filter { it.isNotBlank() }
            .mapNotNull { n -> Course.values().find { it.name == n } }.toSet()
        val mode = DrillMode.values().find { it.name == sp.getString("mode", null) } ?: DrillMode.SEQUENTIAL
        return Settings(src, cou, mode, sp.getBoolean("newOnly", false))
    }

    fun save(sources: Set<QuestionSource>, courses: Set<Course>, mode: DrillMode, newOnly: Boolean) {
        sp.edit()
            .putString("src", sources.joinToString("|") { it.name })
            .putString("cou", courses.joinToString("|") { it.name })
            .putString("mode", mode.name)
            .putBoolean("newOnly", newOnly)
            .apply()
    }
}
