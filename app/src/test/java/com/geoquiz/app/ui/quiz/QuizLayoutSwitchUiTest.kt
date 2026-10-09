package com.geoquiz.app.ui.quiz

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures.QuizHarness
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The quiz when its space changes under it (3.7): rotation or multi-window switching between one
 * and two panes, and the keyboard opening (the space crossing the 560 dp title threshold). The
 * screen sits in a box of the given size inside a large window. The quiz has 40 countries, so
 * the list scrolls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h900dp")
class QuizLayoutSwitchUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val countries = (1..40).map { n ->
        val number = n.toString().padStart(2, '0')
        TestQuizData.country("C$number", "Country $number", "Capital $number")
    }

    private val harness = QuizHarness(countries = countries)

    private var width by mutableStateOf(PHONE_WIDTH)
    private var height by mutableStateOf(PHONE_HEIGHT)

    @Composable
    private fun Quiz(width: Dp, height: Dp) {
        GeographyQuizTheme(darkTheme = false) {
            Box(modifier = Modifier.size(width, height)) {
                QuizScreen(
                    onQuizComplete = {},
                    onNavigateHome = {},
                    viewModel = harness.viewModel,
                    hapticFeedbackPlayer = {}
                )
            }
        }
    }

    private fun launch() {
        compose.setContent { Quiz(width, height) }
        compose.waitForIdle()
    }

    private fun row(n: Int) = compose.onNodeWithContentDescription("Row $n, not yet answered")
    private val list get() = compose.onNode(hasScrollToIndexAction())
    private val field get() = compose.onNode(hasSetTextAction())
    private val title get() = compose.onNode(isHeading() and hasText("All Countries"))
    private val count get() = compose.onNodeWithContentDescription("0 of 40 countries named")

    /** Scrolls the list so row [n] is at the top (no header item at the phone height). */
    private fun scrollListToRow(n: Int) {
        list.performScrollToIndex(n - 1)
        compose.waitForIdle()
        row(n).assertIsDisplayed()
        row(1).assertDoesNotExist()
    }

    @Test
    fun `the list keeps its position when the layout switches to two panes and back`() {
        launch()
        scrollListToRow(25)

        width = TABLET_WIDTH
        height = TABLET_HEIGHT
        compose.waitForIdle()
        row(25).assertIsDisplayed()
        row(1).assertDoesNotExist()

        width = PHONE_WIDTH
        height = PHONE_HEIGHT
        compose.waitForIdle()
        row(25).assertIsDisplayed()
        row(1).assertDoesNotExist()
    }

    @Test
    fun `the list keeps its position when the activity is recreated in the other layout`() {
        val restoration = StateRestorationTester(compose)
        // Read once per composition, like the configuration after a rotation.
        var twoPanes = false
        restoration.setContent {
            // One call site, as after a real rotation (saved state is keyed by position).
            Quiz(
                width = if (twoPanes) TABLET_WIDTH else PHONE_WIDTH,
                height = if (twoPanes) TABLET_HEIGHT else PHONE_HEIGHT
            )
        }
        compose.waitForIdle()
        scrollListToRow(25)

        twoPanes = true
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        row(25).assertIsDisplayed()
        row(1).assertDoesNotExist()
        field.assertIsDisplayed()
    }

    @Test
    fun `when the keyboard leaves less than 560 dp the field keeps its focus and text`() {
        launch()
        field.performTextInput("Coun")
        field.assertIsFocused()
        title.assertIsDisplayed()

        height = SHORT_HEIGHT
        compose.waitForIdle()

        field.assertIsFocused()
        compose.onNode(hasSetTextAction() and hasText("Coun")).assertExists()
        // The title moved into the list, and the list was at the top, so the title still shows.
        title.assertIsDisplayed().assertIsBelow(count)

        height = PHONE_HEIGHT
        compose.waitForIdle()
        field.assertIsFocused()
        title.assertIsDisplayed()
        count.assertIsBelow(title)
    }

    @Test
    fun `a list the player has scrolled stays put when the title moves into it`() {
        launch()
        scrollListToRow(20)

        height = SHORT_HEIGHT
        compose.waitForIdle()

        row(20).assertIsDisplayed()
        title.assertDoesNotExist()
    }

    private companion object {
        val PHONE_WIDTH = 411.dp
        val PHONE_HEIGHT = 880.dp
        /** About a phone with the keyboard open. */
        val SHORT_HEIGHT = 480.dp
        val TABLET_WIDTH = 1000.dp
        val TABLET_HEIGHT = 700.dp
    }
}
