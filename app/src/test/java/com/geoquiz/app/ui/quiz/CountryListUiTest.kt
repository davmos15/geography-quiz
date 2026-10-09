package com.geoquiz.app.ui.quiz

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.assertIsRightOf
import com.geoquiz.app.ui.assertLineCount
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.quiz.components.CountryList
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Capitals list with the country hint on a narrow phone (3.6). */
@RunWith(RobolectricTestRunner::class)
// Real text measurement, so line counts are meaningful.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class CountryListUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val stVincent = TestQuizData.country(
        "VCT", "Saint Vincent and the Grenadines", "Kingstown", region = "Americas"
    )

    @Test
    fun `at 100 percent text a long country with a short capital stays on one line`() {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                // The quiz screen's 16 dp padding.
                CountryList(
                    countries = listOf(stVincent),
                    answeredCodes = setOf("VCT"),
                    quizMode = QuizMode.CAPITALS,
                    showCountryHint = true,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        compose.waitForIdle()

        val country = compose.onNodeWithText("Saint Vincent and the Grenadines", useUnmergedTree = true)
        val capital = compose.onNodeWithText("Kingstown", useUnmergedTree = true)
        country.assertIsDisplayed().assertTextNotClipped().assertLineCount(1)
        capital.assertIsDisplayed().assertTextNotClipped().assertLineCount(1).assertIsRightOf(country)
    }
}
