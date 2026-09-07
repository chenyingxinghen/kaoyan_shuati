package com.example.kaoshishuati.data

import android.content.Context

/**
 * 「已刷」题目进度：用户在任一入口（首页单卷练习、专项刷题）作答过的题目，按全局题目 id 去重记录。
 * 每道题只属于某一卷（questions.paper_id），因此题目实际作答后即把该卷的已刷计数 +1。
 *
 * 这是首页自选题册与专项刷题**共享**的同一份进度：在专项刷题里做过的题，回到首页对应卷能看到已刷计数推进，
 * 反之亦然。错题另由 [WrongStore] 单独记录（答错只增不清），这里只关心「做过/已刷」。
 */
class DoneStore(context: Context) {
    private val sp = context.getSharedPreferences("done", Context.MODE_PRIVATE)

    /** 该题是否已作答过（已刷）。 */
    fun isDone(qid: String): Boolean = sp.getStringSet(KEY_QIDS, emptySet()).orEmpty().contains(qid)

    /** 全部已刷题 id 集合（一次性读出，供批量过滤用，避免每题都读盘）。 */
    fun doneIds(): Set<String> = sp.getStringSet(KEY_QIDS, emptySet()).orEmpty()

    /** 全题库累计已刷题数（去重后）。 */
    fun totalDone(): Int = sp.getStringSet(KEY_QIDS, emptySet()).orEmpty().size

    /** 某卷已刷题数。 */
    fun doneCount(paperId: String): Int = sp.getInt(paperKey(paperId), 0)

    /** 作答过即调用：若该题首次已刷，则累加所属卷的已刷计数（去重，重复作答不重复计数）。 */
    fun markDone(paperId: String, qid: String) {
        val qids = sp.getStringSet(KEY_QIDS, emptySet()).orEmpty().toMutableSet()
        if (qids.add(qid)) {
            sp.edit()
                .putStringSet(KEY_QIDS, qids)
                .putInt(paperKey(paperId), sp.getInt(paperKey(paperId), 0) + 1)
                .apply()
        }
    }

    private fun paperKey(paperId: String): String = "paper:$paperId"

    companion object {
        private const val KEY_QIDS = "qids"
    }
}
