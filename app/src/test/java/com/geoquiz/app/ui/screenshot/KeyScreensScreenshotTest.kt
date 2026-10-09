package com.geoquiz.app.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.geoquiz.app.testutil.MainDispatcherRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the key screens on a phone (Pixel 5), each in light, dark (`+night`) and
 * 200% font scale. Tablet and landscape layouts are in [WideLayoutsScreenshotTest].
 *
 *   Record goldens:  ./gradlew recordRoborazziDebug
 *   Verify:          ./gradlew verifyRoborazziDebug
 *   Only these:      ./gradlew testDebugUnitTest -Proborazzi.test.record=true --tests "com.geoquiz.app.ui.screenshot.*"
 *                    (or -Proborazzi.test.verify=true to verify)
 *   Diff images:     app/build/outputs/roborazzi/ (*_actual.png, *_compare.png after a failed verify)
 *
 * Goldens live in app/src/test/screenshots (set by `roborazzi { outputDir }` in
 * app/build.gradle.kts) and are committed. A plain `testDebugUnitTest` renders these screens but
 * neither records nor compares.
 *
 * Determinism: real screen ViewModels over fixed fakes ([ScreenshotScreens]); coroutines run on
 * an unconfined test dispatcher whose virtual time never advances, and the quiz clock is frozen,
 * so the timer always shows 00:00. No network, ads, Play Games or database. The Play banner ad
 * stays out because inspection mode is set, which is how `BannerAd` skips itself in previews.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class KeyScreensScreenshotTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
        prepare: ComposeContentTestRule.() -> Unit = {}
    ) = compose.captureScreen(name, content, prepare)

    // ---- Quiz: in progress, hard mode, three named, one strike, near-miss feedback ----

    @Test fun quiz_light() = capture("quiz_light", ScreenshotScreens.quizInProgress())

    @Test @Config(qualifiers = "+night")
    fun quiz_dark() = capture("quiz_dark", ScreenshotScreens.quizInProgress())

    @Test @Config(fontScale = 2f)
    fun quiz_font200() = capture("quiz_font200", ScreenshotScreens.quizInProgress())

    // ---- Quiz, Easy tier: options panel above the list, after a wrong pick ----

    @Test fun quizEasy_light() = capture("quiz_easy_light", ScreenshotScreens.quizEasy())

    @Test @Config(qualifiers = "+night")
    fun quizEasy_dark() = capture("quiz_easy_dark", ScreenshotScreens.quizEasy())

    @Test @Config(fontScale = 2f)
    fun quizEasy_font200() = capture("quiz_easy_font200", ScreenshotScreens.quizEasy())

    // ---- Results (loaded) ----

    @Test fun results_light() = capture("results_light", ScreenshotScreens.results())

    @Test @Config(qualifiers = "+night")
    fun results_dark() = capture("results_dark", ScreenshotScreens.results())

    @Test @Config(fontScale = 2f)
    fun results_font200() = capture("results_font200", ScreenshotScreens.results())

    // ---- Results with the new achievements card, scrolled to the practise button ----

    private fun resultsWithAchievements(name: String) = capture(
        name,
        ScreenshotScreens.results(ScreenshotScreens.RESULT_WITH_ACHIEVEMENTS)
    ) {
        onNodeWithText("Practise the ones you missed (3)").performScrollTo()
    }

    @Test fun resultsAchievements_light() = resultsWithAchievements("results_achievements_light")

    @Test @Config(qualifiers = "+night")
    fun resultsAchievements_dark() = resultsWithAchievements("results_achievements_dark")

    @Test @Config(fontScale = 2f)
    fun resultsAchievements_font200() = resultsWithAchievements("results_achievements_font200")

    // ---- Answer review ----

    @Test fun answerReview_light() = capture("answer_review_light", ScreenshotScreens.answerReview())

    @Test @Config(qualifiers = "+night")
    fun answerReview_dark() = capture("answer_review_dark", ScreenshotScreens.answerReview())

    @Test @Config(fontScale = 2f)
    fun answerReview_font200() = capture("answer_review_font200", ScreenshotScreens.answerReview())

    // ---- Play tab, Countries: Today's challenge, Continue, Recommended next, Pinned ----

    @Test fun play_light() = capture("play_light", ScreenshotScreens.play())

    @Test @Config(qualifiers = "+night")
    fun play_dark() = capture("play_dark", ScreenshotScreens.play())

    @Test @Config(fontScale = 2f)
    fun play_font200() = capture("play_font200", ScreenshotScreens.play())

    // ---- Category list: difficulty selector, a row with stars, Best and pinned, rows without ----

    @Test fun categoryList_light() = capture("category_list_light", ScreenshotScreens.categoryList())

    @Test @Config(qualifiers = "+night")
    fun categoryList_dark() = capture("category_list_dark", ScreenshotScreens.categoryList())

    @Test @Config(fontScale = 2f)
    fun categoryList_font200() = capture("category_list_font200", ScreenshotScreens.categoryList())

    // ---- Achievements: gold, silver and bronze unlocked, two locked ----

    @Test fun achievements_light() = capture("achievements_light", ScreenshotScreens.achievements())

    @Test @Config(qualifiers = "+night")
    fun achievements_dark() = capture("achievements_dark", ScreenshotScreens.achievements())

    @Test @Config(fontScale = 2f)
    fun achievements_font200() = capture("achievements_font200", ScreenshotScreens.achievements())

    // ---- Settings ----

    @Test fun settings_light() = capture("settings_light", ScreenshotScreens.settings())

    @Test @Config(qualifiers = "+night")
    fun settings_dark() = capture("settings_dark", ScreenshotScreens.settings())

    @Test @Config(fontScale = 2f)
    fun settings_font200() = capture("settings_font200", ScreenshotScreens.settings())
}
