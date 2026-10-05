package com.example.kaoshishuati.data

import android.content.Context

/** 错题本：用 SharedPreferences 持久化错题 id 集合。 */
class WrongStore(context: Context) {
    private val sp = context.getSharedPreferences("wrong", Context.MODE_PRIVATE)

    fun ids(): Set<String> = sp.getStringSet("ids", emptySet()) ?: emptySet()

    fun add(qid: String) {
        val cur = ids().toMutableSet(); cur.add(qid)
        sp.edit().putStringSet("ids", cur).apply()
    }

    /** 答对(视为已掌握)后移出错题本。 */
    fun remove(qid: String) {
        val cur = ids().toMutableSet()
        if (cur.remove(qid)) sp.edit().putStringSet("ids", cur).apply()
    }

    fun clear() = sp.edit().remove("ids").apply()
}
