package com.geoquiz.app.ui.results

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Results on tablets and landscape phones (3.7): summary in one pane, actions in the other. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h600dp")
class ResultsWideLayoutUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val homes = mutableListOf<String>()

    private fun launch() {
        val viewModel = ScreenTestFixtures.resultsViewModel()
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                ResultsScreen(
                    onPlayAgain = { _, _, _, _ -> },
                    onGoHome = { homes += it },
                    onViewAnswers = {},
                    onPractiseMissed = { _, _, _, _ -> },
                    viewModel = viewModel
                )
            }
        }
        compose.waitForIdle()
    }

    private fun assertActionsBesideSummary() {
        val summary = compose.onNodeWithText("Quiz Complete!").assertIsDisplayed().getBoundsInRoot()
        listOf("View Answers", "Play Again", "Home").forEach { label ->
            val bounds = compose.onNodeWithText(label).assertExists().getBoundsInRoot()
            assertTrue("$label $bounds is not beside the summary $summary", bounds.left >= summary.right)
        }
    }

    @Test
    fun `on a tablet the actions sit beside the score`() {
        launch()

        assertActionsBesideSummary()
        compose.onNodeWithText("Score").assertIsDisplayed().assertIsBelow(compose.onNodeWithText("Quiz Complete!"))
        compose.onNodeWithText("Home").performClick()
        assertEquals(listOf("countries"), homes)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp")
    fun `on a phone in landscape the actions sit beside the score`() {
        launch()

        assertActionsBesideSummary()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on a phone in portrait the actions stay below the score`() {
        launch()

        compose.onNodeWithText("Play Again").assertIsBelow(compose.onNodeWithText("Score"))
    }
}
