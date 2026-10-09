package com.geoquiz.app.ui.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The hidden debug menu entry on the Settings title (now in the wrapping top bar, 3.6). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SettingsScreenUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var debugMenuOpened = 0

    private fun launch() {
        val viewModel = ScreenTestFixtures.settingsViewModel()
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                SettingsScreen(
                    onOpenDebugMenu = { debugMenuOpened++ },
                    viewModel = viewModel
                )
            }
        }
        compose.waitForIdle()
    }

    /** The top-bar title (a heading), not the bottom-bar tab or anything else named Settings. */
    private val title get() = compose.onNode(
        hasText("Settings") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    )

    @Test
    fun `tapping the title 7 times opens the debug menu once`() {
        launch()

        repeat(6) { title.performClick() }
        compose.waitForIdle()
        assertEquals("opened too early", 0, debugMenuOpened)

        title.performClick()
        compose.waitForIdle()
        assertEquals(1, debugMenuOpened)
    }
}
