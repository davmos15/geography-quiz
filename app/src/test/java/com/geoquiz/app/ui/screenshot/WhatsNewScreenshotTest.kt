package com.geoquiz.app.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.geoquiz.app.ui.whatsnew.WhatsNewDialog
import com.geoquiz.app.ui.whatsnew.whatsNewSections
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The one-off "What's new" screen (P10-F), with every mode's flag on. The dialog is its own
 * window, so the whole screen is captured (same approach as [EngagementScreenshotTest]'s share
 * dialog), not just the content root. Not part of the committed goldens: the gate run records
 * these once, at the phase gate, like every other screen (D19).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class WhatsNewScreenshotTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val sections = whatsNewSections(FeatureFlag.entries.toSet())

    @Test
    fun whatsNew_light() = capture("whats_new_light")

    @Test
    @Config(qualifiers = "+night")
    fun whatsNew_dark() = capture("whats_new_dark")

    @Test
    @Config(fontScale = 2f)
    fun whatsNew_font200() = capture("whats_new_font200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        compose.setContent {
            GeographyQuizTheme {
                WhatsNewDialog(sections = sections, onDismiss = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage(
            filePath = "${roborazziSystemPropertyOutputDirectory()}/$name.png",
            roborazziOptions = SCREENSHOT_OPTIONS
        )
    }
}
