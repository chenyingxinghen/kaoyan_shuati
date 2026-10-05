package com.example.kaoshishuati

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kaoshishuati.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrillRoundStoreTest {
    private lateinit var context: Context
    private val single = Question("q1", "p", 1, "single", null, "单选", listOf(Option("A", "甲"), Option("B", "乙")), "A", "解析")
    private val multi = Question("q2", "p", 2, "multiple", null, "多选", listOf(Option("A", "甲"), Option("B", "乙")), "AB", "解析")
    private val key = "test-round"

    @Before fun setUp() {
        context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("round-store-test-$name", mode)
        }
        listOf("drill_rounds", "done", "wrong").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun roundTripRestoresEveryFieldInAllThreeModes() {
        for (mode in DrillMode.values()) {
            val original = DrillRound.create(mode, listOf(single, multi))
                .selectSingle(single, "A").advance().toggleMulti(multi, "B")
            assertTrue(DrillRoundStore(context).save(mode.name, original))
            assertEquals(original, DrillRoundStore(context).load(mode.name))
        }
    }

    @Test fun everyAnswerAndAdvanceIsRecoverableWithoutAnExitCallback() {
        var round = DrillRound.create(DrillMode.SHUFFLED, listOf(single, multi))
        val states = listOf(round, round.selectSingle(single, "A").also { round = it },
            round.advance().also { round = it }, round.toggleMulti(multi, "A").also { round = it },
            round.toggleMulti(multi, "B").submitMulti(multi))
        states.forEach {
            assertTrue(DrillRoundStore(context).save(key, it))
            assertEquals(it, DrillRoundStore(context).load(key))
        }
        val restored = DrillRoundStore(context).load(key)!!
        assertEquals(2, restored.correct)
        assertEquals(restored, restored.submitMulti(multi))
    }

    @Test fun completedRoundIsRemovedButOtherModeIsPreserved() {
        val other = DrillRound.create(DrillMode.WRONG, listOf(multi))
        assertTrue(DrillRoundStore(context).save("other", other))
        val answered = DrillRound.create(DrillMode.SEQUENTIAL, listOf(single)).selectSingle(single, "A")
        assertTrue(DrillRoundStore(context).save(key, answered))
        assertNotNull(DrillRoundStore(context).load(key))
        assertTrue(DrillRoundStore(context).save(key, answered.advance()))
        assertNull(DrillRoundStore(context).load(key))
        assertEquals(other, DrillRoundStore(context).load("other"))
    }

    @Test fun doneAndWrongMembershipChangesDoNotShrinkSavedQuestionOrder() {
        val original = DrillRound.create(DrillMode.WRONG, listOf(single, multi)).selectSingle(single, "A")
        assertTrue(DrillRoundStore(context).save(key, original))
        DoneStore(context).markDone(single.paperId, single.id)
        WrongStore(context).add(single.id)
        WrongStore(context).remove(single.id)
        val restored = DrillRoundStore(context).load(key)!!
        assertEquals(listOf(single, multi), restored.resolve(listOf(multi, single)))
        assertEquals(0, restored.index)
        assertTrue(restored.revealed)
    }

    @Test fun replacingRoundKeepsDoneRecordsAndOtherScopes() {
        val old = DrillRound.create(DrillMode.SHUFFLED, listOf(single)).selectSingle(single, "A")
        val fresh = DrillRound.create(DrillMode.SHUFFLED, listOf(multi))
        val store = DrillRoundStore(context)
        store.save(key, old)
        store.save("other", old)
        DoneStore(context).markDone(single.paperId, single.id)
        assertTrue(store.save(key, fresh))
        assertEquals(fresh, DrillRoundStore(context).load(key))
        assertEquals(old, DrillRoundStore(context).load("other"))
        assertTrue(DoneStore(context).isDone(single.id))
    }

    @Test fun invalidAndUnknownVersionDataIsIgnoredSafely() {
        val prefs = context.getSharedPreferences("drill_rounds", Context.MODE_PRIVATE)
        val valid = DrillRoundStore.encode(DrillRound.create(DrillMode.SHUFFLED, listOf(single)))
        for (raw in listOf("not-json", valid.replace("\"version\":1", "\"version\":999"),
            valid.replace("\"index\":0", "\"index\":-1"), valid.replace("\"index\":0", "\"index\":50"))) {
            prefs.edit().putString(key, raw).commit()
            assertNull(DrillRoundStore(context).load(key))
        }
    }
}
