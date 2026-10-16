package com.geoquiz.app.ui.screenshot

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.data.flagspeed.FlagLookalikeRepository
import com.geoquiz.app.data.geo.AndroidGeoAssetSource
import com.geoquiz.app.data.geo.AssetMapRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.physicalfeatures.AndroidPhysicalFeatureAssetSource
import com.geoquiz.app.data.physicalfeatures.AssetPhysicalFeatureRepository
import com.geoquiz.app.data.physicalfeatures.PhysicalFeatureRepository
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.currencies.CurrencyData
import com.geoquiz.app.domain.mode.currencies.CurrencyEntry
import com.geoquiz.app.domain.mode.currencies.CurrencyGameMode
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeSet
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeVisibility
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedGameMode
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedQuestionGenerator
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeature
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureAnswerValidator
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureFilter
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureTab
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureType
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesCategory
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesGameMode
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.category.CategoryOptionsFixture
import com.geoquiz.app.ui.currencies.CurrenciesGameScreen
import com.geoquiz.app.ui.currencies.CurrenciesGameViewModel
import com.geoquiz.app.ui.currencies.CurrenciesSetupScreen
import com.geoquiz.app.ui.currencies.CurrenciesSetupViewModel
import com.geoquiz.app.ui.flagspeed.FlagSpeedGameScreen
import com.geoquiz.app.ui.flagspeed.FlagSpeedGameViewModel
import com.geoquiz.app.ui.flagspeed.FlagSpeedRoundState
import com.geoquiz.app.ui.flagspeed.FlagSpeedSavedState
import com.geoquiz.app.ui.flagspeed.FlagSpeedSetupScreen
import com.geoquiz.app.ui.flagspeed.FlagSpeedSetupViewModel
import com.geoquiz.app.ui.physicalfeatures.PhysicalFeaturesGameScreen
import com.geoquiz.app.ui.physicalfeatures.PhysicalFeaturesGameViewModel
import com.geoquiz.app.ui.physicalfeatures.PhysicalFeaturesRoundState
import com.geoquiz.app.ui.physicalfeatures.PhysicalFeaturesRouteArgs
import com.geoquiz.app.ui.physicalfeatures.PhysicalFeaturesSavedState
import com.geoquiz.app.ui.physicalfeatures.PhysicalFeaturesSetupScreen
import com.geoquiz.app.ui.physicalfeatures.PhysicalFeaturesSetupViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import androidx.compose.runtime.Composable

/**
 * The screens task 7.4's screenshot tests capture for Phase 7's three modes: Currencies (7.1),
 * Rivers, mountains and lakes (7.2) and Flag speed round (7.3). Built the same way as
 * [NewModesBScreenshotScreens]: fixed state, no Hilt, deterministic clocks/dispatchers/random.
 *
 * Currencies and Flag speed round's ViewModels never schedule a delayed auto-advance you'd need a
 * controllable dispatcher to drive (Currencies waits for an explicit "Next" tap; Flag speed round
 * does schedule one, 900 ms after a pick, but every mid-round golden here is built from a fixture
 * already past that point, via [FlagSpeedSavedState]'s own restore contract instead of actually
 * driving the delay - the same real code path a process death or "Don't keep activities" exercises
 * in the live app), so every fixture below runs eagerly on [com.geoquiz.app.testutil.MainDispatcherRule]'s
 * unconfined test dispatcher, exactly like [NewModesBScreenshotScreens]'s border hop fixtures - no
 * `runCurrent()`/`advanceTimeBy()` needed anywhere in this file.
 *
 * Flag images: a shared currency's choice question shows up to four [com.geoquiz.app.ui.components.FlagImage]s
 * at once (one per option), and Flag speed round's post-round review cards show two to four
 * flags per lookalike set - both are exactly the "several real flags in one capture" shape that a
 * previous session found non-deterministic under Robolectric (session 9, Border hop's Spain
 * flag). Every fixture in this file that can show more than one flag at once uses synthetic
 * country codes (`AAA`, `BBB`, ...) with no bundled SVG, so [com.geoquiz.app.ui.components.FlagImage]
 * always falls back to the same placeholder painter regardless of timing.
 *
 * Rivers, mountains and lakes is the one mode in this wave with no prior screenshot coverage at
 * all of its map rendering: the game screen's `overlay`/`coarse` scene (added in a code-review fix
 * round from code reading alone, see `docs/UPGRADE_PLAN.md` session log) is driven here through
 * the real [PhysicalFeaturesGameViewModel] over the real bundled `physical_features.json` and the
 * real `admin0_50m`/`rivers_50m`/`regions_50m`/`lakes_50m`/`geo_points_50m` layers (a real
 * [AssetMapRepository] and [AssetPhysicalFeatureRepository], not fakes) - the first genuine visual
 * check of that fix. A round is seeded directly via [PhysicalFeaturesSavedState]'s own restore
 * contract (one real feature, `currentIndex` 0) rather than letting
 * [com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesRound]'s own random 15-item draw
 * decide which feature shows, so every golden here is pinned to one specific, well-known feature
 * per geometry kind. Both the feature repository and the map repository are pre-warmed with
 * `runBlocking` before the ViewModel is constructed (mirroring
 * [NewModesBScreenshotScreens.warmUsCaMapPaths]'s reasoning exactly): both real repositories cache
 * by a plain in-memory check with no dispatcher hop once warm, so the ViewModel's own `init` call
 * to the same already-warm instance never actually suspends onto a real IO dispatcher mid-composition.
 */
object NewModesCScreenshotScreens {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // ======================================================================================
    // Currencies (task 7.1)
    // ======================================================================================

    // Synthetic countries (task 7.1 review: a shared currency's choice question shows one flag
    // per option, so every code here has no bundled SVG - see the class doc).
    private val cyDelta = TestQuizData.country("DDD", "Delta", "Delta City", region = "Testland")
    private val cyAlpha = TestQuizData.country("AAA", "Alpha", "Alpha City", region = "Testland")
    private val cyBeta = TestQuizData.country("BBB", "Beta", "Beta City", region = "Testland")
    private val cyGamma = TestQuizData.country("CCC", "Gamma", "Gamma City", region = "Testland")
    private val cyEpsilon = TestQuizData.country("EEE", "Epsilon", "Epsilon City", region = "Testland")
    private val cyZeta = TestQuizData.country("FFF", "Zeta", "Zeta City", region = "Testland")
    private val cyEta = TestQuizData.country("GGG", "Eta", "Eta City", region = "Testland")

    /** Round order: Delta dollar (typed) first, then the Credit (shared, always a choice). */
    private val cyRoundPool = listOf(cyDelta, cyAlpha, cyBeta, cyGamma, cyEpsilon, cyZeta, cyEta)

    private val cyCredit = CurrencyEntry(
        code = "XCU",
        name = "Credit",
        symbol = "₢",
        countries = listOf("AAA", "BBB", "CCC"),
        shared = true,
        fact = "Adopted by three neighbouring countries in a 1999 monetary union."
    )
    private val cyDeltaDollar = CurrencyEntry(
        code = "DDZ",
        name = "Delta dollar",
        symbol = "D$",
        countries = listOf("DDD"),
        shared = false,
        fact = "Pegged to a neighbouring currency since 1960."
    )
    private val cyData = CurrencyData(
        entries = listOf(cyDeltaDollar, cyCredit),
        primaryCurrency = mapOf("DDD" to "DDZ")
    )

    private fun currenciesCountryRepository(): CountryRepository =
        mockk { every { getAllCountries() } returns flowOf(cyRoundPool) }

    private fun currenciesCurrencyRepository(): CurrencyRepository = mockk {
        coEvery { currencies() } returns cyData
        every { cachedOrNull() } returns cyData
    }

    /** Correct only for "Delta" (case-insensitive); everything else is incorrect - just enough
     * for the typed item's happy path, the only one any golden here exercises. */
    private fun currenciesValidateAnswer(): ValidateAnswerUseCase {
        val validate = mockk<ValidateAnswerUseCase>()
        coEvery { validate(any(), any(), any()) } answers {
            if (firstArg<String>().trim().equals("Delta", ignoreCase = true)) {
                AnswerResult.Correct("Delta")
            } else {
                AnswerResult.Incorrect
            }
        }
        return validate
    }

    private fun currenciesMode(): CurrencyGameMode {
        val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
        coEvery { getCountriesForQuiz(any()) } returns cyRoundPool
        val getCountriesForFlagQuiz = mockk<GetCountriesForFlagQuizUseCase>()
        return CurrencyGameMode(
            CountriesQuestionGenerator(getCountriesForQuiz, getCountriesForFlagQuiz),
            currenciesCountryRepository(),
            currenciesCurrencyRepository(),
            currenciesValidateAnswer(),
            CalculateScoreUseCase()
        )
    }

    private fun currenciesCompleteQuiz(mode: CurrencyGameMode): CompleteQuizUseCase = CompleteQuizUseCase(
        gameModes = GameModeRegistry(setOf(mode)),
        completedQuizRepository = FakeCompletedQuizRepository(),
        savedQuizRepository = mockk(relaxed = true),
        achievementRepository = mockk(relaxed = true),
        quizHistoryRepository = mockk(relaxed = true),
        playGamesService = mockk(relaxed = true)
    )

    private fun currenciesViewModel(difficulty: Difficulty): CurrenciesGameViewModel {
        val mode = currenciesMode()
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.showTimer } returns flowOf(true)
        every { settingsRepository.vibration } returns flowOf(true)
        every { settingsRepository.difficulty } returns flowOf(difficulty)
        val handle = SavedStateHandle(
            mapOf(
                "categoryType" to "all",
                "categoryValue" to "_",
                CurrenciesGameViewModel.ARG_DIFFICULTY to difficulty.id
            )
        )
        return CurrenciesGameViewModel(
            savedStateHandle = handle,
            currencyGameMode = mode,
            countryRepository = currenciesCountryRepository(),
            completeQuiz = currenciesCompleteQuiz(mode),
            settingsRepository = settingsRepository,
            clock = MonotonicClock { 0L },
            quizRandom = QuizRandom(Random(1))
        )
    }

    /** Item 0 (Delta dollar), not shared: a typed question, untouched. */
    fun currenciesTypedQuestion(): @Composable () -> Unit {
        val vm = currenciesViewModel(Difficulty.NORMAL)
        return { CurrenciesGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = vm) }
    }

    /** Item 0 answered correctly, Next not yet tapped: the fact banner plus the Next button. */
    fun currenciesFactBanner(): @Composable () -> Unit {
        val vm = currenciesViewModel(Difficulty.NORMAL)
        vm.onInputChange("Delta")
        vm.onSubmit()
        return { CurrenciesGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = vm) }
    }

    /** Item 1: the Credit, shared across Alpha/Beta/Gamma - always a four-option choice (D50),
     * confirming the currency-specific "Which of these uses the Credit...?" wording (task 7.1
     * review's #2 finding) rather than the generic "which country uses this currency?" text. */
    fun currenciesSharedChoiceQuestion(difficulty: Difficulty = Difficulty.NORMAL): @Composable () -> Unit {
        val vm = currenciesViewModel(difficulty)
        vm.onInputChange("Delta")
        vm.onSubmit()
        vm.onNext()
        return { CurrenciesGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = vm) }
    }

    // ---- Currencies: setup screen (real CategoryOptionsFixture countries, no flags shown) ----

    private val cySetupCurrencyData = CurrencyData(
        entries = listOf(
            CurrencyEntry("EUR", "Euro", "€", listOf("DEU", "FRA"), shared = true, fact = "Introduced in 1999."),
            CurrencyEntry("AUD", "Australian dollar", "$", listOf("AUS", "NZL"), shared = true, fact = null),
            CurrencyEntry("GBP", "Pound sterling", "£", listOf("GBR"), shared = false, fact = null),
            CurrencyEntry("ISK", "Icelandic krona", "kr", listOf("ISL"), shared = false, fact = null),
            CurrencyEntry("KZT", "Kazakhstani tenge", "₸", listOf("KAZ"), shared = false, fact = null),
            CurrencyEntry("PHP", "Philippine peso", "₱", listOf("PHL"), shared = false, fact = null),
            CurrencyEntry("MOP", "Macanese pataca", "MOP$", listOf("MAC"), shared = false, fact = null),
            CurrencyEntry("PGK", "Papua New Guinean kina", "K", listOf("PNG"), shared = false, fact = null),
            CurrencyEntry("SBD", "Solomon Islands dollar", "$", listOf("SLB"), shared = false, fact = null),
            CurrencyEntry("ZAR", "South African rand", "R", listOf("ZAF"), shared = false, fact = null),
            CurrencyEntry("MZN", "Mozambican metical", "MT", listOf("MOZ"), shared = false, fact = null),
            CurrencyEntry("XAF", "Central African CFA franc", "FCFA", listOf("TCD"), shared = false, fact = null),
            CurrencyEntry("DJF", "Djiboutian franc", "Fdj", listOf("DJI"), shared = false, fact = null),
            CurrencyEntry("MXN", "Mexican peso", "$", listOf("MEX"), shared = false, fact = null),
            CurrencyEntry("ARS", "Argentine peso", "$", listOf("ARG"), shared = false, fact = null)
        ),
        primaryCurrency = mapOf(
            "DEU" to "EUR", "FRA" to "EUR", "AUS" to "AUD", "NZL" to "AUD",
            "GBR" to "GBP", "ISL" to "ISK", "KAZ" to "KZT", "PHL" to "PHP",
            "MAC" to "MOP", "PNG" to "PGK", "SLB" to "SBD", "ZAF" to "ZAR",
            "MOZ" to "MZN", "TCD" to "XAF", "DJI" to "DJF", "MEX" to "MXN", "ARG" to "ARS"
        )
    )

    private fun currenciesSetupViewModel(): CurrenciesSetupViewModel {
        val countries = CategoryOptionsFixture.COUNTRIES
        val countryRepository = mockk<CountryRepository> { every { getAllCountries() } returns flowOf(countries) }
        val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
        coEvery { getCountriesForQuiz(any()) } answers {
            when (val category = firstArg<QuizCategory>()) {
                is QuizCategory.AllCountries -> countries
                is QuizCategory.ByRegion -> countries.filter { it.region == category.region }
                is QuizCategory.BySubregion -> countries.filter { it.subregion == category.subregion }
                else -> emptyList()
            }
        }
        val getCountriesForFlagQuiz = mockk<GetCountriesForFlagQuizUseCase>()
        val currencyRepository = mockk<CurrencyRepository> {
            coEvery { currencies() } returns cySetupCurrencyData
            every { cachedOrNull() } returns cySetupCurrencyData
        }
        val mode = CurrencyGameMode(
            CountriesQuestionGenerator(getCountriesForQuiz, getCountriesForFlagQuiz),
            countryRepository,
            currencyRepository,
            mockk<ValidateAnswerUseCase>(relaxed = true),
            CalculateScoreUseCase()
        )
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
        return CurrenciesSetupViewModel(
            countryRepository = countryRepository,
            optionsBuilder = CategoryOptionsFixture.builder(),
            settingsRepository = settingsRepository,
            currencyGameMode = mode
        )
    }

    fun currenciesSetup(): @Composable () -> Unit {
        val viewModel = currenciesSetupViewModel()
        return { CurrenciesSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    // ======================================================================================
    // Rivers, mountains and lakes (task 7.2)
    // ======================================================================================

    private fun newPhysicalFeatureRepository(): PhysicalFeatureRepository =
        AssetPhysicalFeatureRepository(AndroidPhysicalFeatureAssetSource(context().assets), Dispatchers.IO)

    private fun newPfMapRepository(): AssetMapRepository =
        AssetMapRepository(AndroidGeoAssetSource(context().assets), Dispatchers.IO)

    /** Pre-warms [repository]'s cache so the ViewModel's own `features()` call returns
     * synchronously (see the class doc). */
    private fun warmedPhysicalFeatureRepository(): PhysicalFeatureRepository {
        val repository = newPhysicalFeatureRepository()
        runBlocking { repository.features() }
        return repository
    }

    /** Pre-warms [mapRepository]'s cache for [id] (see the class doc). */
    private fun warmLayer(mapRepository: MapRepository, id: GeoLayerId) {
        runBlocking { mapRepository.layer(id) }
    }

    private fun tabFor(type: PhysicalFeatureType): PhysicalFeatureTab = when (type) {
        PhysicalFeatureType.RIVER -> PhysicalFeatureTab.RIVERS
        PhysicalFeatureType.RANGE, PhysicalFeatureType.PEAK -> PhysicalFeatureTab.MOUNTAINS
        PhysicalFeatureType.LAKE -> PhysicalFeatureTab.LAKES
    }

    private fun physicalFeaturesViewModel(
        feature: PhysicalFeature,
        difficulty: Difficulty,
        featureRepository: PhysicalFeatureRepository,
        mapRepository: MapRepository
    ): PhysicalFeaturesGameViewModel {
        warmLayer(mapRepository, feature.layerId)
        warmLayer(mapRepository, GeoLayerId.ADMIN0_50M)

        val category = PhysicalFeaturesCategory.toQuizCategory(PhysicalFeatureFilter(tabFor(feature.type)))
        val handle = SavedStateHandle(
            mapOf(
                PhysicalFeaturesRouteArgs.ARG_CATEGORY_TYPE to category.typeKey,
                PhysicalFeaturesRouteArgs.ARG_CATEGORY_VALUE to category.valueKey,
                PhysicalFeaturesRouteArgs.ARG_DIFFICULTY to difficulty.id
            )
        )
        val seedState = PhysicalFeaturesRoundState(
            filter = PhysicalFeatureFilter(tabFor(feature.type)),
            difficulty = difficulty,
            features = listOf(feature),
            isComplete = false
        )
        PhysicalFeaturesSavedState(handle).save(seedState, elapsedMillis = 12_000L)

        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.showTimer } returns flowOf(true)
        every { settingsRepository.vibration } returns flowOf(true)
        every { settingsRepository.difficulty } returns flowOf(difficulty)

        val mode = PhysicalFeaturesGameMode(CalculateScoreUseCase())
        val completeQuiz = CompleteQuizUseCase(
            gameModes = GameModeRegistry(setOf(mode)),
            completedQuizRepository = FakeCompletedQuizRepository(),
            savedQuizRepository = mockk(relaxed = true),
            achievementRepository = mockk(relaxed = true),
            quizHistoryRepository = mockk(relaxed = true),
            playGamesService = mockk(relaxed = true)
        )

        val vm = PhysicalFeaturesGameViewModel(
            savedStateHandle = handle,
            physicalFeatureRepository = featureRepository,
            mapRepository = mapRepository,
            answerValidator = PhysicalFeatureAnswerValidator(NormalizeInputUseCase()),
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            clock = MonotonicClock { 0L },
            quizRandom = QuizRandom(Random(1))
        )
        // A restored, unfinished round comes back paused (D37): resume it so the golden shows
        // the live round, not the paused overlay.
        vm.onResume()
        return vm
    }

    private fun physicalFeaturesGolden(featureName: String, difficulty: Difficulty = Difficulty.NORMAL): @Composable () -> Unit {
        val featureRepository = warmedPhysicalFeatureRepository()
        val mapRepository = newPfMapRepository()
        val feature = runBlocking { featureRepository.features() }.first { it.name == featureName }
        val vm = physicalFeaturesViewModel(feature, difficulty, featureRepository, mapRepository)
        return { PhysicalFeaturesGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = vm) }
    }

    /** Rivers tab: a LINE-geometry feature (`rivers_50m`) - the one that had the "filled as a
     * blob" bug the overlay/no-fill-for-LINE review fix addressed. */
    fun physicalFeaturesRivers(): @Composable () -> Unit = physicalFeaturesGolden("Amazon")

    /** Mountains tab, a range: POLYGON geometry (`regions_50m`) - the one that had the "painted
     * over by overlapping regions" bug. */
    fun physicalFeaturesMountainsRange(): @Composable () -> Unit = physicalFeaturesGolden("Andes")

    /** Mountains tab, a peak: POINT geometry (`geo_points_50m`) - the fourth code path. */
    fun physicalFeaturesMountainsPeak(): @Composable () -> Unit = physicalFeaturesGolden("Mount Everest")

    /** Lakes tab: POLYGON geometry (`lakes_50m`) - the one closest to correct before the fix. */
    fun physicalFeaturesLakes(): @Composable () -> Unit = physicalFeaturesGolden("Lake Baikal")

    // ---- Rivers, mountains and lakes: setup screen (tab picker, no map) ----------------------

    private fun physicalFeaturesSetupViewModel(): PhysicalFeaturesSetupViewModel {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
        return PhysicalFeaturesSetupViewModel(
            savedStateHandle = SavedStateHandle(),
            settingsRepository = settingsRepository,
            physicalFeatureRepository = warmedPhysicalFeatureRepository()
        )
    }

    fun physicalFeaturesSetup(): @Composable () -> Unit {
        val viewModel = physicalFeaturesSetupViewModel()
        return { PhysicalFeaturesSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    // ======================================================================================
    // Flag speed round (task 7.3)
    // ======================================================================================

    // Synthetic countries (task 7.3: the post-round review's cards show 2-4 flags at once per
    // lookalike set - see the class doc).
    private val fsCodeToName = listOf(
        "AAA" to "Alpha", "BBB" to "Beta", "CCC" to "Gamma", "DDD" to "Delta",
        "EEE" to "Epsilon", "FFF" to "Zeta", "GGG" to "Eta", "HHH" to "Theta"
    )
    private val fsCountries = fsCodeToName.map { (code, name) ->
        TestQuizData.country(code, name, "Capital $code", region = "Testland")
    }

    private val fsSetAb = FlagLookalikeSet(
        id = "ab",
        countries = listOf("AAA", "BBB"),
        tier = 1,
        visibility = FlagLookalikeVisibility.CLEAR,
        explanation = "Alpha and Beta both use a plain two-colour layout, differing only in a small emblem many players miss at a glance.",
        cues = emptyMap()
    )
    private val fsSetCd = FlagLookalikeSet(
        id = "cd",
        countries = listOf("CCC", "DDD", "EEE", "FFF"),
        tier = 2,
        visibility = FlagLookalikeVisibility.SUBTLE,
        explanation = "Gamma, Delta, Epsilon and Zeta share a similar tricolour arrangement, differing only in a thin central band.",
        cues = emptyMap()
    )
    private val fsSetGh = FlagLookalikeSet(
        id = "gh",
        countries = listOf("GGG", "HHH"),
        tier = 1,
        visibility = FlagLookalikeVisibility.NEAR_IDENTICAL,
        explanation = "Eta and Theta are practically identical at this app's fixed flag size, even though the real flags differ in proportion.",
        cues = emptyMap()
    )
    private val fsAllSets = listOf(fsSetAb, fsSetCd, fsSetGh)

    private fun flagSpeedCountryRepository(): CountryRepository =
        mockk { every { getAllCountries() } returns flowOf(fsCountries) }

    private fun flagSpeedLookalikeRepository(): FlagLookalikeRepository = object : FlagLookalikeRepository {
        override suspend fun sets(): List<FlagLookalikeSet> = fsAllSets
    }

    private fun flagSpeedCompleteQuiz(): CompleteQuizUseCase {
        val mode = FlagSpeedGameMode(FlagSpeedQuestionGenerator(flagSpeedLookalikeRepository(), flagSpeedCountryRepository()))
        return CompleteQuizUseCase(
            gameModes = GameModeRegistry(setOf(mode)),
            completedQuizRepository = FakeCompletedQuizRepository(),
            savedQuizRepository = mockk(relaxed = true),
            achievementRepository = mockk(relaxed = true),
            quizHistoryRepository = mockk(relaxed = true),
            playGamesService = mockk(relaxed = true)
        )
    }

    private fun flagSpeedSettingsRepository(difficulty: Difficulty): SettingsRepository {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.vibration } returns flowOf(true)
        every { settingsRepository.difficulty } returns flowOf(difficulty)
        return settingsRepository
    }

    fun flagSpeedSetup(): @Composable () -> Unit {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
        val viewModel = FlagSpeedSetupViewModel(settingsRepository)
        return { FlagSpeedSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    /**
     * Mid-round: three correct answers banked back to back (live combo 3), a fourth question
     * open (the tier-2 "cd" set, target Delta), about 37 s left on the clock. Seeded via
     * [FlagSpeedSavedState]'s own restore contract (the real process-death path, see the class
     * doc) rather than driving the real 900 ms feedback delay between picks.
     */
    fun flagSpeedMidRound(): @Composable () -> Unit {
        val handle = SavedStateHandle(mapOf(FlagSpeedGameViewModel.ARG_DIFFICULTY to Difficulty.NORMAL.id))
        val seedState = FlagSpeedRoundState(
            difficulty = Difficulty.NORMAL,
            askedCodes = listOf("AAA", "BBB", "CCC"),
            correctCodes = setOf("AAA", "BBB", "CCC"),
            liveCombo = 3,
            longestCombo = 3,
            encounteredSetIds = setOf("ab", "cd"),
            lastDrawSetId = "cd",
            lastDrawTargetCode = "DDD",
            isComplete = false
        )
        FlagSpeedSavedState(handle).save(seedState, elapsedMillis = 23_000L)
        val vm = FlagSpeedGameViewModel(
            savedStateHandle = handle,
            countryRepository = flagSpeedCountryRepository(),
            lookalikeRepository = flagSpeedLookalikeRepository(),
            completeQuiz = flagSpeedCompleteQuiz(),
            settingsRepository = flagSpeedSettingsRepository(Difficulty.NORMAL),
            clock = MonotonicClock { 0L },
            quizRandom = QuizRandom(Random(1))
        )
        // A restored, unfinished round comes back paused (D37): resume it for the live golden.
        vm.onResume()
        return { FlagSpeedGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = vm) }
    }

    /**
     * The post-round review: the round ended (gave up with one wrong pick along the way), both
     * encountered sets' explanations shown plus the near-identical "did you know" card.
     */
    fun flagSpeedReview(): @Composable () -> Unit {
        val handle = SavedStateHandle(mapOf(FlagSpeedGameViewModel.ARG_DIFFICULTY to Difficulty.NORMAL.id))
        val seedState = FlagSpeedRoundState(
            difficulty = Difficulty.NORMAL,
            askedCodes = listOf("AAA", "BBB", "CCC", "DDD"),
            correctCodes = setOf("AAA", "BBB", "DDD"),
            incorrectGuesses = 1,
            incorrectGuessStrings = listOf("Gamma"),
            liveCombo = 1,
            longestCombo = 2,
            encounteredSetIds = setOf("ab", "cd"),
            isComplete = true,
            gaveUp = true
        )
        FlagSpeedSavedState(handle).save(seedState, elapsedMillis = 41_000L)
        val vm = FlagSpeedGameViewModel(
            savedStateHandle = handle,
            countryRepository = flagSpeedCountryRepository(),
            lookalikeRepository = flagSpeedLookalikeRepository(),
            completeQuiz = flagSpeedCompleteQuiz(),
            settingsRepository = flagSpeedSettingsRepository(Difficulty.NORMAL),
            clock = MonotonicClock { 0L },
            quizRandom = QuizRandom(Random(1))
        )
        return { FlagSpeedGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = vm) }
    }
}
