package com.geoquiz.app.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.testutil.MainDispatcherRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the three modes added in Phase 7 (task 7.4, modes C): Currencies (7.1),
 * Rivers, mountains and lakes (7.2) and Flag speed round (7.3), each in light, dark (`+night`)
 * and 200% font scale where the state is a primary one. Phone goldens use Pixel 5, like
 * [KeyScreensScreenshotTest]/[NewModesScreenshotTest]/[NewModesBScreenshotTest].
 *
 * See [NewModesCScreenshotScreens]'s class doc for how every fixture is built deterministically
 * (synthetic flag codes where several flags show at once; the real bundled map/physical-feature
 * data for Rivers, mountains and lakes, the first real visual check of that mode's map rendering).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class NewModesCScreenshotTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // ---- Currencies: setup screen -------------------------------------------------------------

    @Test
    fun currenciesSetup_light() = compose.captureScreen("currencies_setup_light", NewModesCScreenshotScreens.currenciesSetup())

    @Test
    @Config(qualifiers = "+night")
    fun currenciesSetup_dark() = compose.captureScreen("currencies_setup_dark", NewModesCScreenshotScreens.currenciesSetup())

    @Test
    @Config(fontScale = 2f)
    fun currenciesSetup_font200() = compose.captureScreen("currencies_setup_font200", NewModesCScreenshotScreens.currenciesSetup())

    // ---- Currencies: a non-shared currency's typed question (Normal) --------------------------

    @Test
    fun currenciesTypedQuestion_light() =
        compose.captureScreen("currencies_typed_question_light", NewModesCScreenshotScreens.currenciesTypedQuestion())

    @Test
    @Config(qualifiers = "+night")
    fun currenciesTypedQuestion_dark() =
        compose.captureScreen("currencies_typed_question_dark", NewModesCScreenshotScreens.currenciesTypedQuestion())

    // ---- Currencies: the post-answer fact banner (correct answer, fact present) ---------------

    @Test
    fun currenciesFactBanner_light() =
        compose.captureScreen("currencies_fact_banner_light", NewModesCScreenshotScreens.currenciesFactBanner())

    @Test
    @Config(qualifiers = "+night")
    fun currenciesFactBanner_dark() =
        compose.captureScreen("currencies_fact_banner_dark", NewModesCScreenshotScreens.currenciesFactBanner())

    // ---- Currencies: a shared currency's choice question (highest-risk render, D50) -----------

    @Test
    fun currenciesSharedChoice_light() =
        compose.captureScreen("currencies_shared_choice_light", NewModesCScreenshotScreens.currenciesSharedChoiceQuestion())

    @Test
    @Config(qualifiers = "+night")
    fun currenciesSharedChoice_dark() =
        compose.captureScreen("currencies_shared_choice_dark", NewModesCScreenshotScreens.currenciesSharedChoiceQuestion())

    @Test
    @Config(fontScale = 2f)
    fun currenciesSharedChoice_font200() =
        compose.captureScreen("currencies_shared_choice_font200", NewModesCScreenshotScreens.currenciesSharedChoiceQuestion(Difficulty.HARD))

    // ---- Rivers, mountains and lakes: setup screen (tab picker) -------------------------------

    @Test
    fun physicalFeaturesSetup_light() =
        compose.captureScreen("physical_features_setup_light", NewModesCScreenshotScreens.physicalFeaturesSetup())

    @Test
    @Config(qualifiers = "+night")
    fun physicalFeaturesSetup_dark() =
        compose.captureScreen("physical_features_setup_dark", NewModesCScreenshotScreens.physicalFeaturesSetup())

    @Test
    @Config(fontScale = 2f)
    fun physicalFeaturesSetup_font200() =
        compose.captureScreen("physical_features_setup_font200", NewModesCScreenshotScreens.physicalFeaturesSetup())

    // ---- Rivers, mountains and lakes: Rivers tab (LINE geometry, rivers_50m) ------------------

    @Test
    fun physicalFeaturesRivers_light() =
        compose.captureScreen("physical_features_rivers_light", NewModesCScreenshotScreens.physicalFeaturesRivers())

    @Test
    @Config(qualifiers = "+night")
    fun physicalFeaturesRivers_dark() =
        compose.captureScreen("physical_features_rivers_dark", NewModesCScreenshotScreens.physicalFeaturesRivers())

    @Test
    @Config(fontScale = 2f)
    fun physicalFeaturesRivers_font200() =
        compose.captureScreen("physical_features_rivers_font200", NewModesCScreenshotScreens.physicalFeaturesRivers())

    // ---- Rivers, mountains and lakes: Mountains tab, a range (POLYGON geometry, regions_50m) --

    @Test
    fun physicalFeaturesMountainsRange_light() =
        compose.captureScreen("physical_features_mountains_range_light", NewModesCScreenshotScreens.physicalFeaturesMountainsRange())

    @Test
    @Config(qualifiers = "+night")
    fun physicalFeaturesMountainsRange_dark() =
        compose.captureScreen("physical_features_mountains_range_dark", NewModesCScreenshotScreens.physicalFeaturesMountainsRange())

    // ---- Rivers, mountains and lakes: Mountains tab, a peak (POINT geometry, geo_points_50m) --

    @Test
    fun physicalFeaturesMountainsPeak_light() =
        compose.captureScreen("physical_features_mountains_peak_light", NewModesCScreenshotScreens.physicalFeaturesMountainsPeak())

    @Test
    @Config(qualifiers = "+night")
    fun physicalFeaturesMountainsPeak_dark() =
        compose.captureScreen("physical_features_mountains_peak_dark", NewModesCScreenshotScreens.physicalFeaturesMountainsPeak())

    // ---- Rivers, mountains and lakes: Lakes tab (POLYGON geometry, lakes_50m) -----------------

    @Test
    fun physicalFeaturesLakes_light() =
        compose.captureScreen("physical_features_lakes_light", NewModesCScreenshotScreens.physicalFeaturesLakes())

    @Test
    @Config(qualifiers = "+night")
    fun physicalFeaturesLakes_dark() =
        compose.captureScreen("physical_features_lakes_dark", NewModesCScreenshotScreens.physicalFeaturesLakes())

    // ---- Flag speed round: setup screen --------------------------------------------------------

    @Test
    fun flagSpeedSetup_light() = compose.captureScreen("flag_speed_setup_light", NewModesCScreenshotScreens.flagSpeedSetup())

    @Test
    @Config(qualifiers = "+night")
    fun flagSpeedSetup_dark() = compose.captureScreen("flag_speed_setup_dark", NewModesCScreenshotScreens.flagSpeedSetup())

    // ---- Flag speed round: live round (flag, options, combo counter, countdown) ---------------

    @Test
    fun flagSpeedMidRound_light() =
        compose.captureScreen("flag_speed_mid_round_light", NewModesCScreenshotScreens.flagSpeedMidRound())

    @Test
    @Config(qualifiers = "+night")
    fun flagSpeedMidRound_dark() =
        compose.captureScreen("flag_speed_mid_round_dark", NewModesCScreenshotScreens.flagSpeedMidRound())

    @Test
    @Config(fontScale = 2f)
    fun flagSpeedMidRound_font200() =
        compose.captureScreen("flag_speed_mid_round_font200", NewModesCScreenshotScreens.flagSpeedMidRound())

    // ---- Flag speed round: post-round review (explanations, "did you know" card) --------------

    @Test
    fun flagSpeedReview_light() = compose.captureScreen("flag_speed_review_light", NewModesCScreenshotScreens.flagSpeedReview())

    @Test
    @Config(qualifiers = "+night")
    fun flagSpeedReview_dark() = compose.captureScreen("flag_speed_review_dark", NewModesCScreenshotScreens.flagSpeedReview())
}
