package com.geoquiz.app.ui.quiz

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.quiz.components.MultipleChoicePanel
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.geoquiz.app.ui.widthDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Easy options' tick and cross grow with the text (3.6); long option names wrap. */
@RunWith(RobolectricTestRunner::class)
// Real text measurement, so line counts and clipping are meaningful.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class MultipleChoicePanelUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val question = ChoiceQuestion(
        targetCode = "CAF",
        options = listOf(
            ChoiceOption("CAF", "Central African Republic"),
            ChoiceOption("COD", "Democratic Republic of the Congo"),
            ChoiceOption("FRA", "France"),
            ChoiceOption("PER", "Peru")
        ),
        prompt = ChoicePrompt.InSet("Africa")
    )

    private fun launchAfterWrongPick() {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                MultipleChoicePanel(
                    question = question,
                    feedback = ChoiceFeedback(selectedCode = "COD", isCorrect = false),
                    enabled = true,
                    onSelect = {}
                )
            }
        }
        compose.waitForIdle()
    }

    private fun iconWidth(description: String): Dp =
        compose.onNodeWithContentDescription(description, useUnmergedTree = true)
            .assertIsDisplayed()
            .getBoundsInRoot()
            .widthDp

    @Test
    fun `at 100 percent text the result icons are 24 dp`() {
        launchAfterWrongPick()

        assertEquals(24f, iconWidth("correct answer").value, 0.5f)
        assertEquals(24f, iconWidth("your answer, incorrect").value, 0.5f)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the result icons scale with the text and long names show in full`() {
        launchAfterWrongPick()

        // As big as 24 sp text at this scale (Android 14+ scales large text less than 2x).
        val expected = with(compose.density) { 24.sp.toDp() }
        assertTrue("icon should grow, expected $expected", expected > 30.dp)
        assertEquals(expected.value, iconWidth("correct answer").value, 0.5f)
        assertEquals(expected.value, iconWidth("your answer, incorrect").value, 0.5f)
        compose.onNodeWithText("Democratic Republic of the Congo", useUnmergedTree = true).assertTextNotClipped()
        compose.onNodeWithText("Central African Republic", useUnmergedTree = true).assertTextNotClipped()
    }
}
