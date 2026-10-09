package com.geoquiz.app.ui.quiz

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures.QuizHarness
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The quiz layout on phones, short windows, landscape phones and tablets (3.7), on the real
 * [QuizScreen] and [QuizViewModel]. The quiz lists Austria, France and Germany (rows 1 to 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class QuizAdaptiveLayoutUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun launch() {
        val harness = QuizHarness()
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                QuizScreen(
                    onQuizComplete = {},
                    onNavigateHome = {},
                    viewModel = harness.viewModel,
                    hapticFeedbackPlayer = {}
                )
            }
        }
        compose.waitForIdle()
    }

    private val field get() = compose.onNode(hasSetTextAction())
    private val title get() = compose.onNode(isHeading())
    private val count get() = compose.onNodeWithContentDescription("0 of 3 countries named")
    private fun row(n: Int) = compose.onNodeWithContentDescription("Row $n, not yet answered")

    /** The list sits beside the answer field, on its end side. */
    private fun assertListBesideField() {
        val fieldBounds = field.getBoundsInRoot()
        val rowBounds = row(1).getBoundsInRoot()
        assertTrue("list $rowBounds is not to the right of the field $fieldBounds", rowBounds.left >= fieldBounds.right)
    }

    @Test
    fun `on a phone the answer field is pinned below the list, the title above it`() {
        launch()

        title.assertIsDisplayed()
        count.assertIsBelow(title)
        row(3).assertIsDisplayed()
        field.assertIsDisplayed().assertIsBelow(row(3))
        compose.onNodeWithText("Submit").assertIsDisplayed().assertIsBelow(field)
    }

    @Test
    fun `answering still works with the field at the bottom`() {
        launch()

        field.performTextInput("France")
        compose.onNodeWithText("Submit").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("France, answered").assertIsDisplayed()
        compose.onNodeWithContentDescription("Recent answers: France").assertIsDisplayed()
            .assertIsBelow(compose.onNodeWithContentDescription("Row 3, not yet answered"))
    }

    @Test
    @Config(qualifiers = "w360dp-h500dp")
    fun `on a short window the title scrolls with the list and the count stays pinned`() {
        launch()

        // The title is now the list's first item, under the pinned count.
        title.assertIsDisplayed().assertIsBelow(count)
        row(1).assertIsBelow(title)
        field.assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp")
    fun `on a tablet the list sits beside the prompt and the answer field`() {
        launch()

        title.assertIsDisplayed()
        field.assertIsDisplayed()
        row(3).assertIsDisplayed()
        assertListBesideField()
        // The field is still the last thing in its pane, below the status.
        field.assertIsBelow(count)
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `on a small tablet in portrait the quiz keeps one pane`() {
        launch()

        field.assertIsDisplayed().assertIsBelow(row(3))
    }

    private fun hasTraversalIndex(index: Float) =
        SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, index)

    private val isTraversalGroup = SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true)

    @Test
    fun `on a phone TalkBack reads the status and the answer controls before the list`() {
        launch()

        // The status, then the field and Submit, each as one group ahead of the list (index 0).
        compose.onNode(
            hasTraversalIndex(STATUS_ORDER) and isTraversalGroup and
                hasAnyDescendant(hasContentDescription("0 of 3 countries named"))
        ).assertExists()
        val controls = hasTraversalIndex(CONTROLS_ORDER) and isTraversalGroup and
            hasAnyDescendant(hasSetTextAction()) and hasAnyDescendant(hasText("Submit"))
        compose.onNode(controls).assertExists()
        compose.onNode(hasTraversalIndex(TITLE_ORDER) and hasAnyDescendant(isHeading())).assertExists()
        // Title before status before controls; the list rows are in none of those groups, so
        // they keep the default index 0 and come last.
        assertTrue(TITLE_ORDER < STATUS_ORDER && STATUS_ORDER < CONTROLS_ORDER && CONTROLS_ORDER < 0f)
        (1..3).forEach { n ->
            row(n).assert(!hasAnyAncestor(hasTraversalIndex(CONTROLS_ORDER)))
                .assert(!hasAnyAncestor(hasTraversalIndex(STATUS_ORDER)))
                .assert(!hasAnyAncestor(hasTraversalIndex(TITLE_ORDER)))
        }
        // ...and all of them are siblings in one traversal group, so the indices compare.
        val listGroup = isTraversalGroup and hasAnyDescendant(hasContentDescription("Row 1, not yet answered"))
        compose.onNode(controls and hasParent(listGroup)).assertExists()
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp")
    fun `in two panes TalkBack reads the controls pane before the list`() {
        launch()

        val controlsPane = hasTraversalIndex(CONTROLS_ORDER) and isTraversalGroup and
            hasAnyDescendant(hasSetTextAction()) and hasAnyDescendant(isHeading())
        compose.onNode(controlsPane).assertExists()
        row(1).assert(!hasAnyAncestor(hasTraversalIndex(CONTROLS_ORDER)))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `on a large tablet the list sits beside the answer field`() {
        launch()

        assertListBesideField()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp")
    fun `on a phone in landscape the list sits beside the answer field`() {
        launch()

        field.assertIsDisplayed()
        row(1).assertIsDisplayed()
        assertListBesideField()
    }
}
