package com.example.kaoshishuati.data

import android.content.Context

/**
 * 练习进度：用 SharedPreferences 记住每套卷刷到的题目下标，
 * 中途退出后再进入该卷时能续做，而不是重新从第 1 题开始。
 */
class ProgressStore(context: Context) {
    private val sp = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    /** 该卷/该专项刷题作用域上次刷到的题目下标（0 起），没刷过返回 0。 */
    fun resumeIndex(key: String): Int = sp.getInt("idx:$key", 0)

    /** 是否存在进行中的续做记录（做过但未完成）。 */
    fun hasResume(key: String): Boolean = sp.contains("idx:$key")

    fun save(key: String, index: Int) {
        sp.edit().putInt("idx:$key", index).apply()
    }

    /** 做完/重练一遍后清除，下次进入从第 1 题开始。 */
    fun clear(key: String) {
        sp.edit().remove("idx:$key").apply()
    }
}
