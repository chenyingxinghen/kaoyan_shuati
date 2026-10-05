package com.example.kaoshishuati

import com.example.kaoshishuati.data.Course
import com.example.kaoshishuati.data.DrillMode
import com.example.kaoshishuati.data.DrillRound
import com.example.kaoshishuati.data.Option
import com.example.kaoshishuati.data.Question
import com.example.kaoshishuati.data.QuestionSource
import org.junit.Assert.*
import org.junit.Test

class DrillRoundTest {
    private val single = question("q1", "A")
    private val multiple = question("q2", "AC")
    private val questions = listOf(single, multiple)

    private fun question(id: String, answer: String) = Question(
        id, "paper", 1, if (answer.length > 1) "multiple" else "single", "马原", id,
        listOf("A", "B", "C", "D").map { Option(it, it) }, answer, "解析",
    )

    private fun round(mode: DrillMode = DrillMode.SHUFFLED) = DrillRound.create(mode, questions)

    @Test fun scopeSeparatesModesRangesAndNewOnly() {
        val modes = DrillMode.values().map { DrillRound.scopeKey(it, emptySet(), emptySet(), false) }
        assertEquals(3, modes.toSet().size)
        assertNotEquals(modes[0], DrillRound.scopeKey(DrillMode.SEQUENTIAL, emptySet(), emptySet(), true))
        assertNotEquals(modes[0], DrillRound.scopeKey(DrillMode.SEQUENTIAL, setOf(QuestionSource.PAST), emptySet(), false))
        assertNotEquals(modes[0], DrillRound.scopeKey(DrillMode.SEQUENTIAL, emptySet(), setOf(Course.MAYUAN), false))
        assertEquals(modes[2], DrillRound.scopeKey(DrillMode.WRONG, emptySet(), emptySet(), true))
    }

    @Test fun scopeIgnoresToggleOrder() {
        assertEquals(
            DrillRound.scopeKey(DrillMode.SHUFFLED, linkedSetOf(QuestionSource.PAST, QuestionSource.XIAO1000), linkedSetOf(Course.MAYUAN, Course.XINSI), true),
            DrillRound.scopeKey(DrillMode.SHUFFLED, linkedSetOf(QuestionSource.XIAO1000, QuestionSource.PAST), linkedSetOf(Course.XINSI, Course.MAYUAN), true),
        )
    }

    @Test fun singleAnswerRetainsSelectionAndCannotScoreTwice() {
        val answered = round().selectSingle(single, "A")
        assertTrue(answered.revealed)
        assertEquals("A", answered.singleSelection)
        assertEquals(1, answered.correct)
        assertEquals(answered, answered.selectSingle(single, "A"))
        assertEquals(answered, answered.selectSingle(single, "B"))
    }

    @Test fun unansweredQuestionCannotAdvance() {
        val fresh = round()
        assertEquals(fresh, fresh.advance())
        assertEquals(fresh, fresh.selectSingle(single, "E"))
        assertEquals(fresh, fresh.selectSingle(question("other", "A"), "A"))
    }

    @Test fun partialMultiSelectionDoesNotSubmitOrAdvance() {
        val fresh = DrillRound.create(DrillMode.WRONG, listOf(multiple))
        assertEquals(fresh, fresh.submitMulti(multiple))
        val partial = fresh.toggleMulti(multiple, "C")
        assertEquals(setOf("C"), partial.multiSelection)
        assertFalse(partial.revealed)
        assertEquals(partial, partial.advance())
        assertEquals(fresh, partial.toggleMulti(multiple, "C"))
    }

    @Test fun submittedMultiAnswerCannotScoreTwice() {
        val submitted = DrillRound.create(DrillMode.WRONG, listOf(multiple))
            .toggleMulti(multiple, "C").toggleMulti(multiple, "A").submitMulti(multiple)
        assertEquals(1, submitted.correct)
        assertTrue(submitted.revealed)
        assertEquals(submitted, submitted.submitMulti(multiple))
        assertEquals(submitted, submitted.toggleMulti(multiple, "B"))
    }

    @Test fun incorrectAnswerIsRetainedWithoutIncrementingScore() {
        val answered = round().selectSingle(single, "B")
        assertTrue(answered.revealed)
        assertEquals(0, answered.correct)
        assertFalse(answered.isCorrect(single))
    }

    @Test fun advancingPreservesOrderAndScoreButClearsCurrentAnswer() {
        val original = round()
        val next = original.selectSingle(single, "A").advance()
        assertEquals(original.questionIds, next.questionIds)
        assertEquals(original.id, next.id)
        assertEquals(1, next.index)
        assertEquals(1, next.correct)
        assertNull(next.singleSelection)
        assertTrue(next.multiSelection.isEmpty())
        assertFalse(next.revealed)
    }

    @Test fun finalAnswerRemainsResumableUntilFinishIsPressed() {
        for (mode in DrillMode.values()) {
            val answered = DrillRound.create(mode, listOf(single)).selectSingle(single, "A")
            assertFalse(answered.finished)
            assertTrue(answered.advance().finished)
            assertEquals(answered.advance(), answered.advance().advance())
        }
    }

    @Test fun resolvingKeepsSavedOrderInsteadOfDatabaseOrder() {
        val saved = DrillRound.create(DrillMode.SHUFFLED, listOf(multiple, single)).copy(index = 1)
        assertEquals(listOf(multiple, single), saved.resolve(questions))
        assertEquals(1, saved.index)
    }

    @Test fun missingSavedQuestionDoesNotSilentlyShiftPosition() {
        assertNull(round().resolve(listOf(multiple)))
    }

    @Test fun startingNewRoundResetsStateAndUsesNewIdentity() {
        val old = round().selectSingle(single, "A").advance()
        val fresh = round()
        assertNotEquals(old.id, fresh.id)
        assertEquals(0, fresh.index)
        assertEquals(0, fresh.correct)
        assertFalse(fresh.revealed)
        assertFalse(fresh.finished)
    }
}
