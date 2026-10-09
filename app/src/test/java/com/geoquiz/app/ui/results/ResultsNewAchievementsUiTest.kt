package com.geoquiz.app.ui.results

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The "New achievements" card on the real [ResultsScreen], from the stored result (3.5b). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ResultsNewAchievementsUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun launch(result: CompletedQuiz) {
        val viewModel = ScreenTestFixtures.resultsViewModel(result)
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                ResultsScreen(
                    onPlayAgain = { _, _, _, _ -> },
                    onGoHome = {},
                    onViewAnswers = {},
                    onPractiseMissed = { _, _, _, _ -> },
                    viewModel = viewModel
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun oneUnlockedAchievementShowsItsTitleAndDescription() {
        val achievement = Achievement.FIRST_STEPS
        launch(ScreenTestFixtures.RESULT.copy(newAchievementIds = listOf(achievement.id)))

        compose.onNode(
            hasText("New achievement") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(achievement.title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(achievement.description).performScrollTo().assertIsDisplayed()
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        ).assertExists()
    }

    @Test
    fun severalUnlockedAchievementsUseThePluralHeading() {
        val unlocked = Achievement.entries.take(2)
        launch(ScreenTestFixtures.RESULT.copy(newAchievementIds = unlocked.map { it.id }))

        compose.onNodeWithText("New achievements").performScrollTo().assertIsDisplayed()
        unlocked.forEach { compose.onNodeWithText(it.title).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun noUnlockedAchievementsShowsNoCard() {
        launch(ScreenTestFixtures.RESULT)

        compose.onNodeWithText("Play Again").assertExists()
        compose.onNodeWithText("New achievement").assertDoesNotExist()
        compose.onNodeWithText("New achievements").assertDoesNotExist()
    }
}
