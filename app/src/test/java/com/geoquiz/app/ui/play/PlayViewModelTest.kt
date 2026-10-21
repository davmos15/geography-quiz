package com.geoquiz.app.ui.play

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import com.geoquiz.app.domain.model.PinnedCategory
import com.geoquiz.app.domain.streak.StreakStatus
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestGameModes
import io.mockk.coVerify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

class PlayViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    // Real mode ids from the Countries page's fixed "More ways to play" order (silhouettes, then
    // physical_features, border_hop, currencies, tap_map, us_ca_regions): placement order is
    // tested, not sort order or registration order.
    private val silhouettes = TestGameModes.flaggedMode("silhouettes", FeatureFlag.COUNTRY_SILHOUETTES, sortOrder = 20)
    private val tapMap = TestGameModes.flaggedMode("tap_map", FeatureFlag.TAP_THE_MAP, sortOrder = 10, icon = ModeIcon.FLAG)

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
    fun `More ways to play is empty on every page while new modes' flags are off, even when registered`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(
            extraModes = setOf(silhouettes, tapMap),
            flagStates = MutableStateFlow(flagStates())
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(emptyList<ModeOption>(), state.extraModesByPage["countries"])
            assertEquals(emptyList<ModeOption>(), state.selectedExtraModes)
            assertEquals(3, state.classicModes.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `More ways to play follows the feature flags, in the page's fixed order, and never includes classic modes`() = runTest {
        val flags = MutableStateFlow(flagStates())
        val viewModel = ScreenTestFixtures.playViewModel(
            flagStates = flags,
            extraModes = setOf(silhouettes, tapMap)
        )

        viewModel.uiState.test {
            assertEquals(emptyList<ModeOption>(), awaitLoaded().selectedExtraModes)

            flags.value = flagStates(FeatureFlag.TAP_THE_MAP)
            assertEquals(listOf("tap_map"), awaitItem().selectedExtraModes.map { it.id })

            // silhouettes is fixed before tap_map on the Countries page, even though tap_map has
            // the lower sortOrder: placement order wins, not registry sort order.
            flags.value = flagStates(FeatureFlag.COUNTRY_SILHOUETTES, FeatureFlag.TAP_THE_MAP)
            val both = awaitItem()
            assertEquals(listOf("silhouettes", "tap_map"), both.selectedExtraModes.map { it.id })
            assertEquals(ModeOption("silhouettes", R.string.mode_countries_name, ModeIcon.GLOBE), both.selectedExtraModes.first())
            assertEquals(listOf("countries", "capitals", "flags"), both.classicModes.map { it.id })

            flags.value = flagStates()
            assertEquals(emptyList<ModeOption>(), awaitItem().selectedExtraModes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a released mode without a flag is always on its page`() = runTest {
        // border_hop is on the Countries page's fixed order; a null flag means always available.
        val released = TestGameModes.flaggedMode("border_hop", featureFlag = null, sortOrder = 50)
        val viewModel = ScreenTestFixtures.playViewModel(
            extraModes = setOf(released),
            flagStates = MutableStateFlow(flagStates())
        )

        viewModel.uiState.test {
            assertEquals(listOf("border_hop"), awaitLoaded().selectedExtraModes.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a mode not on any page's fixed list never appears, even when available`() = runTest {
        val unplaced = TestGameModes.flaggedMode("unplaced_mode", featureFlag = null, sortOrder = 5)
        val viewModel = ScreenTestFixtures.playViewModel(extraModes = setOf(unplaced))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.extraModesByPage.values.flatten().none { it.id == "unplaced_mode" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Capitals has no More ways to play modes`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel()

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(emptyList<ModeOption>(), state.extraModesByPage["capitals"])
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
            extraModes = setOf(tapMap)
        )

        viewModel.selectMode("tap_map")
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

    // Today's challenge, live (8.6)

    @Test
    fun `the Today's challenge card carries the live streak and done-today state`() = runTest {
        val streak = MutableStateFlow(StreakStatus.NONE)
        val viewModel = ScreenTestFixtures.playViewModel(
            flagStates = flowOf(flagStates(FeatureFlag.DAILY_CHALLENGE)),
            streak = streak
        )

        viewModel.uiState.test {
            val before = awaitUntil { it.todayChallenge != null }.todayChallenge!!
            assertFalse(before.playedToday)
            assertEquals(0, before.streak.current)

            streak.value = StreakStatus(
                current = 3, best = 3, freezesHeld = 0, daysToNextFreeze = 4, playedToday = true
            )
            val after = awaitUntil { it.todayChallenge?.playedToday == true }.todayChallenge!!
            assertEquals(3, after.streak.current)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the daily challenge has its card, so it is never placed on a page`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(
            flagStates = flowOf(flagStates(FeatureFlag.DAILY_CHALLENGE, FeatureFlag.COUNTRY_SILHOUETTES)),
            extraModes = setOf(silhouettes)
        )

        viewModel.uiState.test {
            val state = awaitUntil { it.todayChallenge != null && it.selectedExtraModes.isNotEmpty() }
            assertEquals(listOf(silhouettes.id), state.selectedExtraModes.map { it.id })
            assertTrue(state.extraModesByPage.values.flatten().none { it.id == "daily_challenge" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a streak never shows the card while the daily challenge flag is off`() = runTest {
        val flags = MutableStateFlow(flagStates())
        val viewModel = ScreenTestFixtures.playViewModel(
            flagStates = flags,
            streak = flowOf(StreakStatus(current = 5, best = 5, freezesHeld = 0, daysToNextFreeze = 2, playedToday = true))
        )

        viewModel.uiState.test {
            assertNull(awaitLoaded().todayChallenge)

            flags.value = flagStates(FeatureFlag.DAILY_CHALLENGE)
            assertEquals(5, awaitUntil { it.todayChallenge != null }.todayChallenge!!.streak.current)

            flags.value = flagStates()
            assertNull(awaitUntil { it.todayChallenge == null }.todayChallenge)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the Today's challenge card is off while its flag is off`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(flagStates = MutableStateFlow(flagStates()))

        viewModel.uiState.test {
            assertFalse(awaitLoaded().showTodayChallenge)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Surprise me (task P10-A.6). Countries over EIGHT: regions Africa, Americas, Asia, Europe
    // come first, so "region"/"Africa" is the first candidate; Flags' first candidate is the
    // most common single colour, "red" (see ScreenTestFixtures.FLAG_COLOURS).

    /** A [Random] whose [Random.nextBits] always returns [bits], so draws are deterministic. */
    private fun fixedRandom(bits: Int = 0) = QuizRandom(object : Random() {
        override fun nextBits(bitCount: Int): Int = bits
    })

    @Test
    fun `Surprise me picks the drawn candidate of the selected mode, never All`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(quizRandom = fixedRandom(0))

        viewModel.uiState.test {
            awaitLoaded()
            val pick = viewModel.surprise("countries")!!
            assertEquals(CategoryCandidate("regions", "region", "Africa"), pick)
            assertNotEquals("all", pick.categoryType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Surprise me follows whichever mode is asked, not the selected one`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(quizRandom = fixedRandom(0))

        viewModel.uiState.test {
            assertEquals("countries", awaitLoaded().selectedModeId)
            val pick = viewModel.surprise("flags")!!
            assertEquals(CategoryCandidate("flag_single_color", "flagcolor", "red"), pick)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Surprise me is null for a mode with no candidates`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(quizRandom = fixedRandom(0))

        viewModel.uiState.test {
            awaitLoaded()
            assertNull(viewModel.surprise("nonsense"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Surprise me never draws the All category, across many draws`() = runTest {
        repeat(50) { seed ->
            val viewModel = ScreenTestFixtures.playViewModel(quizRandom = QuizRandom(Random(seed)))
            viewModel.uiState.test {
                awaitLoaded()
                val pick = viewModel.surprise("countries")!!
                assertNotEquals("all", pick.categoryType)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // Surprise me is disabled until its candidate pool is ready (code review round, task P10-R1).

    @Test
    fun `surpriseMeReady is true once the selected mode's candidates have loaded`() = runTest {
        val viewModel = ScreenTestFixtures.playViewModel(quizRandom = fixedRandom(0))

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals("countries", state.selectedModeId)
            assertTrue(state.surpriseMeReady)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PlayUiState surpriseMeReady is a pure function of candidatesByMode and the selected mode`() {
        val withCandidates = PlayUiState(
            selectedModeId = "countries",
            candidatesByMode = mapOf("countries" to listOf(CategoryCandidate("regions", "region", "Africa")))
        )
        assertTrue(withCandidates.surpriseMeReady)

        assertFalse("null candidatesByMode (still loading)", PlayUiState(selectedModeId = "countries").surpriseMeReady)

        val emptyForMode = PlayUiState(selectedModeId = "countries", candidatesByMode = mapOf("countries" to emptyList()))
        assertFalse("an empty candidate list for the mode", emptyForMode.surpriseMeReady)

        val otherModeOnly = PlayUiState(
            selectedModeId = "countries",
            candidatesByMode = mapOf("flags" to listOf(CategoryCandidate("flag_single_color", "flagcolor", "red")))
        )
        assertFalse("candidates loaded, but not for the selected mode", otherModeOnly.surpriseMeReady)
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
