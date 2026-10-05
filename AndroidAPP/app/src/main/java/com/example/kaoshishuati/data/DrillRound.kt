package com.example.kaoshishuati.data

import java.util.UUID

/** 一轮题序固定；续刷不再按已刷/错题集合过滤，避免答题后题池缩小导致跳题。 */
data class DrillRound(
    val id: String,
    val mode: DrillMode,
    val questionIds: List<String>,
    val index: Int = 0,
    val correct: Int = 0,
    val singleSelection: String? = null,
    val multiSelection: Set<String> = emptySet(),
    val revealed: Boolean = false,
    val finished: Boolean = false,
) {
    fun isCorrect(question: Question): Boolean =
        (if (question.isMultiple) multiSelection.sorted().joinToString("") else singleSelection) == question.answer

    fun selectSingle(question: Question, key: String): DrillRound {
        if (finished || revealed || question.isMultiple || question.id != questionIds[index] ||
            question.options.none { it.key == key }) return this
        return copy(singleSelection = key, revealed = true, correct = correct + if (key == question.answer) 1 else 0)
    }

    fun toggleMulti(question: Question, key: String): DrillRound {
        if (finished || revealed || !question.isMultiple || question.id != questionIds[index] ||
            question.options.none { it.key == key }) return this
        return copy(multiSelection = if (key in multiSelection) multiSelection - key else multiSelection + key)
    }

    fun submitMulti(question: Question): DrillRound {
        if (finished || revealed || !question.isMultiple || question.id != questionIds[index] || multiSelection.isEmpty()) return this
        return copy(revealed = true, correct = correct + if (isCorrect(question)) 1 else 0)
    }

    fun advance(): DrillRound {
        if (finished || !revealed) return this
        return if (index == questionIds.lastIndex) copy(finished = true)
        else copy(index = index + 1, singleSelection = null, multiSelection = emptySet(), revealed = false)
    }

    /** 题库升级删题时不偷偷换题或移动下标，由界面提示开始新一轮。 */
    fun resolve(questions: List<Question>): List<Question>? {
        val byId = questions.associateBy { it.id }
        return questionIds.map { byId[it] ?: return null }
    }

    companion object {
        fun create(mode: DrillMode, questions: List<Question>): DrillRound {
            require(questions.isNotEmpty())
            return DrillRound(UUID.randomUUID().toString(), mode, questions.map { it.id })
        }

        fun scopeKey(mode: DrillMode, sources: Set<QuestionSource>, courses: Set<Course>, newOnly: Boolean): String =
            listOf("round", mode.name, if (newOnly && mode != DrillMode.WRONG) "new" else "all",
                sources.map { it.name }.sorted().joinToString(","),
                courses.map { it.name }.sorted().joinToString(",")).joinToString("|")
    }
}
