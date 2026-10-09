package com.geoquiz.app.ui.play

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import com.geoquiz.app.domain.model.PinnedCategory
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestGameModes
import io.mockk.coVerify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** Skips states until [predicate] holds. */
    private suspend fun ReceiveTurbine<PlayUiState>.awaitUntil(predicate: (PlayUiState) -> Boolean): PlayUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
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

    @Test
    fun `the continue card carries the time played`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = ScreenTestFixtures.SAVED_QUIZ)

        viewModel.uiState.test {
            val info = awaitUntil { it.savedQuiz != null }.savedQuiz!!
            assertEquals(75, info.timeElapsedSeconds)
            assertEquals("Starting with 'A'", info.categoryDisplayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Today's challenge (D24)

    @Test
    fun `the Today's challenge card follows the daily challenge flag`() = runTest {
        val flags = MutableStateFlow(flagStates())
        val viewModel = ScreenTestFixtures.playViewModel(flagStates = flags)

        viewModel.uiState.test {
            assertFalse(awaitLoaded().showTodayChallenge)

            flags.value = flagStates(FeatureFlag.DAILY_CHALLENGE)
            assertTrue(awaitUntil { it.showTodayChallenge }.showTodayChallenge)

            flags.value = flagStates(FeatureFlag.TAP_THE_MAP)
            assertFalse(awaitUntil { !it.showTodayChallenge }.showTodayChallenge)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the Today's challenge card is off by default`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel()

        viewModel.uiState.test {
            assertFalse(awaitLoaded().showTodayChallenge)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Recommended next (D25). Countries over EIGHT: regions Africa, Americas, Asia, Europe come first.

    @Test
    fun `with no history the first category of the mode is recommended`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = null)

        viewModel.uiState.test {
            val recommended = awaitUntil { it.recommended != null }.recommended!!
            assertEquals(
                RecommendedQuiz("countries", R.string.mode_countries_name, "region", "Africa", "Africa", stars = 0),
                recommended
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the recommendation follows history live`() = runTest {
        val stars = MutableStateFlow<Map<String, Int>>(emptyMap())
        val recent = MutableStateFlow<List<String>>(emptyList())
        val history = ScreenTestFixtures.quizHistoryRepository(
            stars = { if (it == "countries") stars else flowOf(emptyMap()) },
            recentKeys = { if (it == "countries") recent else flowOf(emptyList()) }
        )
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = null, history = history)

        viewModel.uiState.test {
            assertEquals("Africa", awaitUntil { it.recommended != null }.recommended!!.categoryValue)

            // Africa played (1 star): next in its group
            stars.value = mapOf("region|Africa" to 1)
            recent.value = listOf("region|Africa")
            assertEquals("Americas", awaitUntil { it.recommended?.categoryValue != "Africa" }.recommended!!.categoryValue)

            // Americas played last: Asia, the next with fewer than 2 stars, with its star
            stars.value = mapOf("region|Africa" to 1, "region|Americas" to 2, "region|Asia" to 1)
            recent.value = listOf("region|Americas", "region|Africa")
            val asia = awaitUntil { it.recommended?.categoryValue == "Asia" }.recommended!!
            assertEquals(1, asia.stars)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when the rest of the last group is mastered the first unplayed category is recommended`() = runTest {
        // Every region has 2 or 3 stars and Africa was last: (a) finds nothing, (b) gives the
        // first unplayed category after the regions.
        val stars = mapOf("region|Africa" to 2, "region|Americas" to 3, "region|Asia" to 3, "region|Europe" to 3)
        val history = ScreenTestFixtures.quizHistoryRepository(
            stars = { flowOf(stars) },
            recentKeys = { flowOf(listOf("region|Africa")) }
        )
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = null, history = history)

        viewModel.uiState.test {
            val recommended = awaitUntil { it.recommended != null }.recommended!!
            assertEquals("subregion", recommended.categoryType)
            assertEquals(0, recommended.stars)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the Continue category is never recommended`() = runTest {
        val saved = ScreenTestFixtures.SAVED_QUIZ.copy(categoryType = "region", categoryValue = "Africa")
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = saved)

        viewModel.uiState.test {
            val state = awaitUntil { it.recommended != null && it.savedQuiz != null }
            assertEquals("Americas", state.recommended!!.categoryValue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a saved quiz in another mode does not change the recommendation`() = runTest {
        val saved = ScreenTestFixtures.SAVED_QUIZ.copy(quizMode = "capitals", categoryType = "region", categoryValue = "Africa")
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = saved)

        viewModel.uiState.test {
            val state = awaitUntil { it.recommended != null && it.savedQuiz != null }
            assertEquals("Africa", state.recommended!!.categoryValue)

            viewModel.selectMode("capitals")
            val capitals = awaitUntil { it.recommended?.quizModeId == "capitals" }.recommended!!
            assertEquals(R.string.mode_capitals_name, capitals.modeLabel)
            assertEquals("Americas", capitals.categoryValue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the recommendation follows the selected mode and its own history`() = runTest {
        val history = ScreenTestFixtures.quizHistoryRepository(
            stars = { mode -> flowOf(if (mode == "flags") mapOf("flagcolor|red" to 1) else emptyMap()) }
        )
        val viewModel = ScreenTestFixtures.playViewModel(savedQuiz = null, history = history)

        viewModel.uiState.test {
            awaitUntil { it.recommended?.quizModeId == "countries" }
            viewModel.selectMode("flags")
            val flags = awaitUntil { it.recommended?.quizModeId == "flags" }.recommended!!
            // By colour, most countries first: red (3), white (3), blue (1); red already has a star.
            assertEquals("flagcolor", flags.categoryType)
            assertEquals("white", flags.categoryValue)
            assertEquals(R.string.mode_flags_name, flags.modeLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Pinned (3.4c, D23). Over EIGHT: regions Africa, Americas, Asia, Europe; flag colours red, white, blue.

    @Test
    fun `pinned categories of every mode are listed in pin order with mode and category`() = runTest {
        val pins = MutableStateFlow(
            listOf(
                PinnedCategory("flags", "flagcolor", "red"),
                PinnedCategory("countries", "region", "Africa"),
                PinnedCategory("capitals", "region", "Americas")
            )
        )
        val viewModel = ScreenTestFixtures.playViewModel(pins = pins)

        viewModel.uiState.test {
            val state = awaitUntil { it.pinned.isNotEmpty() }
            val pinned = state.pinned
            assertEquals(
                listOf(
                    PinnedQuiz("flags", R.string.mode_flags_name, ModeIcon.FLAG, "flagcolor", "red", "Red"),
                    PinnedQuiz("countries", R.string.mode_countries_name, ModeIcon.GLOBE, "region", "Africa", "Africa"),
                    PinnedQuiz("capitals", R.string.mode_capitals_name, ModeIcon.LANDMARK, "region", "Americas", "Americas")
                ),
                pinned
            )
            // Not limited to the selected mode
            assertEquals("countries", state.selectedModeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pins follow the store live and none means an empty list`() = runTest {
        val pins = MutableStateFlow<List<PinnedCategory>>(emptyList())
        val viewModel = ScreenTestFixtures.playViewModel(pins = pins)

        viewModel.uiState.test {
            assertEquals(emptyList<PinnedQuiz>(), awaitLoaded().pinned)

            pins.value = listOf(PinnedCategory("countries", "region", "Asia"))
            assertEquals(listOf("Asia"), awaitUntil { it.pinned.isNotEmpty() }.pinned.map { it.categoryValue })

            pins.value = listOf(PinnedCategory("countries", "region", "Asia"), PinnedCategory("capitals", "region", "Asia"))
            assertEquals(
                listOf("countries", "capitals"),
                awaitUntil { it.pinned.size == 2 }.pinned.map { it.quizModeId }
            )

            pins.value = emptyList()
            assertEquals(emptyList<PinnedQuiz>(), awaitUntil { it.pinned.isEmpty() }.pinned)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pins whose category the mode no longer lists are hidden`() = runTest {
        val pins = MutableStateFlow(
            listOf(
                PinnedCategory("countries", "region", "Oceania"), // no country in the data
                PinnedCategory("flags", "flagcolor", "green"), // no flag with green
                PinnedCategory("countries", "region", "Europe")
            )
        )
        val viewModel = ScreenTestFixtures.playViewModel(pins = pins)

        viewModel.uiState.test {
            val pinned = awaitUntil { it.pinned.isNotEmpty() }.pinned
            assertEquals(listOf("Europe"), pinned.map { it.categoryValue })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
