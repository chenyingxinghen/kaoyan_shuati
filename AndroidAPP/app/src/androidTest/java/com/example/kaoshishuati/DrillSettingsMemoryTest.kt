package com.example.kaoshishuati

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kaoshishuati.data.BankDb
import com.example.kaoshishuati.data.Course
import com.example.kaoshishuati.data.DoneStore
import com.example.kaoshishuati.data.DrillMode
import com.example.kaoshishuati.data.DrillRoundStore
import com.example.kaoshishuati.data.DrillRound
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.kaoshishuati.data.DrillSettingsStore
import com.example.kaoshishuati.data.FeedbackStore
import com.example.kaoshishuati.data.ProgressStore
import com.example.kaoshishuati.data.QuestionSource
import com.example.kaoshishuati.data.WrongStore
import com.example.kaoshishuati.ui.App
import com.example.kaoshishuati.ui.theme.KaoshishuatiTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrillSettingsMemoryTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var bank: BankDb
    private lateinit var restoration: StateRestorationTester
    private lateinit var rounds: DrillRoundStore

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 只借用主包题库资源；偏好和本地库写在测试包，避免触碰用户数据。
        context = object : ContextWrapper(instrumentation.context) {
            override fun getAssets() = instrumentation.targetContext.assets
        }
        context.getSharedPreferences("drill_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("drill_rounds", Context.MODE_PRIVATE).edit().clear().commit()
        bank = BankDb(context)
        val wrong = WrongStore(context)
        val progress = ProgressStore(context)
        val done = DoneStore(context)
        val feedback = FeedbackStore(context)
        val settings = DrillSettingsStore(context)
        rounds = DrillRoundStore(context)
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            KaoshishuatiTheme {
                App(bank, wrong, progress, done, feedback, settings, rounds)
            }
        }
        openDrills()
    }

    @After
    fun tearDown() {
        compose.waitForIdle()
        bank.close()
    }

    @Test
    fun eachModeClickPersistsWithoutAnotherSettingsChange() {
        listOf(DrillMode.SHUFFLED, DrillMode.WRONG, DrillMode.SEQUENTIAL).forEach { mode ->
            clickSetting(mode.label)
            assertEquals(mode, saved().mode)
            reopenDrills()
            compose.onNodeWithText(mode.label).assertIsSelected()
        }
    }

    @Test
    fun sourceSelectionAndDeselectionPersistIncludingEmptyScope() {
        val selected = mutableSetOf<QuestionSource>()
        listOf(QuestionSource.PAST, QuestionSource.XIAO1000, QuestionSource.PAST, QuestionSource.XIAO1000)
            .forEach { source ->
                clickSetting(source.label)
                if (!selected.add(source)) selected.remove(source)
                assertEquals(selected, saved().sources)
                reopenDrills()
                QuestionSource.values().forEach {
                    val chip = compose.onNodeWithText(it.label)
                    if (it in selected) chip.assertIsSelected() else chip.assertIsNotSelected()
                }
            }
    }

    @Test
    fun courseSelectionAndDeselectionPersistIncludingEmptyScope() {
        val selected = mutableSetOf<Course>()
        listOf(Course.MAYUAN, Course.XINSI, Course.MAYUAN, Course.XINSI).forEach { course ->
            clickSetting(course.label)
            if (!selected.add(course)) selected.remove(course)
            assertEquals(selected, saved().courses)
            reopenDrills()
            Course.values().forEach {
                val chip = compose.onNodeWithText(it.label)
                if (it in selected) chip.assertIsSelected() else chip.assertIsNotSelected()
            }
        }
    }

    @Test
    fun combinedSettingsSurviveRunReturnAndStateRestoration() {
        clickSetting(QuestionSource.XIAO1000.label)
        clickSetting(Course.MAYUAN.label)
        clickSetting(DrillMode.SHUFFLED.label)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        val expected = DrillSettingsStore.Settings(
            setOf(QuestionSource.XIAO1000), setOf(Course.MAYUAN), DrillMode.SHUFFLED, true,
        )
        assertEquals(expected, saved())

        clickSetting(DrillMode.WRONG.label)
        assertEquals(expected.copy(mode = DrillMode.WRONG), saved())
        clickSetting(DrillMode.SHUFFLED.label)
        compose.onNode(isToggleable()).assertIsOn()

        clickSetting("开始刷题")
        compose.onNodeWithText("返回").performClick()
        waitForDrills()
        assertSelections(expected)
        reopenDrills()
        assertSelections(expected)

        restoration.emulateSavedInstanceStateRestore()
        openDrills()
        assertSelections(expected)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        assertEquals(expected.copy(newOnly = false), saved())
    }

    @Test
    fun shuffledRoundRestoresQuestionAnswerAndScoreAfterRecreation() {
        clickSetting(DrillMode.SHUFFLED.label)
        val questions = bank.questions("xiao1000_2026_mayuan").filter { !it.isMultiple }.take(3).reversed()
        val key = DrillRound.scopeKey(DrillMode.SHUFFLED, emptySet(), emptySet(), false)
        val original = DrillRound.create(DrillMode.SHUFFLED, questions)
        assertTrue(rounds.save(key, original))
        reopenDrills()
        clickSetting("继续上次")
        compose.onNodeWithText("题号 ${questions[0].id}").assertExists()
        val answer = questions[0].options.first { it.key == questions[0].answer }
        compose.onNodeWithText(answer.content).performScrollTo().performClick()
        val answered = DrillRoundStore(context).load(key)!!
        assertTrue(answered.revealed)
        assertEquals(1, answered.correct)

        restoration.emulateSavedInstanceStateRestore()
        openDrills()
        clickSetting("继续上次")
        compose.onNodeWithText("题号 ${questions[0].id}").assertExists()
        assertEquals(answered, DrillRoundStore(context).load(key))
        compose.onNodeWithText("下一题").performClick()
        compose.onNodeWithText("题号 ${questions[1].id}").assertExists()
        assertEquals(original.questionIds, rounds.load(key)!!.questionIds)
        assertEquals(1, rounds.load(key)!!.index)
        assertEquals(1, rounds.load(key)!!.correct)
    }

    @Test
    fun partialMultiSelectionSurvivesRecreation() {
        val q = bank.questions("xiao1000_2026_mayuan").first { it.isMultiple }
        val key = DrillRound.scopeKey(DrillMode.SEQUENTIAL, emptySet(), emptySet(), false)
        assertTrue(rounds.save(key, DrillRound.create(DrillMode.SEQUENTIAL, listOf(q))))
        reopenDrills()
        clickSetting("继续上次")
        compose.onNodeWithText(q.options.first().content).performScrollTo().performClick()
        val partial = rounds.load(key)!!
        assertEquals(setOf(q.options.first().key), partial.multiSelection)
        restoration.emulateSavedInstanceStateRestore()
        openDrills()
        clickSetting("继续上次")
        assertEquals(partial, rounds.load(key))
        compose.onNodeWithText(q.options.first().content).performScrollTo().performClick()
        assertTrue(rounds.load(key)!!.multiSelection.isEmpty())
    }

    @Test
    fun newRoundRequiresConfirmationAndReplacesOnlyCurrentRound() {
        clickSetting(DrillMode.SHUFFLED.label)
        val key = DrillRound.scopeKey(DrillMode.SHUFFLED, emptySet(), emptySet(), false)
        val original = DrillRound.create(DrillMode.SHUFFLED, bank.questions("xiao1000_2026_mayuan").take(2))
        assertTrue(rounds.save(key, original))
        assertTrue(rounds.save("another-scope", original))
        reopenDrills()
        clickSetting("开始新一轮")
        compose.onNodeWithText("取消").performClick()
        assertEquals(original, rounds.load(key))
        clickSetting("开始新一轮")
        compose.onNodeWithText("开始新一轮？").assertExists()
        compose.onNode(hasText("开始新一轮") and hasAnyAncestor(isDialog())).performClick()
        val fresh = rounds.load(key)!!
        assertNotEquals(original.id, fresh.id)
        assertEquals(0, fresh.index)
        assertEquals(0, fresh.correct)
        assertEquals(original, rounds.load("another-scope"))
    }

    @Test
    fun finishingRoundRemovesResumeEntry() {
        val q = bank.questions("xiao1000_2026_mayuan").first { !it.isMultiple }
        val key = DrillRound.scopeKey(DrillMode.SEQUENTIAL, emptySet(), emptySet(), false)
        assertTrue(rounds.save(key, DrillRound.create(DrillMode.SEQUENTIAL, listOf(q)).selectSingle(q, q.answer)))
        reopenDrills()
        clickSetting("继续上次")
        compose.onNodeWithText("完成").performClick()
        assertNull(rounds.load(key))
        compose.onNodeWithText("返回模式设置").performClick()
        waitForDrills()
        compose.onNodeWithText("继续上次").assertDoesNotExist()
        compose.onNodeWithText("开始刷题").assertExists()
    }

    private fun saved() = DrillSettingsStore(context).load()

    private fun clickSetting(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        if (QuestionSource.values().any { it.label == label } || Course.values().any { it.label == label }) {
            waitForDrills()
        }
    }

    private fun openDrills() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("专项刷题"))
        compose.onNodeWithText("专项刷题").performClick()
        waitForDrills()
    }

    private fun waitForDrills() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("刷题模式").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun reopenDrills() {
        compose.onNodeWithText("返回").performClick()
        openDrills()
    }

    private fun assertSelections(expected: DrillSettingsStore.Settings) {
        assertEquals(expected, saved())
        expected.sources.forEach { compose.onNodeWithText(it.label).assertIsSelected() }
        expected.courses.forEach { compose.onNodeWithText(it.label).assertIsSelected() }
        compose.onNodeWithText(expected.mode.label).assertIsSelected()
        compose.onNode(isToggleable()).assertIsOn()
    }
}
