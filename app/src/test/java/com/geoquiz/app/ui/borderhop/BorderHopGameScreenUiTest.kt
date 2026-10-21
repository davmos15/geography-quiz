package com.geoquiz.app.ui.borderhop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.components.WindowLayout
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One pane, at 200% text, with very little height (e.g. the keyboard open on a short phone,
 * P10-R2 M2): a long autocomplete suggestion list used to leave the field and Submit button
 * squeezed into (or pushed below) whatever was left, because the map kept a fixed minimum height
 * and the list a fixed maximum height regardless of how little room either actually left. Both
 * now give way: the map's minimum shrinks (to nothing, if it must) before the field or Submit
 * ever lose room, and the suggestion list is capped to whatever is actually left - so the field
 * and Submit always have their own, non-overlapping room.
 */
@RunWith(RobolectricTestRunner::class)
// Real text measurement, so the suggestion list's actual height at a large font is meaningful.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h260dp", fontScale = 2f)
class BorderHopGameScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val names = mapOf(
        "GBR" to "United Kingdom", "FRA" to "France", "ARG" to "Argentina",
        "AUS" to "Australia", "BRA" to "Brazil", "CAN" to "Canada", "DEU" to "Germany", "EGY" to "Egypt"
    )

    private fun name(code: String) = names[code] ?: code

    private fun launch() {
        val state = BorderHopState(
            category = QuizCategory.AllCountries,
            difficulty = Difficulty.NORMAL,
            startCode = "GBR",
            targetCode = "FRA",
            parHops = 2,
            path = listOf("GBR"),
            input = "A",
            // Six suggestions: at 200% text, each row and the whole list grow well past the
            // fixed 168 dp the list used to assume was always available.
            suggestions = listOf("ARG", "AUS", "BRA", "CAN", "DEU", "EGY")
        )
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                Surface {
                    // The space actually available once the app bar, padding and (in the real
                    // screen) the keyboard have taken their share: a short window (260 dp), via
                    // the @Config qualifiers above, so an overflow has nowhere else to go.
                    Box(modifier = Modifier.size(360.dp, 260.dp)) {
                        BorderHopActiveContent(
                            state = state,
                            mapState = BorderHopMapState(),
                            routeFrame = null,
                            countryName = ::name,
                            timerVisible = true,
                            timerSeconds = 12,
                            actions = BorderHopActions(),
                            // Forced one pane (a tall window): the real bug is the keyboard
                            // shrinking a portrait phone's one-pane height, not a landscape
                            // phone's own two-pane layout.
                            windowLayout = WindowLayout(widthDp = 360, heightDp = 600)
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the field and Submit stay visible, and don't overlap, with a long suggestion list, 200 percent text and little height`() {
        launch()

        val field = compose.onNode(hasSetTextAction()).assertIsDisplayed()
        val submit = compose.onNodeWithText("Submit").assertIsDisplayed()
        // Not just both present somewhere: Submit must sit below the field with its own room,
        // never squeezed into (or on top of) it (the field alone fits in well under 70 dp even
        // at this font scale, so a real overlap - not just rounding - fails this).
        submit.assertIsBelow(field)
    }
}
