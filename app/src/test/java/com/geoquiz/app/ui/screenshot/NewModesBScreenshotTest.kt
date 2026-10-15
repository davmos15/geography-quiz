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
 * Screenshot tests for the two modes added in Phase 6 (task 6.3, modes B): Border hop (6.1) and
 * US states and Canadian provinces (6.2), each in light, dark (`+night`) and 200% font scale
 * where the state is a primary one (the setup screens and each variant's game screen); the
 * Border hop "reached" outcome and the secondary US/CA setup shot (Canadian provinces) are light
 * and dark only, following [NewModesScreenshotTest]'s density for secondary states. Phone
 * goldens use Pixel 5, like [KeyScreensScreenshotTest]/[NewModesScreenshotTest].
 *
 * Determinism: [NewModesBScreenshotScreens] builds every screen from fixed state - a frozen
 * [com.geoquiz.app.domain.time.MonotonicClock] and a seeded [com.geoquiz.app.domain.mode.QuizRandom]
 * for Border hop, plain fixed [com.geoquiz.app.domain.model.QuizState]/[com.geoquiz.app.ui.usca.UsCaTapGameState]
 * for US/CA's stateless content composables - and the real map/outline/flag data is loaded and
 * pre-warmed synchronously before each composable is returned (see that object's class doc).
 * No round timer ticker is ever advanced (no `advanceUntilIdle()` anywhere in this suite): every
 * ViewModel call runs on [MainDispatcherRule]'s unconfined test dispatcher, whose virtual time
 * never moves, so a ticker job started by a round in progress simply suspends on its first
 * `delay()` and is never woken again during the test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class NewModesBScreenshotTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // ---- Border hop: setup screen ------------------------------------------------------------

    @Test
    fun borderHopSetup_light() = compose.captureScreen("border_hop_setup_light", NewModesBScreenshotScreens.borderHopSetup())

    @Test
    @Config(qualifiers = "+night")
    fun borderHopSetup_dark() = compose.captureScreen("border_hop_setup_dark", NewModesBScreenshotScreens.borderHopSetup())

    @Test
    @Config(fontScale = 2f)
    fun borderHopSetup_font200() = compose.captureScreen("border_hop_setup_font200", NewModesBScreenshotScreens.borderHopSetup())

    // ---- Border hop: mid-round (one hop made, path chip, current position, neighbour list) ---

    @Test
    fun borderHopMidRound_light() = compose.captureScreen("border_hop_mid_round_light", NewModesBScreenshotScreens.borderHopMidRound())

    @Test
    @Config(qualifiers = "+night")
    fun borderHopMidRound_dark() = compose.captureScreen("border_hop_mid_round_dark", NewModesBScreenshotScreens.borderHopMidRound())

    @Test
    @Config(fontScale = 2f)
    fun borderHopMidRound_font200() = compose.captureScreen("border_hop_mid_round_font200", NewModesBScreenshotScreens.borderHopMidRound())

    // ---- Border hop: target reached, outcome banner showing ----------------------------------

    @Test
    fun borderHopReached_light() = compose.captureScreen("border_hop_reached_light", NewModesBScreenshotScreens.borderHopReached())

    @Test
    @Config(qualifiers = "+night")
    fun borderHopReached_dark() = compose.captureScreen("border_hop_reached_dark", NewModesBScreenshotScreens.borderHopReached())

    // ---- US states and Canadian provinces: setup screen ---------------------------------------

    @Test
    fun usCaSetupUs_light() = compose.captureScreen("us_ca_setup_us_light", NewModesBScreenshotScreens.usCaSetupUs())

    @Test
    @Config(qualifiers = "+night")
    fun usCaSetupUs_dark() = compose.captureScreen("us_ca_setup_us_dark", NewModesBScreenshotScreens.usCaSetupUs())

    @Test
    @Config(fontScale = 2f)
    fun usCaSetupUs_font200() = compose.captureScreen("us_ca_setup_us_font200", NewModesBScreenshotScreens.usCaSetupUs())

    /** Canadian provinces selected: the DC-optional checkbox (US only) is gone. */
    @Test
    fun usCaSetupCa_light() = compose.captureScreen("us_ca_setup_ca_light", NewModesBScreenshotScreens.usCaSetupCa())

    // ---- US states and Canadian provinces: Tap variant, mid-round -----------------------------

    @Test
    fun usCaTapMidRound_light() = compose.captureScreen("us_ca_tap_mid_round_light", NewModesBScreenshotScreens.usCaTapMidRound())

    @Test
    @Config(qualifiers = "+night")
    fun usCaTapMidRound_dark() = compose.captureScreen("us_ca_tap_mid_round_dark", NewModesBScreenshotScreens.usCaTapMidRound())

    @Test
    @Config(fontScale = 2f)
    fun usCaTapMidRound_font200() = compose.captureScreen("us_ca_tap_mid_round_font200", NewModesBScreenshotScreens.usCaTapMidRound())

    // ---- US states and Canadian provinces: Name all variant, mid-round ------------------------

    @Test
    fun usCaNameAllMidRound_light() = compose.captureScreen("us_ca_name_all_mid_round_light", NewModesBScreenshotScreens.usCaNameAllMidRound())

    @Test
    @Config(qualifiers = "+night")
    fun usCaNameAllMidRound_dark() = compose.captureScreen("us_ca_name_all_mid_round_dark", NewModesBScreenshotScreens.usCaNameAllMidRound())

    /** Added after the lead's review: confirms the `AdaptiveTrailingRow` fix for D29 (200% font
     * scale overlap) applies to this screen's header too, the same as the Tap variant's. */
    @Test
    @Config(fontScale = 2f)
    fun usCaNameAllMidRound_font200() = compose.captureScreen("us_ca_name_all_mid_round_font200", NewModesBScreenshotScreens.usCaNameAllMidRound())

    // ---- US states and Canadian provinces: Multiple choice variant, mid-round -----------------

    @Test
    fun usCaChoiceMidRound_light() = compose.captureScreen("us_ca_choice_mid_round_light", NewModesBScreenshotScreens.usCaChoiceMidRound())

    @Test
    @Config(qualifiers = "+night")
    fun usCaChoiceMidRound_dark() = compose.captureScreen("us_ca_choice_mid_round_dark", NewModesBScreenshotScreens.usCaChoiceMidRound())
}
