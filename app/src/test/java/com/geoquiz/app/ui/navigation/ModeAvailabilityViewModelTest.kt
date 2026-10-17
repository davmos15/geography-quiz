package com.geoquiz.app.ui.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.classic.CapitalsGameMode
import com.geoquiz.app.domain.mode.classic.CountriesGameMode
import com.geoquiz.app.domain.mode.classic.FlagsGameMode
import com.geoquiz.app.domain.mode.currencies.CurrencyGameMode
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedGameMode
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesGameMode
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [ModeAvailabilityViewModel] (task 9.2): with every flag at its default (all on for the release
 * candidate, D63) every registered mode is reachable; a mode whose flag is turned off is not.
 * The registry holds the real production specs (keep [ALL_SPECS] in step with
 * `di/GameModeModule.kt`); only their behaviour is stubbed, which this ViewModel never calls.
 */
class ModeAvailabilityViewModelTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var flags: FeatureFlagRepository

    private val registry = GameModeRegistry(ALL_SPECS.map(::stubMode).toSet())

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "feature_flags.preferences_pb")
        }
        flags = FeatureFlagRepository(dataStore, overridesAllowed = true)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `every registered mode is available with all flags on`() = runTest {
        allFlagsOn()
        val viewModel = ModeAvailabilityViewModel(registry, flags)

        val available = viewModel.availableModeIds.first { it != null }

        assertEquals(ALL_SPECS.map { it.id }.toSet(), available)
    }

    @Test
    fun `a mode whose flag is off is not available, the others still are`() = runTest {
        allFlagsOn()
        flags.setOverride(FeatureFlag.BORDER_HOP, false)
        val viewModel = ModeAvailabilityViewModel(registry, flags)

        val available = viewModel.availableModeIds.first { it != null }!!

        assertFalse(BorderHopGameMode.SPEC.id in available)
        assertEquals(ALL_SPECS.map { it.id }.toSet() - BorderHopGameMode.SPEC.id, available)
    }

    @Test
    fun `turning the daily challenge flag off hides only the daily challenge`() = runTest {
        allFlagsOn()
        flags.setOverride(FeatureFlag.DAILY_CHALLENGE, false)
        val viewModel = ModeAvailabilityViewModel(registry, flags)

        val available = viewModel.availableModeIds.first { it != null }!!

        assertFalse(DailyChallengeGameMode.MODE_ID in available)
        assertTrue(CountriesGameMode.SPEC.id in available)
    }

    /**
     * Overrides every flag on, so these tests don't depend on the defaults: D63 has them all on
     * for the RC, but Dav may turn some back off before shipping.
     */
    private suspend fun allFlagsOn() = FeatureFlag.entries.forEach { flags.setOverride(it, true) }

    private companion object {
        /** Every production spec, as bound in `di/GameModeModule.kt`. */
        val ALL_SPECS: List<GameModeSpec> = listOf(
            CountriesGameMode.SPEC,
            CapitalsGameMode.SPEC,
            FlagsGameMode.SPEC,
            SilhouettesGameMode.SPEC,
            TapMapGameMode.SPEC,
            BorderHopGameMode.SPEC,
            UsCaRegionsGameMode.SPEC,
            CurrencyGameMode.SPEC,
            PhysicalFeaturesGameMode.SPEC,
            FlagSpeedGameMode.SPEC,
            DailyChallengeGameMode.SPEC
        )

        fun stubMode(modeSpec: GameModeSpec): GameMode = object : GameMode {
            override val spec = modeSpec
            override val generator = mockk<QuestionGenerator>()
            override val validator = mockk<AnswerValidator>()
            override val scoring = mockk<ScoringRule>()
            override val choiceGenerator = mockk<ChoiceQuestionGenerator>()
        }
    }
}
