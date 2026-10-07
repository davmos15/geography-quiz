package com.geoquiz.app.ui.results

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Practise the ones you missed" on the real [ResultsScreen] (3.5c, D21). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResultsPractiseMissedUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private data class PractiseCall(val mode: String, val type: String, val value: String, val difficulty: String)

    private val practiseCalls = mutableListOf<PractiseCall>()
    private val playAgainCalls = mutableListOf<PractiseCall>()

    private fun launch(result: CompletedQuiz) {
        val viewModel = ScreenTestFixtures.resultsViewModel(result)
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                ResultsScreen(
                    onPlayAgain = { mode, type, value, difficulty ->
                        playAgainCalls += PractiseCall(mode, type, value, difficulty)
                    },
                    onGoHome = {},
                    onViewAnswers = {},
                    onPractiseMissed = { mode, type, value, difficulty ->
                        practiseCalls += PractiseCall(mode, type, value, difficulty)
                    },
                    viewModel = viewModel
                )
            }
        }
        compose.waitForIdle()
    }

    /** Eight countries, five named, so AUT, KEN and CHL were missed ([ScreenTestFixtures.RESULT]). */
    private val result = ScreenTestFixtures.RESULT.copy(
        quizModeId = "capitals",
        difficultyId = Difficulty.EASY.id
    )

    @Test
    fun `with misses the button shows the count and starts a practice quiz of exactly those items`() {
        launch(result)

        val missed = result.missedCodes()
        assertEquals(3, missed.size)
        compose.onNodeWithText("Practise the ones you missed (3)")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(
            listOf(PractiseCall("capitals", "practice", missed.joinToString("+"), "easy")),
            practiseCalls
        )
        // Not a practice result: Share and Challenge stay.
        compose.onNodeWithText("Share").assertExists()
        compose.onNodeWithText("Challenge").assertExists()
    }

    @Test
    fun `one miss uses the singular label`() {
        val codes = result.countryCodes
        launch(result.copy(answeredCodes = codes.drop(1), correct = codes.size - 1))

        compose.onNodeWithText("Practise the one you missed").performScrollTo().performClick()

        assertEquals(listOf(codes.first()), practiseCalls.map { it.value })
    }

    @Test
    fun `a perfect result shows no practise button`() {
        launch(result.copy(answeredCodes = result.countryCodes, correct = result.total))

        compose.onNodeWithText("Play Again").assertExists()
        compose.onNodeWithText("Practise the ones you missed", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Practise the one you missed", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a practice result hides Share and Challenge, offers its own misses and replays itself`() {
        val practice = QuizCategory.Practice(listOf("JPN", "KEN", "CHL"))
        launch(
            result.copy(
                categoryType = practice.typeKey,
                categoryValue = practice.valueKey,
                categoryName = practice.displayName,
                countryCodes = practice.codes,
                answeredCodes = listOf("KEN"),
                correct = 1,
                total = 3,
                difficultyId = Difficulty.HARD.id,
                hardMode = true
            )
        )

        compose.onNodeWithText("Share").assertDoesNotExist()
        compose.onNodeWithText("Challenge").assertDoesNotExist()
        compose.onNodeWithText("Practise your misses").assertIsDisplayed()
        compose.onNodeWithText("Practice quizzes don’t count", substring = true).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Practise the ones you missed (2)").performScrollTo().performClick()
        compose.onNodeWithText("Play Again").performScrollTo().performClick()

        assertEquals(listOf(PractiseCall("capitals", "practice", "JPN+CHL", "hard")), practiseCalls)
        assertEquals(listOf(PractiseCall("capitals", "practice", "JPN+KEN+CHL", "hard")), playAgainCalls)
        assertTrue(practiseCalls.none { it.value.contains("KEN") })
    }
}
