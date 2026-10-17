package com.geoquiz.app.ui.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import com.geoquiz.app.domain.model.PinnedCategory
import com.geoquiz.app.domain.streak.StreakStatus
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestGameModes
import com.geoquiz.app.ui.navigation.ModeRoutes
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import io.mockk.coVerify
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Play tab (3.4a, D22) on the real [PlayScreen] and [PlayViewModel]. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PlayScreenUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val categories = mutableListOf<Pair<String, String>>()
    private val quizzes = mutableListOf<Triple<String, String, String>>()
    private val openedModes = mutableListOf<String>()

    private fun launch(viewModel: PlayViewModel = ScreenTestFixtures.playViewModel()) {
        compose.setContent {
            // Inspection mode keeps the banner ad out of the test.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                GeographyQuizTheme(darkTheme = false) {
                    PlayScreen(
                        onOpenCategory = { mode, group -> categories += mode to group },
                        onStartQuiz = { mode, type, value -> quizzes += Triple(mode, type, value) },
                        onOpenMode = { openedModes += it },
                        viewModel = viewModel
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** A mode segment, whether it shows its label or (large text) only a described icon. */
    private fun segment(label: String) = compose.onNode(hasText(label).or(hasContentDescription(label)))

    private val grid get() = compose.onNode(hasScrollAction())

    private fun hasClickLabel(label: String) = SemanticsMatcher("onClick label = $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun `Play shows the mode switch with Countries selected and its groups`() {
        launch()

        segment("Countries").assertIsDisplayed().assertIsSelected()
        segment("Capitals").assertIsDisplayed().assertIsNotSelected()
        segment("Flags").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithText("All countries").assertIsDisplayed()

        grid.performScrollToNode(hasText("Letter Patterns"))
        compose.onNodeWithText("Letter Patterns").assertIsDisplayed()
        grid.performScrollToNode(hasText("Word Patterns"))
        compose.onNodeWithText("Word Patterns").performClick()
        assertEquals(listOf("countries" to "word_patterns"), categories)
    }

    @Test
    fun `All countries starts a quiz in one tap`() {
        launch()

        compose.onNodeWithText("All countries").performClick()

        assertEquals(listOf(Triple("countries", "all", "_")), quizzes)
    }

    @Test
    fun `All capitals starts a capitals quiz in one tap`() {
        launch()

        segment("Capitals").performClick()
        compose.waitForIdle()
        segment("Capitals").assertIsSelected()
        compose.onNodeWithText("All capitals").assertIsDisplayed().performClick()

        assertEquals(listOf(Triple("capitals", "all", "_")), quizzes)
    }

    @Test
    fun `switching to Flags shows the flag groups`() {
        launch()

        segment("Flags").performClick()
        compose.waitForIdle()

        segment("Flags").assertIsSelected()
        segment("Countries").assertIsNotSelected()
        compose.onNode(hasText("All countries")).assertDoesNotExist()
        grid.performScrollToNode(hasText("By Colour"))
        compose.onNodeWithText("By Colour").assertIsDisplayed().performClick()
        grid.performScrollToNode(hasText("Shapes & Objects"))
        compose.onNodeWithText("Shapes & Objects").assertIsDisplayed()
        assertEquals(listOf("flags" to "flag_single_color"), categories)
    }

    @Test
    fun `New modes is absent by default`() {
        launch(ScreenTestFixtures.playViewModel(extraModes = setOf(tapTheMap)))

        compose.onNode(hasText("New modes")).assertDoesNotExist()
        compose.onNode(hasClickLabel("Open mode")).assertDoesNotExist()
    }

    @Test
    fun `New modes shows an enabled mode and opens it`() {
        val enabled = FeatureFlag.entries.map {
            FeatureFlagState(it, enabled = it == FeatureFlag.TAP_THE_MAP, overridden = it == FeatureFlag.TAP_THE_MAP)
        }
        launch(ScreenTestFixtures.playViewModel(flagStates = flowOf(enabled), extraModes = setOf(tapTheMap)))

        grid.performScrollToNode(hasText("New modes"))
        compose.onNodeWithText("New modes").assertIsDisplayed()
        grid.performScrollToNode(hasClickLabel("Open mode"))
        compose.onNode(hasClickLabel("Open mode")).performClick()

        assertEquals(listOf("tap_the_map"), openedModes)
    }

    @Test
    fun `the Continue card resumes the saved quiz in its own mode`() {
        launch(ScreenTestFixtures.playViewModel(savedQuiz = ScreenTestFixtures.SAVED_QUIZ.copy(quizMode = "capitals")))

        val card = compose.onNode(hasClickLabel("Resume quiz"))
        card.assertIsDisplayed()
            .assert(hasText("Continue"))
            .assert(hasText("Capitals: Starting with 'A'"))
            // Shown as "2 answered in 01:15"; spoken with the time in words instead
            .assert(hasContentDescription("2 answered in 1 minute 15 seconds"))
        compose.onNodeWithContentDescription("Dismiss saved quiz").assertIsDisplayed().assertHasClickAction()

        card.performClick()

        assertEquals(listOf(Triple("capitals", "startletter", "A")), quizzes)
    }

    @Test
    fun `dismissing the Continue card does not start a quiz`() {
        val repository = ScreenTestFixtures.savedQuizRepository()
        launch(ScreenTestFixtures.playViewModel(savedQuizRepository = repository))

        compose.onNodeWithContentDescription("Dismiss saved quiz").performClick()

        coVerify(exactly = 1) { repository.clearSavedQuiz() }
        assertEquals(emptyList<Triple<String, String, String>>(), quizzes)
    }

    @Test
    fun `Recommended next shows the mode, category and stars and starts that quiz in one tap`() {
        launch(ScreenTestFixtures.playViewModel(savedQuiz = null))

        val card = compose.onNode(hasText("Recommended next"))
        card.assertIsDisplayed()
            .assert(hasText("Countries: Africa"))
            .assert(hasContentDescription("0 of 3 stars"))
            .assert(hasClickLabel("Start quiz"))
            .performClick()

        assertEquals(listOf(Triple("countries", "region", "Africa")), quizzes)
    }

    @Test
    fun `Recommended next names the stars earned`() {
        val history = ScreenTestFixtures.quizHistoryRepository(
            stars = { flowOf(mapOf("region|Africa" to 1, "region|Americas" to 1)) },
            recentKeys = { flowOf(listOf("region|Africa")) }
        )
        launch(ScreenTestFixtures.playViewModel(savedQuiz = null, history = history))

        compose.onNode(hasText("Recommended next"))
            .assert(hasText("Countries: Americas"))
            .assert(hasContentDescription("1 of 3 stars"))
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the top cards show in full and still work`() {
        val daily = FeatureFlag.entries.map {
            FeatureFlagState(it, enabled = it == FeatureFlag.DAILY_CHALLENGE, overridden = it == FeatureFlag.DAILY_CHALLENGE)
        }
        launch(ScreenTestFixtures.playViewModel(flagStates = flowOf(daily)))

        compose.onNode(hasText("Today’s challenge")).assertIsDisplayed()
        grid.performScrollToNode(hasText("Continue"))
        compose.onNode(hasClickLabel("Resume quiz")).assertIsDisplayed()
        compose.onNodeWithContentDescription("Dismiss saved quiz").assertIsDisplayed()
        grid.performScrollToNode(hasText("Recommended next"))
        compose.onNode(hasText("Recommended next"))
            .assertIsDisplayed()
            .assert(hasText("Countries: Africa"))
            .performClick()

        assertEquals(listOf(Triple("countries", "region", "Africa")), quizzes)
    }

    /** Every flag at its default except the daily challenge, which is switched off (D63 defaults it on). */
    private fun dailyOff() = FeatureFlag.entries.map {
        val daily = it == FeatureFlag.DAILY_CHALLENGE
        FeatureFlagState(it, enabled = !daily && it.defaultEnabled, overridden = daily)
    }

    @Test
    fun `Today's challenge is hidden while its flag is off`() {
        launch(ScreenTestFixtures.playViewModel(flagStates = flowOf(dailyOff())))

        compose.onNode(hasText("Today’s challenge")).assertDoesNotExist()
    }

    @Test
    fun `Today's challenge opens the daily challenge in one tap with its flag on`() {
        val daily = FeatureFlag.entries.map {
            FeatureFlagState(it, enabled = it == FeatureFlag.DAILY_CHALLENGE, overridden = it == FeatureFlag.DAILY_CHALLENGE)
        }
        launch(ScreenTestFixtures.playViewModel(flagStates = flowOf(daily)))

        compose.onNode(hasText("Today’s challenge"))
            .assertIsDisplayed()
            .assert(hasText("10 questions, the same for everyone today. One go per day."))
            .performClick()
        assertEquals(listOf(ModeRoutes.DAILY_CHALLENGE_MODE_ID), openedModes)
    }

    @Test
    fun `no streak chip without a running streak, or with the daily challenge off`() {
        val daily = FeatureFlag.entries.map {
            FeatureFlagState(it, enabled = it == FeatureFlag.DAILY_CHALLENGE, overridden = it == FeatureFlag.DAILY_CHALLENGE)
        }
        launch(ScreenTestFixtures.playViewModel(flagStates = flowOf(daily)))
        compose.onNodeWithContentDescription("streak", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a streak shows no chip or card while the daily challenge flag is off`() {
        val streak = StreakStatus(current = 5, best = 5, freezesHeld = 0, daysToNextFreeze = 2, playedToday = true)
        launch(ScreenTestFixtures.playViewModel(flagStates = flowOf(dailyOff()), streak = flowOf(streak)))

        compose.onNodeWithContentDescription("5-day streak", substring = true).assertDoesNotExist()
        compose.onNode(hasText("Today’s challenge")).assertDoesNotExist()
    }

    @Test
    fun `a running streak shows in the header and the card says today is done`() {
        val daily = FeatureFlag.entries.map {
            FeatureFlagState(it, enabled = it == FeatureFlag.DAILY_CHALLENGE, overridden = it == FeatureFlag.DAILY_CHALLENGE)
        }
        val streak = StreakStatus(
            current = 5, best = 5, freezesHeld = 1, daysToNextFreeze = 2, playedToday = true
        )
        launch(ScreenTestFixtures.playViewModel(flagStates = flowOf(daily), streak = flowOf(streak)))

        compose.onNodeWithContentDescription("5-day streak, 1 streak freeze saved").assertIsDisplayed()
        compose.onNode(hasText("Today’s challenge"))
            .assert(hasText("Done for today. A new set arrives tomorrow."))
            .assert(hasText("Streak: 5 days", substring = true))
    }

    // Pinned (3.4c)

    @Test
    fun `Pinned is hidden when nothing is pinned`() {
        launch(ScreenTestFixtures.playViewModel(savedQuiz = null))

        compose.onNode(hasText("Pinned")).assertDoesNotExist()
    }

    @Test
    fun `a Pinned row shows category and mode and starts that quiz in its mode in one tap`() {
        val pins = flowOf(
            listOf(PinnedCategory("flags", "flagcolor", "red"), PinnedCategory("capitals", "region", "Asia"))
        )
        launch(ScreenTestFixtures.playViewModel(savedQuiz = null, pins = pins))

        grid.performScrollToNode(hasText("Pinned"))
        compose.onNodeWithText("Pinned").assertIsDisplayed()
        grid.performScrollToNode(hasText("Red"))
        compose.onNode(hasText("Red").and(hasText("Flags")))
            .assertIsDisplayed()
            .assert(hasClickLabel("Start quiz"))
        grid.performScrollToNode(hasText("Asia"))
        compose.onNode(hasText("Asia").and(hasText("Capitals")))
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf(Triple("capitals", "region", "Asia")), quizzes)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text a Pinned row shows in full and works`() {
        launch(
            ScreenTestFixtures.playViewModel(
                savedQuiz = null,
                pins = flowOf(listOf(PinnedCategory("countries", "region", "Europe")))
            )
        )

        grid.performScrollToNode(hasText("Europe").and(hasText("Countries")))
        compose.onNode(hasText("Europe").and(hasText("Countries")))
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf(Triple("countries", "region", "Europe")), quizzes)
    }

    private val tapTheMap = TestGameModes.flaggedMode("tap_the_map", FeatureFlag.TAP_THE_MAP, sortOrder = 10)
}
