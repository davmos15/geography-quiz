package com.geoquiz.app.ui.silhouettes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountryOutlineUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.TestQuizData.country
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Key semantics of [SilhouettesGameScreen] at 100% and 200% font, and on short/wide layouts
 * (task 5.1 review, S5): the progress, lives, difficulty and controls are all reachable, and the
 * outline's description never names the country.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class SilhouettesGameScreenUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val france = country("FRA", "France", "Paris")
    private val germany = country("DEU", "Germany", "Berlin")
    private val italy = country("ITA", "Italy", "Rome")
    private val spain = country("ESP", "Spain", "Madrid")

    private val outline = OutlineModel(
        cca3 = "FRA",
        includeRemote = false,
        projection = LambertAzimuthalEqualAreaProjection(0.0, 0.0),
        rings = emptyList(),
        neighbours = emptyList(),
        frame = PlaneRect(-1f, -1f, 1f, 1f),
        scale = 1f
    )

    private fun viewModel(
        difficulty: Difficulty = Difficulty.NORMAL,
        outlineResult: OutlineModel? = outline,
        validateAnswerResult: (String) -> AnswerResult = { AnswerResult.Incorrect }
    ): SilhouettesGameViewModel {
        val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
        coEvery { getCountriesForQuiz(any()) } returns listOf(france)
        val validateAnswer = mockk<ValidateAnswerUseCase>()
        coEvery { validateAnswer(any(), any(), any()) } answers { validateAnswerResult(firstArg()) }
        val mode = SilhouettesGameMode(
            CountriesQuestionGenerator(getCountriesForQuiz, mockk<GetCountriesForFlagQuizUseCase>()),
            validateAnswer,
            CalculateScoreUseCase(),
            QuizRandom(Random(1))
        )
        val countryRepository = mockk<CountryRepository>()
        every { countryRepository.getAllCountries() } returns flowOf(listOf(france, germany, italy, spain))
        val getCountryOutline = mockk<GetCountryOutlineUseCase>()
        coEvery { getCountryOutline(any(), any(), any()) } returns outlineResult
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.showTimer } returns flowOf(true)
        every { settingsRepository.vibration } returns flowOf(true)
        val completeQuiz = CompleteQuizUseCase(
            gameModes = GameModeRegistry(setOf(mode)),
            completedQuizRepository = FakeCompletedQuizRepository(),
            savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true),
            achievementRepository = mockk<AchievementRepository>(relaxed = true),
            quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true),
            playGamesService = mockk<PlayGamesAchievementService>(relaxed = true)
        )
        val handle = SavedStateHandle(
            buildMap<String, Any?> {
                put("categoryType", "all")
                put("categoryValue", "_")
                put(SilhouettesGameViewModel.ARG_DIFFICULTY, difficulty.id)
            }
        )
        return SilhouettesGameViewModel(
            savedStateHandle = handle,
            silhouettesGameMode = mode,
            countryRepository = countryRepository,
            getCountryOutline = getCountryOutline,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            clock = MonotonicClock { 0L },
            quizRandom = QuizRandom(Random(2))
        )
    }

    private fun launch(viewModel: SilhouettesGameViewModel) {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                SilhouettesGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `at 100 percent text the header, outline, answer field and actions are all shown`() {
        launch(viewModel())

        compose.onNodeWithContentDescription("Silhouette 1 of 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("3 lives left").assertIsDisplayed()
        compose.onNodeWithText("Enter a country name").assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsDisplayed()
        compose.onNodeWithText("Pass").assertIsDisplayed()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun `at 200 percent text the field and Submit stay reachable`() {
        launch(viewModel())

        compose.onNodeWithText("Enter a country name").assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsDisplayed()
    }

    @Test
    fun `Easy shows four options, not a text field, and its own prompt rather than Which of these fits`() {
        launch(viewModel(Difficulty.EASY))

        compose.onNodeWithText("Enter a country name").assertDoesNotExist()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
        // The target's own option is always among the four choices.
        compose.onNodeWithText("France").assertIsDisplayed()
        // task 5.1 review, B1: the screen's own prompt replaces MultipleChoicePanel's hidden one.
        compose.onNodeWithText("Which country is this?").assertIsDisplayed()
        compose.onNode(hasText("Which of these fits", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `the answer field stays enabled (not disabled) during the brief pause after a correct answer`() {
        val vm = viewModel(validateAnswerResult = { AnswerResult.Correct("France") })
        launch(vm)

        compose.onNodeWithText("Enter a country name").performTextInput("France")
        compose.onNodeWithText("Submit").performClick()
        compose.waitForIdle()

        // task 5.1 review, S1: disabling the field (rather than making it read-only) would close
        // the keyboard on every silhouette.
        compose.onNodeWithText("Enter a country name").assertIsEnabled()
    }

    @Test
    fun `a missing outline shows a fallback message, not an endless spinner`() {
        launch(viewModel(outlineResult = null))

        compose.onNodeWithText("Outline unavailable").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h300dp")
    fun `on a short wide screen the answer field and the outline are both displayed`() {
        launch(viewModel())

        compose.onNodeWithText("Enter a country name").assertIsDisplayed()
        compose.onNodeWithContentDescription("Silhouette 1 of 1").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h400dp", fontScale = 2f)
    fun `at 200 percent font on a short screen the answer field and the outline are both displayed`() {
        launch(viewModel())

        compose.onNodeWithText("Enter a country name").assertIsDisplayed()
        compose.onNodeWithContentDescription("Silhouette 1 of 1").assertIsDisplayed()
    }
}
