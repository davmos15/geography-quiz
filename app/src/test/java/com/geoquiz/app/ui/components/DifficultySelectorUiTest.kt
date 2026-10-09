package com.geoquiz.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.assertIsRightOf
import com.geoquiz.app.ui.assertLineCount
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [DifficultySelector] at 100% and 200% text (3.6): no tier name is ever cut off. */
@RunWith(RobolectricTestRunner::class)
// Real text measurement, so line counts and clipping are meaningful.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class DifficultySelectorUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val picked = mutableListOf<Difficulty>()
    private val names = listOf("Easy", "Normal", "Hard")

    private fun launch() {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                var selected by remember { mutableStateOf(Difficulty.NORMAL) }
                DifficultySelector(
                    selected = selected,
                    onSelect = {
                        picked += it
                        selected = it
                    }
                )
            }
        }
        compose.waitForIdle()
    }

    private fun label(name: String) = compose.onNodeWithText(name, useUnmergedTree = true)

    /** The tappable tier (a segment or a radio row), found by its name. */
    private fun tier(name: String) = compose.onNode(
        hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    )

    @Test
    fun `at 100 percent text the tiers are segments on one line`() {
        launch()

        names.forEach { label(it).assertIsDisplayed().assertTextNotClipped().assertLineCount(1) }
        label("Normal").assertIsRightOf(label("Easy"))
        label("Hard").assertIsRightOf(label("Normal"))
        tier("Normal").assertIsSelected()

        tier("Hard").performClick()

        assertEquals(listOf(Difficulty.HARD), picked)
        tier("Hard").assertIsSelected()
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the tiers become radio rows with every name in full`() {
        launch()

        names.forEach { label(it).assertIsDisplayed().assertTextNotClipped().assertLineCount(1) }
        label("Normal").assertIsBelow(label("Easy"))
        label("Hard").assertIsBelow(label("Normal"))
        tier("Normal").assertIsSelected()
        tier("Easy").assertIsNotSelected()

        tier("Easy").performClick()

        assertEquals(listOf(Difficulty.EASY), picked)
        tier("Easy").assertIsSelected()
        tier("Normal").assertIsNotSelected()
        // The explanation follows the choice.
        compose.onNodeWithText("Pick each answer from 4 options.").assertIsDisplayed()
    }
}
