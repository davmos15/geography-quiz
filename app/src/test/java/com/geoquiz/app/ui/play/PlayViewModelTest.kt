package com.geoquiz.app.ui.play

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestGameModes
import io.mockk.coVerify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val silhouettes = TestGameModes.flaggedMode("silhouettes", FeatureFlag.COUNTRY_SILHOUETTES, sortOrder = 20)
    private val tapTheMap = TestGameModes.flaggedMode("tap_the_map", FeatureFlag.TAP_THE_MAP, sortOrder = 10, icon = ModeIcon.FLAG)

    private fun flagStates(vararg enabled: FeatureFlag): List<FeatureFlagState> =
        FeatureFlag.entries.map { FeatureFlagState(it, enabled = it in enabled, overridden = it in enabled) }

    /** Skips states until one is loaded (group tiles built). */
    private suspend fun ReceiveTurbine<PlayUiState>.awaitLoaded(): PlayUiState {
        while (true) {
            val state = awaitItem()
            if (!state.isLoading && state.classicModes.isNotEmpty()) return state
        }
    }

    @Test
    fun `classic modes come from the registry in sort order with their labels and icons`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel()

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(listOf("countries", "capitals", "flags"), state.classicModes.map { it.id })
            assertEquals(
                listOf(R.string.mode_countries_name, R.string.mode_capitals_name, R.string.mode_flags_name),
                state.classicModes.map { it.label }
            )
            assertEquals(listOf(ModeIcon.GLOBE, ModeIcon.LANDMARK, ModeIcon.FLAG), state.classicModes.map { it.icon })
            val registryOrder = TestGameModes.registry().classic.sortedBy { it.spec.sortOrder }.map { it.id }
            assertEquals(registryOrder, state.classicModes.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new modes are empty by default even when flagged modes are registered`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(extraModes = setOf(silhouettes, tapTheMap))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(emptyList<ModeOption>(), state.newModes)
            assertEquals(3, state.classicModes.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new modes follow the feature flags in sort order and never include classic modes`() = runTest {
        val flags = MutableStateFlow(flagStates())
        val viewModel = ScreenTestFixtures.playViewModel(
            flagStates = flags,
            extraModes = setOf(silhouettes, tapTheMap)
        )

        viewModel.uiState.test {
            assertEquals(emptyList<ModeOption>(), awaitLoaded().newModes)

            flags.value = flagStates(FeatureFlag.COUNTRY_SILHOUETTES)
            assertEquals(listOf("silhouettes"), awaitItem().newModes.map { it.id })

            flags.value = flagStates(FeatureFlag.COUNTRY_SILHOUETTES, FeatureFlag.TAP_THE_MAP)
            val both = awaitItem()
            assertEquals(listOf("tap_the_map", "silhouettes"), both.newModes.map { it.id })
            assertEquals(ModeOption("tap_the_map", R.string.mode_countries_name, ModeIcon.FLAG), both.newModes.first())
            assertEquals(listOf("countries", "capitals", "flags"), both.classicModes.map { it.id })

            flags.value = flagStates()
            assertEquals(emptyList<ModeOption>(), awaitItem().newModes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a released mode without a flag is always a new mode`() = runTest {
        val released = TestGameModes.flaggedMode("released", featureFlag = null, sortOrder = 50)
        val viewModel = ScreenTestFixtures.playViewModel(extraModes = setOf(released))

        viewModel.uiState.test {
            assertEquals(listOf("released"), awaitLoaded().newModes.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Countries is selected by default and selecting a mode is kept in the SavedStateHandle`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = ScreenTestFixtures.playViewModel(savedStateHandle = handle)

        viewModel.uiState.test {
            val initial = awaitLoaded()
            assertEquals("countries", initial.selectedModeId)
            assertEquals("countries", initial.selectedContent?.modeId)

            viewModel.selectMode("flags")
            val flags = awaitItem()
            assertEquals("flags", flags.selectedModeId)
            assertEquals("flags", flags.selectedContent?.modeId)
            assertEquals("flags", handle[PlayViewModel.KEY_SELECTED_MODE])
            cancelAndIgnoreRemainingEvents()
        }

        // A new ViewModel over the same handle (rotation or process death) restores the mode
        val restored = ScreenTestFixtures.playViewModel(savedStateHandle = handle)
        restored.uiState.test {
            assertEquals("flags",awaitLoaded().selectedModeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting an id that is not a classic mode is ignored`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = ScreenTestFixtures.playViewModel(
            savedStateHandle = handle,
            flagStates = MutableStateFlow(flagStates(FeatureFlag.TAP_THE_MAP)),
            extraModes = setOf(tapTheMap)
        )

        viewModel.selectMode("tap_the_map")
        viewModel.selectMode("nonsense")

        viewModel.uiState.test {
            assertEquals("countries", awaitLoaded().selectedModeId)
            cancelAndIgnoreRemainingEvents()
        }
        // getStateFlow stores the default; the ignored ids never replace it
        assertEquals("countries", handle.get<String>(PlayViewModel.KEY_SELECTED_MODE))
    }

    @Test
    fun `an unknown stored mode falls back to the first classic mode`() = runTest {
        val handle = SavedStateHandle(mapOf(PlayViewModel.KEY_SELECTED_MODE to "gone"))
        val viewModel = ScreenTestFixtures.playViewModel(savedStateHandle = handle)

        viewModel.uiState.test {
            assertEquals("countries", awaitLoaded().selectedModeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `group tiles are built for every classic mode`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel()

        viewModel.uiState.test {
            val content = awaitLoaded().modeContent!!
            assertEquals(setOf("countries", "capitals", "flags"), content.keys)
            assertEquals(ScreenTestFixtures.EIGHT.size, content.getValue("countries").allItems?.count)
            assertEquals(R.string.play_all_capitals, content.getValue("capitals").allItems?.title)
            assertNull(content.getValue("flags").allItems)
            assertTrue(content.getValue("countries").groups.any { it.id == "letter_patterns" })
            assertTrue(content.getValue("flags").groups.any { it.id == "flag_single_color" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the resume card keeps the saved quiz's mode and answered count`() = runTest {
        val saved = ScreenTestFixtures.SAVED_QUIZ.copy(quizMode = "capitals")
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = saved)

        viewModel.uiState.test {
            var state = awaitLoaded()
            while (state.savedQuiz == null) state = awaitItem()
            val info = state.savedQuiz!!
            assertEquals("capitals", info.quizModeId)
            assertEquals(R.string.mode_capitals_name, info.modeLabel)
            assertEquals("startletter", info.categoryType)
            assertEquals("A", info.categoryValue)
            assertEquals(2, info.answeredCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no saved quiz means no resume card`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = null)

        viewModel.uiState.test {
            assertNull(awaitLoaded().savedQuiz)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the saved quiz clears it`() = runTest {
        val repository = ScreenTestFixtures.savedQuizRepository()
        val viewModel = ScreenTestFixtures.playViewModel(savedQuizRepository = repository)

        viewModel.dismissSavedQuiz()

        coVerify(exactly = 1) { repository.clearSavedQuiz() }
    }
}
