package com.example.kaoshishuati.data

/** 试卷（种子库 papers 表的一行） */
data class Paper(
    val id: String,
    val title: String,
    val year: Int,
    val subject: String,
    val questionCount: Int,
    val difficulty: Int,
)

/** 选项 */
data class Option(val key: String, val content: String)

/** 题目（questions 表的一行） */
data class Question(
    val id: String,
    val paperId: String,
    val order: Int,
    val type: String,        // single / multiple / judge
    val category: String?,
    val content: String,
    val options: List<Option>,
    val answer: String,      // 如 "A" / "AC"
    val analysis: String,
) {
    val isMultiple: Boolean get() = type == "multiple"
}
