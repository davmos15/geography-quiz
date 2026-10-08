package com.geoquiz.app.ui.category

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.db.QuizBestScore
import com.geoquiz.app.data.local.preferences.PinnedCategoriesRepository
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.PinnedCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestGameModes
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Category rows (3.4c): the pin toggle and the live "Best" score, on the real screen and ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CategoryListScreenUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val bestScores = MutableStateFlow<Map<String, QuizBestScore>>(emptyMap())
    private val pins = MutableStateFlow<List<PinnedCategory>>(emptyList())
    private val quizzes = mutableListOf<Triple<String, String, String>>()

    private val pinnedRepository = mockk<PinnedCategoriesRepository> {
        every { pinnedCategories } returns pins
        coEvery { setPinned(any(), any(), any(), any()) } answers {
            val pin = PinnedCategory(firstArg(), secondArg(), thirdArg())
            pins.value = if (arg<Boolean>(3)) (pins.value - pin) + pin else pins.value - pin
        }
    }

    private fun launch() {
        val countries = mockk<CountryRepository> {
            every { getAllCountries() } returns flowOf(CategoryOptionsFixture.COUNTRIES)
        }
        val history = mockk<QuizHistoryRepository> {
            every { bestScoresForMode(any()) } returns bestScores
            every { masteryStarsForMode(any()) } returns flowOf(emptyMap())
        }
        val playGames = mockk<PlayGamesAchievementService> {
            every { playerName } returns MutableStateFlow("Sam")
        }
        val viewModel = CategoryListViewModel(
            savedStateHandle = SavedStateHandle(mapOf("quizMode" to "countries", "groupId" to CategoryGroup.REGIONS.id)),
            repository = countries,
            optionsBuilder = CategoryOptionsFixture.builder(),
            quizHistoryRepository = history,
            challengeRepository = mockk<ChallengeRepository>(relaxed = true),
            playGamesService = playGames,
            settingsRepository = ScreenTestFixtures.settingsRepository(),
            pinnedCategoriesRepository = pinnedRepository,
            challengeLinkSigner = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray()),
            gameModes = TestGameModes.registry()
        )
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                CategoryListScreen(
                    quizMode = "countries",
                    onNavigateBack = {},
                    onStartQuiz = { type, value, difficulty -> quizzes += Triple(type, value, difficulty) },
                    viewModel = viewModel
                )
            }
        }
        compose.waitForIdle()
    }

    private fun hasClickLabel(label: String) = SemanticsMatcher("onClick label = $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private val africaRow get() = compose.onNode(hasText("Africa").and(hasClickLabel("Start quiz")))

    @Test
    fun `the pin toggle pins and unpins with its name and state`() {
        launch()

        compose.onNodeWithContentDescription("Pin Africa")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsOff()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Unpin Africa").assertIsDisplayed().assertIsOn()
        compose.onNodeWithContentDescription("Pin Europe").assertIsOff()
        assertEquals(listOf(PinnedCategory("countries", "region", "Africa")), pins.value)

        compose.onNodeWithContentDescription("Unpin Africa").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Pin Africa").assertIsOff()
        assertEquals(emptyList<PinnedCategory>(), pins.value)
        // Pinning never starts a quiz
        assertEquals(emptyList<Triple<String, String, String>>(), quizzes)
    }

    @Test
    fun `the pin toggle is its own TalkBack item, separate from the row`() {
        launch()

        africaRow.assertIsDisplayed()
            .assert(hasContentDescription("Pin Africa").not())
            .performClick()

        assertEquals(listOf(Triple("region", "Africa", "normal")), quizzes)
    }

    @Test
    fun `Best appears and updates live as history changes`() {
        launch()
        africaRow.assert(hasContentDescription("Best score", substring = true).not())

        bestScores.value = mapOf("region|Africa" to QuizBestScore("region", "Africa", 2.25, 3, 4))
        compose.waitForIdle()
        africaRow.assert(hasContentDescription("Best score 3 of 4, 2 points"))

        bestScores.value = mapOf("region|Africa" to QuizBestScore("region", "Africa", 4.8, 4, 4))
        compose.waitForIdle()
        africaRow.assert(hasContentDescription("Best score 4 of 4, 5 points"))
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the pin toggle still shows and works`() {
        launch()

        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Pin Africa"))
        compose.onNodeWithContentDescription("Pin Africa").assertIsDisplayed().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Unpin Africa").assertIsDisplayed().assertIsOn()
    }
}
