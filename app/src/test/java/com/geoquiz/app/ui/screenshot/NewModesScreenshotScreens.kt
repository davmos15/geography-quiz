package com.geoquiz.app.ui.screenshot

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.geo.AndroidGeoAssetSource
import com.geoquiz.app.data.geo.AssetMapRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountryOutlineUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestGameModes
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.category.CategoryOptionsFixture
import com.geoquiz.app.ui.map.MapPathCache
import com.geoquiz.app.ui.map.MapViewMode
import com.geoquiz.app.ui.map.CountryOutlinePathCache
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.silhouettes.SilhouettesGameScreen
import com.geoquiz.app.ui.silhouettes.SilhouettesGameViewModel
import com.geoquiz.app.ui.silhouettes.SilhouettesSetupScreen
import com.geoquiz.app.ui.silhouettes.SilhouettesSetupViewModel
import com.geoquiz.app.ui.silhouettes.SilhouettesUiState
import com.geoquiz.app.ui.tapmap.TapMapActiveContent
import com.geoquiz.app.ui.tapmap.TapMapGameState
import com.geoquiz.app.ui.tapmap.TapMapMapState
import com.geoquiz.app.ui.tapmap.TapMapNameAllActiveContent
import com.geoquiz.app.ui.tapmap.TapMapSetupScreen
import com.geoquiz.app.ui.tapmap.TapMapSetupViewModel
import com.geoquiz.app.ui.tapmap.loadTapMapMapState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.random.Random

/**
 * The screens task 5.3's screenshot tests capture for Country silhouettes (5.1) and Tap the map
 * (5.2/5.2b), built the same way as [ScreenshotScreens]: fixed state, no Hilt, deterministic
 * clocks/dispatchers/random. Country silhouettes has no stateless content composable (unlike Tap
 * the map's [TapMapActiveContent]/[TapMapNameAllActiveContent]), so its screens go through the
 * real [SilhouettesGameScreen] with a [SilhouettesGameViewModel] built from fakes, the same way
 * `SilhouettesGameScreenUiTest` does.
 *
 * Real map data: the outline and the map canvas both read the bundled `assets/geo` binary layers
 * via a real [AssetMapRepository] (no fake), exactly like `MapScreenshotTest`. The same flake
 * that test guards against (a path built asynchronously on `Dispatchers.Default`, missed by the
 * first capture) is avoided the same way: every path cache ([CountryOutlinePathCache.Shared] for
 * silhouettes, [MapPathCache.Shared] for the map canvas) is pre-built and awaited before the
 * composable is returned, and [GetCountryOutlineUseCase] is given [Dispatchers.Unconfined] so its
 * own `withContext` never leaves the calling coroutine (which itself runs eagerly on the
 * `UnconfinedTestDispatcher` `MainDispatcherRule` installs).
 */
object NewModesScreenshotScreens {

    // ---- Real map data (shared, read-only, safe to reuse across tests) --------------------

    private fun newMapRepository(): AssetMapRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AssetMapRepository(AndroidGeoAssetSource(context.assets), Dispatchers.IO)
    }

    /** The app's 197 countries, loaded the same way as `MapScreenshotTest`/`RegionPresetTest`. */
    private val realCountries: List<Country> by lazy {
        val source = File("../data/source")
        val extra = Json.parseToJsonElement(File(source, "alias_overrides.json").readText())
            .jsonObject.getValue("extraCountries").jsonArray.map { it.jsonPrimitive.content }.toSet()
        Json.parseToJsonElement(File(source, "countries.json").readText()).jsonArray.map { it.jsonObject }
            .filter { o -> o["unMember"]?.jsonPrimitive?.booleanOrNull == true || o.getValue("cca3").jsonPrimitive.content in extra }
            .map { o ->
                val name = o.getValue("name").jsonObject
                Country(
                    code = o.getValue("cca3").jsonPrimitive.content,
                    name = name.getValue("common").jsonPrimitive.content,
                    officialName = name.getValue("official").jsonPrimitive.content,
                    region = o.getValue("region").jsonPrimitive.content,
                    subregion = o["subregion"]?.jsonPrimitive?.content.orEmpty(),
                    nameLength = 0,
                    capital = "",
                )
            }
    }

    private fun realCountry(code: String): Country = realCountries.first { it.code == code }

    private val europeMemberCodes: Set<String> by lazy {
        realCountries.filter { RegionPreset.EUROPE.includes(it) }.map { it.code }.toSet()
    }

    /** A real Europe [TapMapMapState] (task 5.2/5.2b): the actual bundled 110m/50m/tap-zone layers. */
    private fun europeMapState(): TapMapMapState {
        val repository = newMapRepository()
        return runBlocking {
            loadTapMapMapState(
                mapRepository = repository,
                defaultDispatcher = Dispatchers.Unconfined,
                preset = RegionPreset.EUROPE,
                members = europeMemberCodes,
                isPractice = false
            )
        }
    }

    /**
     * Pre-builds [MapPathCache.Shared]'s entries for every layer in [mapState] under
     * [projection] and awaits them, so the first composition of [TapMapActiveContent] (or the
     * Name all variant) never meets a still-building (blank) map - the same fix as the outline
     * cache below, generalised to the map canvas.
     */
    private fun warmMapPaths(mapState: TapMapMapState, projection: MapProjection) = runBlocking {
        mapState.coarse?.let { MapPathCache.Shared.paths(it, projection).await() }
        mapState.detail?.let { MapPathCache.Shared.paths(it, projection).await() }
        mapState.tapZones?.let { MapPathCache.Shared.paths(it, projection).await() }
    }

    // ---- Country silhouettes (task 5.1) ----------------------------------------------------

    /**
     * A [SilhouettesGameViewModel] over a one-country round ([targetCode]/[targetName]), built
     * from fakes the way `SilhouettesGameScreenUiTest` does, but with the real
     * [GetCountryOutlineUseCase] (on [Dispatchers.Unconfined], so it resolves synchronously
     * during construction, like every other coroutine here) instead of a mocked one.
     */
    private fun silhouettesViewModel(
        difficulty: Difficulty,
        targetCode: String = "ITA",
        targetName: String = "Italy"
    ): SilhouettesGameViewModel {
        val target = TestQuizData.country(targetCode, targetName, "Rome")
        val germany = TestQuizData.GERMANY
        val france = TestQuizData.FRANCE
        val spain = TestQuizData.country("ESP", "Spain", "Madrid")

        val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
        coEvery { getCountriesForQuiz(any()) } returns listOf(target)
        val validateAnswer = mockk<ValidateAnswerUseCase>()
        coEvery { validateAnswer(any(), any(), any()) } answers {
            if (firstArg<String>() == "Germany") AnswerResult.Correct("Germany") else AnswerResult.Incorrect
        }
        val mode = SilhouettesGameMode(
            CountriesQuestionGenerator(getCountriesForQuiz, mockk<GetCountriesForFlagQuizUseCase>()),
            validateAnswer,
            CalculateScoreUseCase(),
            QuizRandom(Random(1))
        )
        val countryRepository = mockk<CountryRepository>()
        every { countryRepository.getAllCountries() } returns flowOf(listOf(target, germany, france, spain))

        val outlineRepository = newMapRepository()
        runBlocking { outlineRepository.layer(GeoLayerId.ADMIN0_50M) }
        val getCountryOutline = GetCountryOutlineUseCase(outlineRepository, Dispatchers.Unconfined)

        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.showTimer } returns flowOf(true)
        every { settingsRepository.vibration } returns flowOf(true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
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

    /** Awaits the current silhouette's outline paths so [SilhouetteOutlineBox] never shows a blank frame. */
    private fun warmOutlinePaths(viewModel: SilhouettesGameViewModel) {
        val state = (viewModel.uiState.value as SilhouettesUiState.Active).state
        val outline = requireNotNull(state.outline) { "no outline built for the silhouettes screenshot" }
        runBlocking { CountryOutlinePathCache.Shared.paths(outline, state.showNeighbours).await() }
    }

    /**
     * Normal, typed: Italy's real outline, lives 2 of 3 after one wrong country guess ("Germany",
     * an orange "guessed so far" chip) and the First letter hint used (shown as text).
     */
    fun silhouettesNormal(): @Composable () -> Unit {
        val viewModel = silhouettesViewModel(Difficulty.NORMAL)
        viewModel.onInputChange("Germany")
        viewModel.onSubmit()
        viewModel.onHint(HintType.FIRST_LETTER)
        warmOutlinePaths(viewModel)
        return { SilhouettesGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    /** Easy: four name options and the screen's own "Which country is this?" prompt, no typed field. */
    fun silhouettesEasy(): @Composable () -> Unit {
        val viewModel = silhouettesViewModel(Difficulty.EASY)
        warmOutlinePaths(viewModel)
        return { SilhouettesGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    /** The setup screen: difficulty, then the filters this mode offers. */
    fun silhouettesSetup(): @Composable () -> Unit {
        val countryRepository = mockk<CountryRepository>()
        every { countryRepository.getAllCountries() } returns flowOf(CategoryOptionsFixture.COUNTRIES)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
        val mode = SilhouettesGameMode(
            CountriesQuestionGenerator(
                mockk<GetCountriesForQuizUseCase>(relaxed = true),
                mockk<GetCountriesForFlagQuizUseCase>(relaxed = true)
            ),
            mockk<ValidateAnswerUseCase>(),
            CalculateScoreUseCase(),
            QuizRandom(Random(1))
        )
        val viewModel = SilhouettesSetupViewModel(
            countryRepository = countryRepository,
            optionsBuilder = CategoryOptionsFixture.builder(),
            settingsRepository = settingsRepository,
            silhouettesGameMode = mode
        )
        return { SilhouettesSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    // ---- Tap the map: Find (task 5.2) -------------------------------------------------------

    /** Normal, Europe: France (the prompt's target), three found, one missed, one still to come. */
    fun tapMapFind(): @Composable () -> Unit {
        val target = realCountry("FRA")
        val found = listOf("DEU", "AUT", "NLD").map(::realCountry)
        val missed = realCountry("ESP")
        val remaining = realCountry("ITA")
        val countries = listOf(target) + found + listOf(missed, remaining)
        val quiz = Quiz(category = QuizCategory.MapRegion(RegionPreset.EUROPE.id), countries = countries)
        val state = TapMapGameState(
            quizState = QuizState(
                quiz = quiz,
                answeredCountries = found.map { it.code }.toSet(),
                missedCountries = setOf(missed.code)
            ),
            currentTargetCode = target.code
        )
        val mapState = europeMapState()
        warmMapPaths(mapState, RegionPreset.EUROPE.projection)
        return tapMapFindContent(state, mapState, Difficulty.NORMAL, timerSeconds = 48)
    }

    /** Easy: the highlighted target (France), the subregion clue and four name options. */
    fun tapMapFindEasy(): @Composable () -> Unit {
        val target = realCountry("FRA")
        val germany = realCountry("DEU")
        val austria = realCountry("AUT")
        val spain = realCountry("ESP")
        val countries = listOf(target, germany, austria, spain)
        val quiz = Quiz(category = QuizCategory.MapRegion(RegionPreset.EUROPE.id), countries = countries)
        val choice = ChoiceQuestion(
            targetCode = target.code,
            options = listOf(target, germany, austria, spain).map { ChoiceOption(it.code, it.name) },
            prompt = ChoicePrompt.InSet("")
        )
        val state = TapMapGameState(quizState = QuizState(quiz = quiz, choice = choice))
        val mapState = europeMapState()
        warmMapPaths(mapState, RegionPreset.EUROPE.projection)
        return tapMapFindContent(state, mapState, Difficulty.EASY, timerSeconds = 0, timerVisible = false)
    }

    private fun tapMapFindContent(
        state: TapMapGameState,
        mapState: TapMapMapState,
        difficulty: Difficulty,
        timerSeconds: Int,
        timerVisible: Boolean = true
    ): @Composable () -> Unit = {
        // Surface, like the real screen's Scaffold content: gives text the theme's content colour.
        Surface(Modifier.fillMaxSize()) {
            TapMapActiveContent(
                state = state,
                mapState = mapState,
                difficulty = difficulty,
                timerVisible = timerVisible,
                timerSeconds = timerSeconds,
                viewMode = MapViewMode.Map,
                onFeatureTap = {},
                onListSelect = {},
                onChoiceSelected = {},
                onSkip = {},
                onHint = {},
                onGiveUp = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // ---- Tap the map: Name all (task 5.2b, D40) ---------------------------------------------

    /** Hard, Europe: several found (recent-answer chips) and one strike (1 of 3). */
    fun tapMapNameAll(): @Composable () -> Unit {
        val codes = listOf("FRA", "DEU", "AUT", "NLD", "ESP", "ITA", "POL", "PRT")
        val countries = codes.map(::realCountry)
        val quiz = Quiz(category = QuizCategory.MapRegion(RegionPreset.EUROPE.id), countries = countries)
        val state = QuizState(
            quiz = quiz,
            answeredCountries = setOf("FRA", "DEU", "AUT", "NLD"),
            incorrectGuesses = 1,
            recentCorrect = listOf("NLD", "AUT", "DEU")
        )
        val mapState = europeMapState()
        warmMapPaths(mapState, RegionPreset.EUROPE.projection)
        return {
            Surface(Modifier.fillMaxSize()) {
                TapMapNameAllActiveContent(
                    state = state,
                    mapState = mapState,
                    difficulty = Difficulty.HARD,
                    timerVisible = true,
                    timerSeconds = 83,
                    viewMode = MapViewMode.Map,
                    onInputChange = {},
                    onSubmit = {},
                    onGiveUp = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // ---- Tap the map: setup screen ----------------------------------------------------------

    private fun tapMapSetupViewModel(): TapMapSetupViewModel = TapMapSetupViewModel(
        savedStateHandle = SavedStateHandle(),
        settingsRepository = ScreenTestFixtures.settingsRepository(difficulty = Difficulty.NORMAL)
    )

    /** Find selected (the default). */
    fun tapMapSetup(): @Composable () -> Unit {
        val viewModel = tapMapSetupViewModel()
        return { TapMapSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    /** Name all selected: Easy hidden from the difficulty row (D40). */
    fun tapMapSetupNameAll(): @Composable () -> Unit {
        val viewModel = tapMapSetupViewModel()
        viewModel.onVariantSelected(TapMapGameMode.VARIANT_NAME_ALL)
        return { TapMapSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    // ---- Results, a new mode (D35: no Challenge button) ------------------------------------

    /** A Country silhouettes result: mode label "Country silhouettes", no Challenge button. */
    fun resultsSilhouettes(): @Composable () -> Unit {
        val result = TestQuizData.completedQuiz(
            id = "result-silhouettes",
            quizModeId = SilhouettesGameMode.MODE_ID,
            countryCodes = listOf("ITA", "FRA", "DEU"),
            answeredCodes = listOf("ITA", "FRA")
        )
        val viewModel = ScreenTestFixtures.resultsViewModel(
            result = result,
            extraModes = setOf(TestGameModes.silhouettes())
        )
        return {
            ResultsScreen(
                onPlayAgain = { _ -> },
                onGoHome = {},
                onViewAnswers = {},
                onPractiseMissed = { _ -> },
                viewModel = viewModel
            )
        }
    }
}
