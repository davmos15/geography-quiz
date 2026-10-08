package com.geoquiz.app.testutil

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagColorEntity
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.data.local.db.FlagElementEntity
import com.geoquiz.app.data.local.db.SavedQuizEntity
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.AdManager
import com.geoquiz.app.data.service.BillingRepository
import com.geoquiz.app.data.service.ConsentManager
import com.geoquiz.app.data.service.InterstitialPolicy
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.domain.usecase.ResetAllDataUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.ui.play.PlayCategoryGroups
import com.geoquiz.app.ui.play.PlayViewModel
import com.geoquiz.app.ui.navigation.Screen
import com.geoquiz.app.ui.quiz.QuizViewModel
import com.geoquiz.app.ui.results.AnswerReviewViewModel
import com.geoquiz.app.ui.results.ResultsViewModel
import com.geoquiz.app.ui.settings.SettingsViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.random.Random

/**
 * Real screen ViewModels built from fakes and mocks, with fixed data, for the Compose UI and
 * screenshot tests. Nothing here touches the network, ads, Play Games or a database.
 *
 * Use with [MainDispatcherRule]: ViewModel coroutines then run eagerly on an unconfined test
 * dispatcher whose virtual time never advances, so the quiz timer stays at 00:00.
 */
object ScreenTestFixtures {

    val JAPAN = TestQuizData.country("JPN", "Japan", "Tokyo", region = "Asia")
    val KENYA = TestQuizData.country("KEN", "Kenya", "Nairobi", region = "Africa")
    val BRAZIL = TestQuizData.country("BRA", "Brazil", "Brasília", region = "Americas")
    val CHILE = TestQuizData.country("CHL", "Chile", "Santiago", region = "Americas")

    /** Eight countries for screenshots (shown sorted by name). */
    val EIGHT: List<Country> = TestQuizData.THREE + listOf(TestQuizData.PERU, JAPAN, KENYA, BRAZIL, CHILE)

    /** A clock that never moves: the quiz timer always reads 00:00. */
    object FrozenClock : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = 1_000_000L
    }

    /** Country lookups backed by [countries]; answers match names, ignoring case. */
    fun countryRepository(countries: List<Country>): CountryRepository {
        val normalize = NormalizeInputUseCase()
        return mockk(relaxed = true) {
            every { getAllCountries() } returns flowOf(countries)
            coEvery { getCountryCount() } returns countries.size
            coEvery { getCountriesByCodes(any()) } answers {
                val codes = firstArg<List<String>>()
                countries.filter { it.code in codes }
            }
            coEvery { findCountryByAnswer(any()) } answers {
                val input = firstArg<String>().trim()
                countries.find { it.name.equals(input, ignoreCase = true) }
            }
            coEvery { getCountryAnswerAliases() } returns
                countries.map { AnswerAlias(normalize(it.name), it) }
        }
    }

    fun settingsRepository(
        showTimer: Boolean = true,
        showFlags: Boolean = false,
        showCountryHint: Boolean = false,
        difficulty: Difficulty = Difficulty.NORMAL,
        vibration: Boolean = true
    ): SettingsRepository = mockk(relaxed = true) {
        every { this@mockk.vibration } returns flowOf(vibration)
        every { this@mockk.showTimer } returns flowOf(showTimer)
        every { this@mockk.showFlags } returns flowOf(showFlags)
        every { this@mockk.showCountryHint } returns flowOf(showCountryHint)
        every { this@mockk.difficulty } returns flowOf(difficulty)
    }

    /**
     * A Countries-mode "All Countries" quiz with the real answer checking and completion.
     * [hardMode] starts it at Hard through the route, otherwise at Normal.
     */
    class QuizHarness(
        countries: List<Country> = TestQuizData.THREE,
        hardMode: Boolean = false,
        showTimer: Boolean = true,
        vibration: Boolean = true
    ) {
        val completedQuizzes = FakeCompletedQuizRepository()

        private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true) {
            coEvery { getSavedQuiz() } returns null
        }
        private val achievementRepository = mockk<AchievementRepository> {
            coEvery { onQuizCompleted(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        }
        private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase> {
            coEvery { this@mockk.invoke(any()) } returns countries
        }

        private val gameModes = TestGameModes.registry(
            getCountriesForQuiz = getCountriesForQuiz,
            validateAnswer = ValidateAnswerUseCase(countryRepository(countries), NormalizeInputUseCase())
        )

        val viewModel = QuizViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "quizMode" to "countries",
                    "categoryType" to "all",
                    "categoryValue" to "_",
                    QuizViewModel.ARG_DIFFICULTY to (if (hardMode) Difficulty.HARD else Difficulty.NORMAL).id
                )
            ),
            gameModes = gameModes,
            completeQuiz = CompleteQuizUseCase(
                gameModes = gameModes,
                completedQuizRepository = completedQuizzes,
                savedQuizRepository = savedQuizRepository,
                achievementRepository = achievementRepository,
                quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true),
                playGamesService = mockk<PlayGamesAchievementService>(relaxed = true)
            ),
            settingsRepository = settingsRepository(showTimer = showTimer, vibration = vibration),
            savedQuizRepository = savedQuizRepository,
            adManager = mockk<AdManager>(relaxed = true),
            clock = FrozenClock,
            countryRepository = countryRepository(countries),
            quizRandom = QuizRandom(Random(42))
        )
    }

    /** A finished Countries quiz over [EIGHT]: five named, two wrong guesses, 3:12. */
    val RESULT: CompletedQuiz = TestQuizData.completedQuiz(
        id = "result-shot",
        countryCodes = EIGHT.map { it.code },
        answeredCodes = listOf("FRA", "DEU", "PER", "JPN", "BRA"),
        incorrectGuessStrings = listOf("Narnia", "Tokyo")
    ).copy(timeSeconds = 192, score = 41.5)

    fun resultsViewModel(result: CompletedQuiz = RESULT): ResultsViewModel {
        val playGames = mockk<PlayGamesAchievementService>(relaxed = true) {
            every { playerName } returns MutableStateFlow("A friend")
        }
        val policy = mockk<InterstitialPolicy>(relaxed = true) {
            every { onQuizCompleted(any()) } returns false
        }
        return ResultsViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Screen.Results.ARG_RESULT_ID to result.id)),
            playGamesService = playGames,
            challengeRepository = mockk<ChallengeRepository>(relaxed = true),
            completedQuizRepository = FakeCompletedQuizRepository(result),
            adManager = mockk<AdManager>(relaxed = true),
            interstitialPolicy = policy,
            challengeLinkSigner = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray())
        )
    }

    fun answerReviewViewModel(
        result: CompletedQuiz = RESULT,
        countries: List<Country> = EIGHT
    ) = AnswerReviewViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Screen.AnswerReview.ARG_RESULT_ID to result.id)),
        completedQuizRepository = FakeCompletedQuizRepository(result),
        countryRepository = countryRepository(countries),
        settingsRepository = settingsRepository()
    )

    /** Flag colours and elements for [EIGHT]-style Play tests: 3 colours, 1 element. */
    val FLAG_COLOURS: List<FlagColorEntity> = listOf(
        FlagColorEntity("FRA", "blue"), FlagColorEntity("FRA", "white"), FlagColorEntity("FRA", "red"),
        FlagColorEntity("AUT", "white"), FlagColorEntity("AUT", "red"),
        FlagColorEntity("PER", "white"), FlagColorEntity("PER", "red")
    )
    val FLAG_ELEMENTS: List<FlagElementEntity> = listOf(FlagElementEntity("BRA", "star"))

    /** A saved Countries quiz for the "Resume quiz" card: starting letter A, two answered. */
    val SAVED_QUIZ = SavedQuizEntity(
        categoryType = "startletter",
        categoryValue = "A",
        answeredCountryCodes = "[\"AUT\",\"FRA\"]",
        timeElapsedSeconds = 75,
        savedAtMillis = 1_700_000_000_000L
    )

    /**
     * The Play tab over [countries], with [savedQuiz] on the "Resume quiz" card, feature flags
     * from [flagStates] (all defaults, so all off, unless given) and the classic registry plus
     * [extraModes].
     */
    fun playViewModel(
        countries: List<Country> = EIGHT,
        savedQuiz: SavedQuizEntity? = SAVED_QUIZ,
        flagStates: Flow<List<FeatureFlagState>> = flowOf(defaultFlagStates()),
        extraModes: Set<GameMode> = emptySet(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        savedQuizRepository: SavedQuizRepository = savedQuizRepository(savedQuiz)
    ): PlayViewModel {
        val flags = mockk<FeatureFlagRepository> {
            every { states } returns flagStates
        }
        val flagColorDao = mockk<FlagColorDao> { coEvery { getAllMappings() } returns FLAG_COLOURS }
        val flagElementDao = mockk<FlagElementDao> { coEvery { getAllMappings() } returns FLAG_ELEMENTS }
        return PlayViewModel(
            savedStateHandle = savedStateHandle,
            registry = TestGameModes.registry(extraModes = extraModes),
            featureFlagRepository = flags,
            countryRepository = countryRepository(countries),
            savedQuizRepository = savedQuizRepository,
            categoryGroups = PlayCategoryGroups(flagColorDao, flagElementDao)
        )
    }

    /** A relaxed saved-quiz repository holding [savedQuiz]; answered codes are parsed from the JSON. */
    fun savedQuizRepository(savedQuiz: SavedQuizEntity? = SAVED_QUIZ): SavedQuizRepository =
        mockk(relaxed = true) {
            every { this@mockk.savedQuiz } returns flowOf(savedQuiz)
            every { parseAnsweredCodes(any()) } answers {
                Regex("[A-Z]{3}").findAll(firstArg<String>()).map { it.value }.toSet()
            }
        }

    fun defaultFlagStates(): List<FeatureFlagState> =
        FeatureFlag.entries.map { FeatureFlagState(it, enabled = it.defaultEnabled, overridden = false) }

    fun settingsViewModel(): SettingsViewModel {
        val billing = mockk<BillingRepository>(relaxed = true) {
            every { adsRemoved } returns MutableStateFlow(false)
            every { price } returns MutableStateFlow("A$4.99")
        }
        val consent = mockk<ConsentManager>(relaxed = true) {
            every { privacyOptionsRequired } returns MutableStateFlow(false)
        }
        return SettingsViewModel(
            settingsRepository = settingsRepository(showTimer = true, difficulty = Difficulty.HARD),
            billingRepository = billing,
            consentManager = consent,
            resetAllData = mockk<ResetAllDataUseCase>(relaxed = true)
        )
    }
}

/** Runs `Dispatchers.Main` (and so `viewModelScope`) on an unconfined test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun finished(description: Description) {
        kotlinx.coroutines.Dispatchers.resetMain()
    }
}
