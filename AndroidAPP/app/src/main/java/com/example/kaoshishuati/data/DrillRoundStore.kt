package com.example.kaoshishuati.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 每个模式+范围独立保存一轮。交互时落盘，不依赖 onStop/onDestroy 被调用。 */
class DrillRoundStore(context: Context) {
    private val sp = context.getSharedPreferences("drill_rounds", Context.MODE_PRIVATE)

    fun load(key: String): DrillRound? = try {
        sp.getString(key, null)?.let { decode(it) }
    } catch (_: Exception) {
        null
    }

    // 确认持久化成功才推进界面；失败时保留当前题，允许用户重试。
    fun save(key: String, round: DrillRound): Boolean = try {
        if (round.finished) clear(key)
        else sp.edit().putString(key, encode(round)).commit()
    } catch (_: Exception) {
        false
    }

    fun clear(key: String): Boolean = try {
        sp.edit().remove(key).commit()
    } catch (_: Exception) {
        false
    }

    companion object {
        internal fun encode(round: DrillRound): String = JSONObject().apply {
            put("version", 1)
            put("id", round.id)
            put("mode", round.mode.name)
            put("questions", JSONArray(round.questionIds))
            put("index", round.index)
            put("correct", round.correct)
            put("single", round.singleSelection ?: JSONObject.NULL)
            put("multi", JSONArray(round.multiSelection.sorted()))
            put("revealed", round.revealed)
        }.toString()

        private fun decode(raw: String): DrillRound {
            val obj = JSONObject(raw)
            require(obj.getInt("version") == 1)
            val ids = obj.getJSONArray("questions").let { array ->
                List(array.length()) { array.getString(it) }
            }
            val round = DrillRound(
                id = obj.getString("id"), mode = DrillMode.valueOf(obj.getString("mode")), questionIds = ids,
                index = obj.getInt("index"), correct = obj.getInt("correct"),
                singleSelection = if (obj.isNull("single")) null else obj.getString("single"),
                multiSelection = obj.getJSONArray("multi").let { array ->
                    List(array.length()) { array.getString(it) }.toSet()
                },
                revealed = obj.getBoolean("revealed"),
            )
            require(round.id.isNotBlank() && ids.isNotEmpty() && ids.all { it.isNotBlank() })
            require(ids.distinct().size == ids.size && round.index in ids.indices)
            require(round.correct in 0..(round.index + if (round.revealed) 1 else 0))
            return round
        }
    }
}
