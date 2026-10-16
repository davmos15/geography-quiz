package com.geoquiz.app.ui.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.ui.share.LocalShareCardRenderContext
import com.geoquiz.app.ui.share.ShareCardContent
import com.geoquiz.app.ui.share.ShareCardPalette
import com.geoquiz.app.ui.share.ShareCardRenderer
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Screenshot tests for Phase 8 (engagement): the daily challenge Setup with its streak summary
 * (8.2), the "already played today" game state (D54), the Results share dialog (8.3, including
 * landscape, where the preview must shrink so its buttons stay visible) and the share card image
 * itself in both themes. The Play tab's Today's challenge card and streak chip (8.6) are in
 * [KeyScreensScreenshotTest]/[WideLayoutsScreenshotTest] (`play_*`), plus `play_done_light` here.
 * See [EngagementScreenshotScreens] for why the question screens are not captured.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class EngagementScreenshotTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val running = EngagementScreenshotScreens.streak(current = 9, freezes = 1, playedToday = false)

    // ---- Play: Today's challenge done ---------------------------------------------------------

    @Test
    fun playDone_light() = compose.captureScreen("play_done_light", ScreenshotScreens.play(playedToday = true))

    // ---- Daily challenge Setup with a streak --------------------------------------------------

    @Test
    fun dailySetup_light() = compose.captureScreen("daily_setup_light", EngagementScreenshotScreens.dailySetup(running))

    @Test
    @Config(qualifiers = "+night")
    fun dailySetup_dark() = compose.captureScreen("daily_setup_dark", EngagementScreenshotScreens.dailySetup(running))

    @Test
    @Config(fontScale = 2f)
    fun dailySetup_font200() = compose.captureScreen("daily_setup_font200", EngagementScreenshotScreens.dailySetup(running))

    @Test
    fun dailySetupPlayed_light() = compose.captureScreen(
        "daily_setup_played_light",
        EngagementScreenshotScreens.dailySetup(EngagementScreenshotScreens.streak(current = 10, freezes = 1, playedToday = true))
    )

    @Test
    fun dailySetupFreezesFull_light() = compose.captureScreen(
        "daily_setup_freezes_full_light",
        EngagementScreenshotScreens.dailySetup(EngagementScreenshotScreens.streak(current = 15, freezes = 2, playedToday = false))
    )

    // ---- Daily challenge game: already played (D54) -------------------------------------------

    @Test
    fun dailyAlreadyPlayed_light() =
        compose.captureScreen("daily_already_played_light", EngagementScreenshotScreens.dailyAlreadyPlayed())

    @Test
    @Config(fontScale = 2f)
    fun dailyAlreadyPlayed_font200() =
        compose.captureScreen("daily_already_played_font200", EngagementScreenshotScreens.dailyAlreadyPlayed())

    // ---- Results share dialog -----------------------------------------------------------------

    @Test
    fun shareDialog_light() = captureShareDialog("share_dialog_light")

    @Test
    @Config(qualifiers = "+night")
    fun shareDialog_dark() = captureShareDialog("share_dialog_dark")

    @Test
    @Config(fontScale = 2f)
    fun shareDialog_font200() = captureShareDialog("share_dialog_font200")

    @Test
    @Config(qualifiers = "+land")
    fun shareDialog_landscape() = captureShareDialog("share_dialog_landscape")

    // ---- The share card image -----------------------------------------------------------------

    @Test
    fun shareCard_light() = captureCard("share_card_light", EngagementScreenshotScreens.CARD)

    @Test
    @Config(qualifiers = "+night")
    fun shareCard_dark() = captureCard("share_card_dark", EngagementScreenshotScreens.CARD)

    @Test
    fun shareCardLongTitle_light() = captureCard("share_card_long_title_light", EngagementScreenshotScreens.CARD_LONG_TITLE)

    /** The dialog is its own window, so the whole screen is captured, not the content root. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureShareDialog(name: String) {
        val (viewModel, content) = EngagementScreenshotScreens.resultsShareDialog()
        compose.setContent {
            // Render the card on the composition's thread (see LocalShareCardRenderContext).
            CompositionLocalProvider(LocalShareCardRenderContext provides EmptyCoroutineContext) {
                GeographyQuizTheme { content() }
            }
        }
        compose.waitForIdle()
        viewModel.openSharePreview()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasContentDescription("Share card:", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        captureScreenRoboImage(
            filePath = "${roborazziSystemPropertyOutputDirectory()}/$name.png",
            roborazziOptions = SCREENSHOT_OPTIONS
        )
    }

    /** Renders [card] with the current theme's palette (light or `+night`) and captures the bitmap. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureCard(name: String, card: ShareCardContent) {
        var palette: ShareCardPalette? = null
        compose.setContent {
            GeographyQuizTheme {
                val colours = MaterialTheme.colorScheme
                palette = ShareCardPalette(
                    background = colours.background.toArgb(),
                    onBackground = colours.onBackground.toArgb(),
                    onSurfaceVariant = colours.onSurfaceVariant.toArgb(),
                    primary = colours.primary.toArgb()
                )
            }
        }
        compose.waitForIdle()
        ShareCardRenderer.render(card, palette!!, appName = "Geography Quiz").captureRoboImage(
            filePath = "${roborazziSystemPropertyOutputDirectory()}/$name.png",
            roborazziOptions = SCREENSHOT_OPTIONS
        )
    }
}
