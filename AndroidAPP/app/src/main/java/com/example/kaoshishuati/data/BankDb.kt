package com.example.kaoshishuati.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.File

/**
 * 读取打包进 assets 的考研政治种子库（SQLite）。
 * 首启把 assets/openexam_kaoyan.db 拷到 files 目录后以只读方式打开。
 */
class BankDb(private val context: Context) {

    private val dbFile: File =
        File(context.filesDir, "db/openexam_kaoyan.db")

    private var db: SQLiteDatabase? = null

    fun open(): SQLiteDatabase {
        db?.let { if (it.isOpen) return it }
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(ASSET_NAME).use { input ->
                dbFile.outputStream().use { out -> input.copyTo(out) }
            }
        }
        val d = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        db = d
        return d
    }

    fun close() {
        db?.close(); db = null
    }

    fun papers(): List<Paper> {
        val d = open()
        val out = ArrayList<Paper>()
        d.rawQuery(
            "SELECT id,title,year,subject,question_count,difficulty FROM papers ORDER BY " +
                "(title LIKE '%真题%') DESC, year DESC, title ASC", null
        ).use { c ->
            val iId = c.getColumnIndexOrThrow("id"); val iT = c.getColumnIndexOrThrow("title")
            val iY = c.getColumnIndexOrThrow("year"); val iS = c.getColumnIndexOrThrow("subject")
            val iQ = c.getColumnIndexOrThrow("question_count"); val iD = c.getColumnIndexOrThrow("difficulty")
            while (c.moveToNext()) {
                out.add(
                    Paper(c.getString(iId), c.getString(iT), c.getInt(iY), c.getString(iS),
                        c.getInt(iQ), c.getInt(iD))
                )
            }
        }
        return out
    }

    fun questions(paperId: String): List<Question> {
        val d = open()
        val out = ArrayList<Question>()
        d.rawQuery(
            "SELECT id,paper_id,order_num,type,category,content,options,answer,analysis " +
                "FROM questions WHERE paper_id=? ORDER BY order_num ASC", arrayOf(paperId)
        ).use { c ->
            val iId = c.getColumnIndexOrThrow("id"); val iP = c.getColumnIndexOrThrow("paper_id")
            val iO = c.getColumnIndexOrThrow("order_num"); val iTy = c.getColumnIndexOrThrow("type")
            val iCa = c.getColumnIndexOrThrow("category"); val iC = c.getColumnIndexOrThrow("content")
            val iOp = c.getColumnIndexOrThrow("options"); val iA = c.getColumnIndexOrThrow("answer")
            val iAn = c.getColumnIndexOrThrow("analysis")
            while (c.moveToNext()) {
                out.add(
                    Question(c.getString(iId), c.getString(iP), c.getInt(iO), c.getString(iTy),
                        c.getString(iCa), c.getString(iC), parseOptions(c.getString(iOp)),
                        c.getString(iA).trim().uppercase(), c.getString(iAn) ?: "")
                )
            }
        }
        return out
    }

    fun questionById(qid: String): Question? {
        val d = open()
        d.rawQuery(
            "SELECT id,paper_id,order_num,type,category,content,options,answer,analysis " +
                "FROM questions WHERE id=?", arrayOf(qid)
        ).use { c ->
            if (!c.moveToFirst()) return null
            return Question(c.getString(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("paper_id")),
                c.getInt(c.getColumnIndexOrThrow("order_num")),
                c.getString(c.getColumnIndexOrThrow("type")),
                c.getString(c.getColumnIndexOrThrow("category")),
                c.getString(c.getColumnIndexOrThrow("content")),
                parseOptions(c.getString(c.getColumnIndexOrThrow("options"))),
                c.getString(c.getColumnIndexOrThrow("answer")).trim().uppercase(),
                c.getString(c.getColumnIndexOrThrow("analysis")) ?: "")
        }
    }

    private fun parseOptions(json: String): List<Option> {
        val out = ArrayList<Option>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Option(o.optString("key", ""), o.optString("content", "")))
            }
        } catch (_: Exception) { }
        return out
    }

    companion object {
        private const val ASSET_NAME = "openexam_kaoyan.db"
    }
}
