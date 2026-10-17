package com.geoquiz.app.ui.results

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestGameModes
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D35: a new (non-classic) mode's result hides the Challenge button but keeps Share. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResultsNewModeUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun launch(result: CompletedQuiz, extraModes: Set<GameMode> = emptySet()) {
        val viewModel = ScreenTestFixtures.resultsViewModel(result, extraModes = extraModes)
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                ResultsScreen(
                    onPlayAgain = {},
                    onGoHome = {},
                    onViewAnswers = {},
                    onPractiseMissed = {},
                    viewModel = viewModel
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a new mode hides Challenge but keeps Share`() {
        launch(
            ScreenTestFixtures.RESULT.copy(quizModeId = "silhouettes"),
            extraModes = setOf(TestGameModes.silhouettes())
        )

        compose.onNodeWithText("Share").assertExists()
        compose.onNodeWithText("Challenge").assertDoesNotExist()
    }

    @Test
    fun `a new mode's own label is shown, not the Countries fallback`() {
        launch(
            ScreenTestFixtures.RESULT.copy(quizModeId = "silhouettes"),
            extraModes = setOf(TestGameModes.silhouettes())
        )

        // The real Silhouettes spec's result label, not GameModeRegistry.findOrDefault's
        // Countries fallback for an unregistered id.
        compose.onNodeWithText("Country silhouettes").assertExists()
        compose.onNodeWithText("Countries").assertDoesNotExist()
    }

    @Test
    fun `tap_map also hides Challenge but keeps Share`() {
        launch(ScreenTestFixtures.RESULT.copy(quizModeId = "tap_map"))

        compose.onNodeWithText("Share").assertExists()
        compose.onNodeWithText("Challenge").assertDoesNotExist()
    }

    @Test
    fun `a classic mode still shows Challenge`() {
        launch(ScreenTestFixtures.RESULT.copy(quizModeId = "countries"))

        compose.onNodeWithText("Share").assertExists()
        compose.onNodeWithText("Challenge").assertExists()
    }

    @Test
    fun `the daily challenge hides Play Again but keeps Home (one attempt per day, D54)`() {
        launch(
            ScreenTestFixtures.RESULT.copy(quizModeId = DailyChallengeGameMode.MODE_ID),
            extraModes = setOf(DailyChallengeGameMode())
        )

        compose.onNodeWithText("Play Again").assertDoesNotExist()
        compose.onNodeWithText("Home").assertExists()
    }

    @Test
    fun `a classic mode still shows Play Again`() {
        launch(ScreenTestFixtures.RESULT.copy(quizModeId = "countries"))

        compose.onNodeWithText("Play Again").assertExists()
    }
}
