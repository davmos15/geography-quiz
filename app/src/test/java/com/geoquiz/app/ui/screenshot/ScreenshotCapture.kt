package com.geoquiz.app.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory

/**
 * Up to 1% of pixels may differ before a comparison fails. Goldens are recorded on Windows and
 * verified on Linux in CI; Robolectric's native graphics bundle the same fonts on both, but
 * anti-aliasing at glyph edges can differ slightly. Layout, colour, theme and font-scale
 * regressions change far more than 1% of the image; a change of a few characters may not, so
 * these tests guard the look of a screen and the Compose UI tests guard its content.
 * Images are stored at half size, which keeps the repository small and also evens out edge
 * anti-aliasing.
 */
val SCREENSHOT_OPTIONS = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01F),
    recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)
)

/**
 * Renders [content] in the app theme (light or dark from the `+night` qualifier, text size from
 * the configuration's font scale), runs [prepare] (e.g. scrolling a card into view) and
 * captures the whole window as `<roborazzi output dir>/<name>.png`.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.captureScreen(
    name: String,
    content: @Composable () -> Unit,
    prepare: ComposeContentTestRule.() -> Unit = {}
) {
    setContent { GeographyQuizTheme { content() } }
    waitForIdle()
    prepare()
    waitForIdle()
    onRoot().captureRoboImage(
        filePath = "${roborazziSystemPropertyOutputDirectory()}/$name.png",
        roborazziOptions = SCREENSHOT_OPTIONS
    )
}
