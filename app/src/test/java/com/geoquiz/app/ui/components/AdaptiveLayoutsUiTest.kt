package com.geoquiz.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.assertIsRightOf
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.heightDp
import com.geoquiz.app.ui.quiz.components.PanelAboveList
import com.geoquiz.app.ui.textLayout
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The large-text layouts (3.6) on a narrow phone, at 100% and 200% text. */
@RunWith(RobolectricTestRunner::class)
// Real text measurement, so line counts and clipping are meaningful.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class AdaptiveLayoutsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { GeographyQuizTheme(darkTheme = false) { content() } }
        compose.waitForIdle()
    }

    private fun twoButtons() = setContent {
        // The width of the Results buttons on this phone (80% of 312 dp).
        AdaptiveButtonRow(modifier = Modifier.size(width = 250.dp, height = 400.dp)) {
            Button(onClick = {}) { Text("Share") }
            Button(onClick = {}) { Text("Challenge") }
        }
    }

    private fun label(text: String) = compose.onNodeWithText(text, useUnmergedTree = true)

    @Test
    fun `at 100 percent text two short buttons sit side by side`() {
        twoButtons()

        label("Challenge").assertIsRightOf(label("Share"))
        label("Share").assertTextNotClipped()
        label("Challenge").assertTextNotClipped()
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the buttons stack and show their labels in full`() {
        twoButtons()

        label("Share").assertIsDisplayed().assertTextNotClipped()
        label("Challenge").assertIsDisplayed().assertTextNotClipped().assertIsBelow(label("Share"))
    }

    @Test
    fun `the clipping check catches a label cut off by a fixed layout`() {
        // The old Results buttons: one line, no wrapping, in a box too narrow for the label.
        setContent {
            Box(modifier = Modifier.width(60.dp)) {
                Text("Challenge", maxLines = 1, softWrap = false)
            }
        }

        val failure = runCatching { label("Challenge").assertTextNotClipped() }.exceptionOrNull()
        assertTrue("a cut-off label passed the check", failure is AssertionError)
    }

    private fun nameWithActions() = setContent {
        AdaptiveTrailingRow(
            modifier = Modifier.fillMaxWidth(),
            main = { Text("Central African Republic", modifier = Modifier.testTag("main")) },
            // Like a category row: a count and two 48 dp buttons.
            trailing = {
                Row(modifier = Modifier.testTag("trailing")) {
                    Text("54")
                    repeat(2) {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Action $it")
                        }
                    }
                }
            }
        )
    }

    @Test
    fun `at 100 percent text the trailing button sits beside the name`() {
        nameWithActions()

        compose.onNodeWithTag("trailing").assertIsRightOf(compose.onNodeWithTag("main"))
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the trailing button moves below and the name gets the full width`() {
        nameWithActions()

        compose.onNodeWithTag("trailing").assertIsDisplayed().assertIsBelow(compose.onNodeWithTag("main"))
        compose.onNodeWithTag("main").assertTextNotClipped()
        assertEquals(360f, compose.onNodeWithTag("main").getBoundsInRoot().right.value, 1f)
    }

    private fun tiles() = setContent {
        LazyVerticalGrid(
            columns = UpToTwoColumns(minCellWidth = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(listOf("one", "two")) { tag ->
                Box(modifier = Modifier.height(80.dp).testTag(tag))
            }
        }
    }

    @Test
    fun `at 100 percent text the tiles make two columns`() {
        tiles()

        compose.onNodeWithTag("two").assertIsRightOf(compose.onNodeWithTag("one"))
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the tiles make one column`() {
        tiles()

        compose.onNodeWithTag("two").assertIsBelow(compose.onNodeWithTag("one"))
        assertEquals(360f, compose.onNodeWithTag("one").getBoundsInRoot().right.value, 1f)
    }

    private val longTitle = "Answers - All Countries"

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text a long top-bar title wraps to two lines, is not cut off vertically and is a heading`() {
        setContent {
            WrappingTopAppBar(
                title = longTitle,
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }

        val title = compose.onNodeWithText(longTitle, useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        val layout = title.textLayout()
        assertEquals(2, layout.lineCount)
        assertTrue("title cut off vertically", !layout.didOverflowHeight)
        // Taller than Material's fixed 64 dp bar.
        assertTrue(title.getBoundsInRoot().heightDp > 64.dp)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text a title too long for two lines is ellipsised but read in full`() {
        val veryLong = "Answers - Countries whose names contain every vowel at least once"
        setContent { WrappingTopAppBar(title = veryLong) }

        val title = compose.onNodeWithText(veryLong, useUnmergedTree = true).assertIsDisplayed()
        val layout = title.textLayout()
        assertEquals(2, layout.lineCount)
        assertTrue("expected an ellipsis", layout.isLineEllipsized(1))
        // TalkBack gets the whole title, not the shortened one.
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(veryLong))))
        compose.onNode(hasText(veryLong)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `at 100 percent text a short top-bar title stays on one line`() {
        setContent { WrappingTopAppBar(title = "Settings") }

        compose.onNodeWithText("Settings", useUnmergedTree = true).assertTextNotClipped()
        assertEquals(1, compose.onNodeWithText("Settings", useUnmergedTree = true).textLayout().lineCount)
    }

    private fun tallPanel() = setContent {
        PanelAboveList(
            modifier = Modifier.size(width = 360.dp, height = 600.dp),
            panel = { Box(modifier = Modifier.height(2000.dp).fillMaxWidth().testTag("panel")) },
            list = { modifier -> Box(modifier = modifier.fillMaxWidth().testTag("list")) }
        )
    }

    @Test
    fun `at 100 percent text a tall Easy panel scrolls and leaves the list at least 120 dp`() {
        tallPanel()

        val list = compose.onNodeWithTag("list").assertIsDisplayed().getBoundsInRoot()
        assertTrue("list is ${list.heightDp}", list.heightDp >= 119.dp)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text a tall Easy panel leaves the list 40 percent of the space`() {
        tallPanel()

        val list = compose.onNodeWithTag("list").assertIsDisplayed().getBoundsInRoot()
        assertTrue("list is ${list.heightDp}", list.heightDp >= 239.dp)
    }

    @Test
    @Config(fontScale = 2f)
    fun `the Easy panel scrolls back to the top when the question changes`() {
        var question by mutableStateOf("FRA")
        setContent {
            PanelAboveList(
                modifier = Modifier.size(width = 360.dp, height = 600.dp),
                panel = {
                    Text("Top of $question", modifier = Modifier.testTag("top"))
                    Box(modifier = Modifier.height(2000.dp).fillMaxWidth())
                },
                list = { modifier -> Box(modifier = modifier.fillMaxWidth()) },
                scrollKey = question
            )
        }
        val startTop = compose.onNodeWithTag("top").getBoundsInRoot().top

        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 500f) }
        compose.waitForIdle()
        // Scrolled out of view.
        compose.onNodeWithTag("top").assertIsNotDisplayed()

        question = "DEU"
        compose.waitForIdle()

        compose.onNodeWithTag("top").assertIsDisplayed()
        assertEquals(startTop.value, compose.onNodeWithTag("top").getBoundsInRoot().top.value, 0.5f)
    }
}
