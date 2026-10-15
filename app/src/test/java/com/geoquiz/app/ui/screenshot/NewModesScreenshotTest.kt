package com.geoquiz.app.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.geoquiz.app.testutil.MainDispatcherRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the two modes added in Phase 5 (task 5.3): Country silhouettes (5.1) and
 * Tap the map, both variants (5.2/5.2b), each in light, dark (`+night`) and 200% font scale where
 * the brief asks. Phone goldens use Pixel 5, like [KeyScreensScreenshotTest]; the two wide
 * goldens at the bottom set their own qualifiers, like [WideLayoutsScreenshotTest].
 *
 * Determinism: [NewModesScreenshotScreens] builds every screen from fixed state - no running
 * timer (a frozen [com.geoquiz.app.domain.time.MonotonicClock] for silhouettes, a timer value
 * passed straight in for Tap the map, which keeps no clock of its own in these stateless content
 * composables), no unseeded random (country choices are fixed lists, not shuffled in the test),
 * and the real map/outline data is loaded and pre-warmed synchronously before each composable is
 * returned (see that object's class doc for how the known async-path-cache flake is avoided).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class NewModesScreenshotTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // ---- Country silhouettes: Normal, typed, lives 2 of 3, one wrong chip, a hint used -----

    @Test
    fun silhouettesNormal_light() = compose.captureScreen("silhouettes_normal_light", NewModesScreenshotScreens.silhouettesNormal())

    @Test
    @Config(qualifiers = "+night")
    fun silhouettesNormal_dark() = compose.captureScreen("silhouettes_normal_dark", NewModesScreenshotScreens.silhouettesNormal())

    @Test
    @Config(fontScale = 2f)
    fun silhouettesNormal_font200() = compose.captureScreen("silhouettes_normal_font200", NewModesScreenshotScreens.silhouettesNormal())

    // ---- Country silhouettes: Easy, four options, no generic prompt ------------------------

    @Test
    fun silhouettesEasy_light() = compose.captureScreen("silhouettes_easy_light", NewModesScreenshotScreens.silhouettesEasy())

    // ---- Country silhouettes: setup screen --------------------------------------------------

    @Test
    fun silhouettesSetup_light() = compose.captureScreen("silhouettes_setup_light", NewModesScreenshotScreens.silhouettesSetup())

    // ---- Tap the map, Find: Normal, Europe, a few found and one missed ---------------------

    @Test
    fun tapMapFind_light() = compose.captureScreen("tapmap_find_light", NewModesScreenshotScreens.tapMapFind())

    @Test
    @Config(qualifiers = "+night")
    fun tapMapFind_dark() = compose.captureScreen("tapmap_find_dark", NewModesScreenshotScreens.tapMapFind())

    @Test
    @Config(fontScale = 2f)
    fun tapMapFind_font200() = compose.captureScreen("tapmap_find_font200", NewModesScreenshotScreens.tapMapFind())

    // ---- Tap the map, Find: Easy, highlighted country, subregion clue, four options --------

    @Test
    fun tapMapFindEasy_light() = compose.captureScreen("tapmap_find_easy_light", NewModesScreenshotScreens.tapMapFindEasy())

    // ---- Tap the map, Name all: Hard, Europe, several found, recent chips, strikes 1/3 -----

    @Test
    fun tapMapNameAll_light() = compose.captureScreen("tapmap_nameall_light", NewModesScreenshotScreens.tapMapNameAll())

    @Test
    @Config(qualifiers = "+night")
    fun tapMapNameAll_dark() = compose.captureScreen("tapmap_nameall_dark", NewModesScreenshotScreens.tapMapNameAll())

    // ---- Tap the map: setup screen (Find, then Name all with Easy hidden) ------------------

    @Test
    fun tapMapSetup_light() = compose.captureScreen("tapmap_setup_light", NewModesScreenshotScreens.tapMapSetup())

    @Test
    fun tapMapSetupNameAll_light() = compose.captureScreen("tapmap_setup_nameall_light", NewModesScreenshotScreens.tapMapSetupNameAll())

    // ---- Results: a new mode, no Challenge button -------------------------------------------

    @Test
    fun resultsSilhouettes_light() = compose.captureScreen("results_silhouettes_light", NewModesScreenshotScreens.resultsSilhouettes())

    // ---- Wide: two panes ---------------------------------------------------------------------

    /** Landscape phone: the map pane and the controls pane (status, prompt, Give up) together. */
    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun tapMapFind_landscape() = compose.captureScreen("tapmap_find_landscape", NewModesScreenshotScreens.tapMapFind())

    /** Tablet: the outline/hints pane and the answer controls pane together. */
    @Test
    @Config(qualifiers = TABLET)
    fun silhouettes_tablet() = compose.captureScreen("silhouettes_tablet", NewModesScreenshotScreens.silhouettesNormal())

    companion object {
        const val TABLET = "w900dp-h600dp-xhdpi"
    }
}
