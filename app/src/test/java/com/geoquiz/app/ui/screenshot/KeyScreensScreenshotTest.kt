package com.geoquiz.app.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.ui.home.HomeScreen
import com.geoquiz.app.ui.quiz.QuizScreen
import com.geoquiz.app.ui.results.AnswerReviewScreen
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.settings.SettingsScreen
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the key screens, each in light, dark (`+night`) and 200% font scale.
 *
 *   Record goldens:  ./gradlew recordRoborazziDebug
 *   Verify:          ./gradlew verifyRoborazziDebug
 *   Only this class: ./gradlew testDebugUnitTest -Proborazzi.test.record=true --tests "*KeyScreensScreenshotTest"
 *                    (or -Proborazzi.test.verify=true to verify)
 *   Diff images:     app/build/outputs/roborazzi/ (*_actual.png, *_compare.png after a failed verify)
 *
 * Goldens live in app/src/test/screenshots (set by `roborazzi { outputDir }` in
 * app/build.gradle.kts) and are committed. A plain `testDebugUnitTest` renders these screens but
 * neither records nor compares.
 *
 * Determinism: real screen ViewModels over fixed fakes ([ScreenTestFixtures]); coroutines run on
 * an unconfined test dispatcher whose virtual time never advances, and the quiz clock is frozen,
 * so the timer always shows 00:00. No network, ads, Play Games or database. The home banner ad
 * stays out because [LocalInspectionMode] is set, which is how `BannerAd` skips itself in
 * previews.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class KeyScreensScreenshotTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // ---- Quiz: in progress, hard mode, three named, one strike, near-miss feedback ----

    @Test fun quiz_light() = capture("quiz_light", quizInProgress())

    @Test @Config(qualifiers = "+night")
    fun quiz_dark() = capture("quiz_dark", quizInProgress())

    @Test @Config(fontScale = 2f)
    fun quiz_font200() = capture("quiz_font200", quizInProgress())

    // ---- Results (loaded) ----

    @Test fun results_light() = capture("results_light", results())

    @Test @Config(qualifiers = "+night")
    fun results_dark() = capture("results_dark", results())

    @Test @Config(fontScale = 2f)
    fun results_font200() = capture("results_font200", results())

    // ---- Answer review ----

    @Test fun answerReview_light() = capture("answer_review_light", answerReview())

    @Test @Config(qualifiers = "+night")
    fun answerReview_dark() = capture("answer_review_dark", answerReview())

    @Test @Config(fontScale = 2f)
    fun answerReview_font200() = capture("answer_review_font200", answerReview())

    // ---- Countries home (with a "Resume quiz" card) ----

    @Test fun home_light() = capture("home_countries_light", countriesHome())

    @Test @Config(qualifiers = "+night")
    fun home_dark() = capture("home_countries_dark", countriesHome())

    @Test @Config(fontScale = 2f)
    fun home_font200() = capture("home_countries_font200", countriesHome())

    // ---- Settings ----

    @Test fun settings_light() = capture("settings_light", settings())

    @Test @Config(qualifiers = "+night")
    fun settings_dark() = capture("settings_dark", settings())

    @Test @Config(fontScale = 2f)
    fun settings_font200() = capture("settings_font200", settings())

    // ---- Screens: ViewModels are built here, once per test, outside composition ----

    private fun quizInProgress(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.QuizHarness(
            countries = ScreenTestFixtures.EIGHT,
            hardMode = true
        ).viewModel
        // Three named, one strike, then a typo, which hard mode answers with "check the spelling".
        listOf("France", "Peru", "Japan", "Narnia", "Germnay").forEach {
            viewModel.onInputChange(it)
            viewModel.onSubmitAnswer()
        }
        return { QuizScreen(onQuizComplete = {}, onNavigateHome = {}, viewModel = viewModel) }
    }

    private fun results(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.resultsViewModel()
        return {
            ResultsScreen(
                onPlayAgain = { _, _, _, _ -> },
                onGoHome = {},
                onViewAnswers = {},
                viewModel = viewModel
            )
        }
    }

    private fun answerReview(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.answerReviewViewModel()
        return { AnswerReviewScreen(onNavigateBack = {}, onGoHome = {}, viewModel = viewModel) }
    }

    private fun countriesHome(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.homeViewModel()
        return {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                HomeScreen(
                    onNavigateToCategory = {},
                    onNavigateToSettings = {},
                    onNavigateToStats = {},
                    onStartQuiz = { _, _ -> },
                    viewModel = viewModel
                )
            }
        }
    }

    private fun settings(): @Composable () -> Unit {
        val viewModel = ScreenTestFixtures.settingsViewModel()
        return { SettingsScreen(onNavigateBack = {}, viewModel = viewModel) }
    }

    /**
     * Renders [content] in the app theme (light or dark from the `+night` qualifier, text size
     * from the configuration's font scale) and captures the whole window.
     */
    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { GeographyQuizTheme { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            filePath = "${roborazziSystemPropertyOutputDirectory()}/$name.png",
            roborazziOptions = OPTIONS
        )
    }

    companion object {
        /**
         * Up to 1% of pixels may differ before a comparison fails. Goldens are recorded on
         * Windows and verified on Linux in CI; Robolectric's native graphics bundle the same
         * fonts on both, but anti-aliasing at glyph edges can differ slightly. Layout, colour,
         * theme and font-scale regressions change far more than 1% of the image; a change of a
         * few characters may not, so these tests guard the look of a screen and the Compose UI
         * tests guard its content.
         * Images are stored at half size, which keeps the repository small and also evens out
         * edge anti-aliasing.
         */
        val OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01F),
            recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)
        )
    }
}
