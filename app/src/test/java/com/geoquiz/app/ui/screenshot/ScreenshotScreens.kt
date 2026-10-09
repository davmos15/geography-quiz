package com.geoquiz.app.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.db.QuizBestScore
import com.geoquiz.app.data.local.preferences.PinnedCategoriesRepository
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.AchievementTier
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import com.geoquiz.app.domain.model.PinnedCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestGameModes
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.achievements.AchievementDisplayInfo
import com.geoquiz.app.ui.achievements.AchievementsContent
import com.geoquiz.app.ui.achievements.AchievementsUiState
import com.geoquiz.app.ui.category.CategoryListScreen
import com.geoquiz.app.ui.category.CategoryListViewModel
import com.geoquiz.app.ui.category.CategoryOptionsFixture
import com.geoquiz.app.ui.navigation.AppNavigationRail
import com.geoquiz.app.ui.navigation.Screen
import com.geoquiz.app.ui.play.PlayScreen
import com.geoquiz.app.ui.quiz.QuizScreen
import com.geoquiz.app.ui.quiz.QuizUiState
import com.geoquiz.app.ui.results.AnswerReviewScreen
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.settings.SettingsScreen
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The screens the screenshot tests capture, each fed fixed state without Hilt. ViewModels are
 * built when a factory is called (once per test, outside composition); the returned lambda
 * only composes the screen. Nothing reads a clock or an unseeded random: the quiz clock is
 * frozen (timer 00:00), the Easy quiz uses the harness's seeded [com.geoquiz.app.domain.mode.QuizRandom],
 * and every list comes from the fixtures. Needs [com.geoquiz.app.testutil.MainDispatcherRule].
 */
object ScreenshotScreens {

    /** Banner ads skip themselves in inspection mode, as in previews. */
    @Composable
    private fun NoAds(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalInspectionMode provides true) { content() }
    }

    // ---- Quiz ----

    /**
     * A typed-answer quiz over [ScreenTestFixtures.EIGHT]: three named and one strike. At Hard
     * a final typo gets the "check the spelling" near-miss feedback; at Normal it is left out.
     */
    fun quizInProgress(hardMode: Boolean = true): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.QuizHarness(
            countries = ScreenTestFixtures.EIGHT,
            hardMode = hardMode
        ).viewModel
        val answers = listOf("France", "Peru", "Japan", "Narnia") + if (hardMode) listOf("Germnay") else emptyList()
        answers.forEach {
            viewModel.onInputChange(it)
            viewModel.onSubmitAnswer()
        }
        return { QuizScreen(onQuizComplete = {}, onNavigateHome = {}, viewModel = viewModel) }
    }

    /**
     * An Easy (multiple choice) quiz of the Americas (Brazil, Chile, Peru) with the rest of
     * [ScreenTestFixtures.EIGHT] as outsiders, so the prompt is "in the set" with four
     * options. One wrong pick has been made: its result (correct option blue with a tick, the
     * pick orange with a cross) stays on screen because virtual time never advances.
     */
    fun quizEasy(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.QuizHarness(
            countries = listOf(TestQuizData.PERU, ScreenTestFixtures.BRAZIL, ScreenTestFixtures.CHILE),
            allCountries = ScreenTestFixtures.EIGHT,
            difficulty = Difficulty.EASY,
            categoryType = "region",
            categoryValue = "Americas"
        ).viewModel
        val choice = checkNotNull((viewModel.uiState.value as QuizUiState.Active).state.choice) {
            "Easy quiz has no question"
        }
        viewModel.onChoiceSelected(choice.options.first { it.code != choice.targetCode }.code)
        return { QuizScreen(onQuizComplete = {}, onNavigateHome = {}, viewModel = viewModel) }
    }

    // ---- Results ----

    /** [ScreenTestFixtures.RESULT] with two newly unlocked achievements (the card) and three misses (the practise button). */
    val RESULT_WITH_ACHIEVEMENTS = ScreenTestFixtures.RESULT.copy(
        newAchievementIds = Achievement.entries.take(2).map { it.id }
    )

    fun results(result: CompletedQuiz = ScreenTestFixtures.RESULT): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.resultsViewModel(result)
        return {
            ResultsScreen(
                onPlayAgain = { _, _, _, _ -> },
                onGoHome = {},
                onViewAnswers = {},
                onPractiseMissed = { _, _, _, _ -> },
                viewModel = viewModel
            )
        }
    }

    fun answerReview(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.answerReviewViewModel()
        return { AnswerReviewScreen(onNavigateBack = {}, onGoHome = {}, viewModel = viewModel) }
    }

    // ---- Play ----

    /** Feature flags at their defaults, plus Today's challenge on. */
    private fun dailyChallengeOn(): List<FeatureFlagState> = FeatureFlag.entries.map {
        val daily = it == FeatureFlag.DAILY_CHALLENGE
        FeatureFlagState(it, enabled = daily || it.defaultEnabled, overridden = daily)
    }

    /** Two pins in other modes than the one shown. */
    val PINS = listOf(
        PinnedCategory("flags", "flagcolor", "red"),
        PinnedCategory("capitals", "region", "Asia")
    )

    /**
     * The Play tab, Countries selected, with every top card: Today's challenge (flag on),
     * Continue (the saved starting-letter quiz), Recommended next and two Pinned rows.
     */
    fun play(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.playViewModel(
            flagStates = flowOf(dailyChallengeOn()),
            pins = flowOf(PINS)
        )
        return {
            NoAds {
                PlayScreen(
                    onOpenCategory = { _, _ -> },
                    onStartQuiz = { _, _, _ -> },
                    onOpenMode = {},
                    viewModel = viewModel
                )
            }
        }
    }

    // ---- Category list ----

    /**
     * Countries "By Region" over [CategoryOptionsFixture]: the difficulty selector (Normal),
     * Africa with two stars, a Best of 3 of 4 and pinned; the other regions with no history.
     */
    fun categoryList(): @Composable () -> Unit {
        val countries = mockk<CountryRepository> {
            every { getAllCountries() } returns flowOf(CategoryOptionsFixture.COUNTRIES)
        }
        val history = mockk<QuizHistoryRepository> {
            every { bestScoresForMode(any()) } returns
                flowOf(mapOf("region|Africa" to QuizBestScore("region", "Africa", 2.25, 3, 4)))
            every { masteryStarsForMode(any()) } returns flowOf(mapOf("region|Africa" to 2))
        }
        val pins = mockk<PinnedCategoriesRepository>(relaxUnitFun = true) {
            every { pinnedCategories } returns MutableStateFlow(listOf(PinnedCategory("countries", "region", "Africa")))
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
            pinnedCategoriesRepository = pins,
            challengeLinkSigner = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray()),
            gameModes = TestGameModes.registry()
        )
        return {
            CategoryListScreen(
                quizMode = "countries",
                onNavigateBack = {},
                onStartQuiz = { _, _, _ -> },
                viewModel = viewModel
            )
        }
    }

    // ---- Achievements ----

    /** One unlocked card of each tier, then two locked ones (the first achievements not used above). */
    fun achievementsState(): AchievementsUiState {
        val gold = Achievement.entries.first { it.tier == AchievementTier.GOLD }
        val silver = Achievement.entries.first { it.tier == AchievementTier.SILVER }
        val bronze = Achievement.entries.first { it.tier == AchievementTier.BRONZE }
        val locked = Achievement.entries.filter { it != gold && it != silver && it != bronze }.take(2)
        return AchievementsUiState(
            achievements = listOf(gold, silver, bronze).map { AchievementDisplayInfo(it, unlocked = true) } +
                locked.map { AchievementDisplayInfo(it, unlocked = false) }
        )
    }

    /** The Achievements tab (no back arrow), from [achievementsState]. */
    fun achievements(): @Composable () -> Unit {
        val state = achievementsState()
        return { AchievementsContent(state = state) }
    }

    // ---- Settings ----

    fun settings(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.settingsViewModel()
        return { SettingsScreen(viewModel = viewModel) }
    }

    // ---- Navigation rail ----

    /** The rail on its own, with Play selected. */
    fun navRail(): @Composable () -> Unit = {
        AppNavigationRail(isSelected = { it == Screen.Play.route }, onSelect = {})
    }
}
