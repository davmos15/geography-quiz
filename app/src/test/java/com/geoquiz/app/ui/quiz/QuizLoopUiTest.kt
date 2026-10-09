package com.geoquiz.app.ui.quiz

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures.QuizHarness
import com.geoquiz.app.ui.quiz.feedback.AnswerFeedbackEvent
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The quiz loop through the real [QuizScreen] and [QuizViewModel] (real answer checking and
 * completion over fakes) on Robolectric. Assertions use text and the TalkBack descriptions from
 * task 2.8, not pixels. The quiz has France, Germany and Austria, listed as Austria, France,
 * Germany (rows 1 to 3).
 */
@RunWith(RobolectricTestRunner::class)
// A typical phone; the default Robolectric screen (320x470 dp) clips the third row.
@Config(qualifiers = "w411dp-h891dp")
class QuizLoopUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val completedResultIds = mutableListOf<String>()

    /** Haptics the screen asked for (the real player needs a device). */
    private val haptics = mutableListOf<AnswerFeedbackEvent>()

    private fun launch(hardMode: Boolean = false, vibration: Boolean = true): QuizHarness {
        val harness = QuizHarness(hardMode = hardMode, vibration = vibration)
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                QuizScreen(
                    onQuizComplete = { completedResultIds += it },
                    onNavigateHome = {},
                    viewModel = harness.viewModel,
                    hapticFeedbackPlayer = { haptics += it }
                )
            }
        }
        compose.waitForIdle()
        return harness
    }

    private fun submit(answer: String) {
        compose.onNode(hasSetTextAction()).performTextInput(answer)
        compose.onNodeWithText("Submit").performClick()
        compose.waitForIdle()
    }

    @Test
    fun correctAnswerFillsItsRowAndUpdatesTheCount() {
        launch()
        compose.onNodeWithContentDescription("0 of 3 countries named").assertIsDisplayed()
        compose.onNodeWithContentDescription("Row 2, not yet answered").assertIsDisplayed()

        submit("France")

        compose.onNodeWithContentDescription("France, answered").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 3 countries named").assertIsDisplayed()
        compose.onNodeWithText("France - Correct!").assertIsDisplayed()
        compose.onNodeWithContentDescription("Row 2, not yet answered").assertDoesNotExist()
    }

    @Test
    fun wrongAnswerShowsIncorrectFeedbackAndCountsTheGuess() {
        launch()

        submit("Narnia")

        compose.onNodeWithText("Not recognised. Try again!").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 incorrect guess").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 of 3 countries named").assertIsDisplayed()
        // The typed answer stays in the field so it can be corrected.
        compose.onNode(hasSetTextAction() and hasText("Narnia")).assertExists()
    }

    @Test
    fun nearMissInHardModeAsksToCheckTheSpellingWithoutAStrike() {
        launch(hardMode = true)
        compose.onNodeWithContentDescription("0 of 3 incorrect guesses used").assertIsDisplayed()

        submit("Germnay")

        compose.onNodeWithText("Close – check the spelling").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 of 3 incorrect guesses used").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 of 3 countries named").assertIsDisplayed()
        compose.onNodeWithContentDescription("Row 3, not yet answered").assertIsDisplayed()
    }

    @Test
    fun typoOutsideHardModeCountsAsCorrect() {
        launch()

        submit("Germnay")

        compose.onNodeWithContentDescription("Germany, answered").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 3 countries named").assertIsDisplayed()
    }

    @Test
    fun givingUpRecordsTheQuizAndCompletes() {
        val harness = launch()
        submit("Austria")

        compose.onNodeWithContentDescription("Give Up").performClick()
        compose.onNodeWithText("You've named 1 of 3 countries. Are you sure?").assertIsDisplayed()
        compose.onNodeWithText("Yes, Give Up").performClick()
        compose.waitForIdle()

        assertEquals(1, completedResultIds.size)
        val stored = checkNotNull(harness.completedQuizzes.stored) { "no result recorded" }
        assertEquals(completedResultIds.single(), stored.id)
        assertEquals(listOf("AUT"), stored.answeredCodes)
        assertEquals(3, stored.total)
    }

    @Test
    fun keepGoingDismissesTheGiveUpDialog() {
        launch()

        compose.onNodeWithContentDescription("Give Up").performClick()
        compose.onNodeWithText("Keep Going").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Yes, Give Up").assertDoesNotExist()
        assertEquals(emptyList<String>(), completedResultIds)
    }

    // Answer feedback (3.3) and the pause announcement (Phase 2 follow-up (f))

    @Test
    fun eachAnswerPlaysItsHaptic() {
        launch()

        submit("France")
        submit("Narnia")

        assertEquals(listOf(AnswerFeedbackEvent.CORRECT, AnswerFeedbackEvent.INCORRECT), haptics)
    }

    @Test
    fun vibrationSwitchedOffPlaysNoHaptics() {
        launch(vibration = false)

        submit("France")
        submit("Narnia")

        assertEquals(emptyList<AnswerFeedbackEvent>(), haptics)
    }

    @Test
    fun recentAnswersAppearAboveTheFieldNewestFirst() {
        launch()
        compose.onNode(hasContentDescription("Recent answers", substring = true)).assertDoesNotExist()

        submit("France")
        submit("Germany")

        compose.onNodeWithContentDescription("Recent answers: Germany, France").assertIsDisplayed()
    }

    private fun politeStatus(text: String) =
        hasContentDescription(text) and
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun pausingAndResumingAreAnnouncedByAPersistentStatus() {
        launch()
        compose.onNode(politeStatus("Paused")).assertDoesNotExist()

        compose.onNodeWithContentDescription("Pause").performClick()
        compose.waitForIdle()

        compose.onNode(politeStatus("Paused")).assertExists()
        compose.onNodeWithText("Paused").assertIsDisplayed()

        compose.onNodeWithText("Resume").performClick()
        // Stop the clock short of the delay that clears "Resumed".
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(200)

        compose.onNode(politeStatus("Resumed")).assertExists()
        compose.onNodeWithText("Paused").assertDoesNotExist()
    }
}
