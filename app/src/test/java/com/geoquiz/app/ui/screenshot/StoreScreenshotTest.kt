package com.geoquiz.app.ui.screenshot

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Play Store listing screenshots (task 9.x, D65; lineup chosen by Dav after phase 10: Play,
 * More ways to play, typed quiz, Tap the map, silhouettes, Border hop, Australian states, Results):
 * eight plain, uncaptioned captures at
 * 1080x1920 px (portrait, xxhdpi), light theme only, written straight to
 * `<repo root>/store_assets/screenshots/NN_name.png`.
 *
 * These are **not** Roborazzi goldens: the output path is outside `src/test/screenshots` (set by
 * `roborazzi { outputDir }`), so a plain `testDebugUnitTest` would still render and overwrite
 * them. [requireStoreShots] skips the whole class unless the caller passes
 * `-Pgeoquiz.storeShots=true` (forwarded to a system property in `app/build.gradle.kts`, the same
 * way `-Pgeoquiz.perf=true` is), so `verifyRoborazziDebug`/`recordRoborazziDebug` and an ordinary
 * test run never touch these files.
 *
 * Every fixture is reused from the existing screenshot suites, so the same determinism and
 * real-asset flake guards (pre-warmed path caches, frozen clocks, seeded random) already written
 * there apply here: [ScreenshotScreens] for Play and the classic quiz, [NewModesScreenshotScreens]
 * for Tap the map and Country silhouettes, [NewModesBScreenshotScreens] for Border hop and
 * Australian states.
 *
 * Run them on the **release** unit-test variant, so the header shows the real launcher name
 * (`src/debug/res` renames the app "GeoQuiz Debug"):
 * `./gradlew testReleaseUnitTest --tests "*StoreScreenshotTest" -Pgeoquiz.storeShots=true`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = StoreScreenshotTest.DEVICE)
class StoreScreenshotTest {

    /**
     * The release unit-test variant has no `ui-test-manifest` (a debug-only dependency), so
     * nothing declares the [ComponentActivity] the compose rule launches. Register it with
     * Robolectric's package manager first (see robolectric/robolectric#4736).
     */
    @get:Rule(order = 0)
    val registerHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule(order = 1)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    @Before
    fun requireStoreShots() {
        assumeTrue(
            "Store screenshots only run with ./gradlew testReleaseUnitTest -Pgeoquiz.storeShots=true",
            System.getProperty("geoquiz.storeShots") == "true"
        )
    }

    /** `app/`'s parent, i.e. the repo root, whatever directory Gradle runs the JVM test from. */
    private val outputDir: File by lazy {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        val repoRoot = requireNotNull(File(userDir).parentFile) {
            "Expected a parent directory above $userDir"
        }
        File(repoRoot, "store_assets/screenshots").apply { mkdirs() }
    }

    /**
     * `captureRoboImage` is a no-op unless its [RoborazziOptions.taskType] is enabled, which a
     * plain `testDebugUnitTest` leaves as [RoborazziTaskType.None] (only the Gradle
     * `recordRoborazziDebug`/`verifyRoborazziDebug` tasks set the `roborazzi.test.*` system
     * properties that would enable it). These are plain file writes, not a golden comparison, so
     * every capture here forces [RoborazziTaskType.Record] directly, at full resolution (no
     * [SCREENSHOT_OPTIONS] half-size, which is only for keeping committed goldens small).
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private val forceCapture = RoborazziOptions(taskType = RoborazziTaskType.Record)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun shoot(
        name: String,
        content: @Composable () -> Unit,
        prepare: ComposeContentTestRule.() -> Unit = {}
    ) {
        compose.setContent { GeographyQuizTheme { content() } }
        compose.waitForIdle()
        compose.prepare()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            filePath = File(outputDir, "$name.png").path,
            roborazziOptions = forceCapture
        )
    }

    // ---- 01: Play, classic modes, Today's challenge card with a streak --------------------

    @Test
    fun shot01_play() = shoot("01_play", ScreenshotScreens.play())

    // ---- 02: Play, every mode on, "More ways to play" visible on the Countries page -------

    @Test
    fun shot02_playMoreWays() = shoot("02_play_more_ways", ScreenshotScreens.playAllModes()) {
        // Scroll to the Countries page's last "More ways to play" tile (task P10-A.5; Flag speed
        // round is on the Flags page instead, not shown by this shot).
        onNode(hasScrollAction()).performScrollToNode(hasText("States and territories"))
    }

    // ---- 03: Classic typed quiz, mid-round, ending on a correct answer ----------------------

    @Test
    fun shot03_quizCountries() = shoot(
        "03_quiz_countries",
        ScreenshotScreens.quizInProgress(hardMode = false, answers = listOf("France", "Peru", "Narnia", "Japan"))
    )

    // ---- 04: Tap the map, Find, mid-round ---------------------------------------------------

    @Test
    fun shot04_tapTheMap() = shoot("04_tap_the_map", NewModesScreenshotScreens.tapMapFind())

    // ---- 05: Country silhouettes, mid-round -------------------------------------------------

    @Test
    fun shot05_silhouettes() = shoot("05_silhouettes", NewModesScreenshotScreens.silhouettesNormal())

    // ---- 06: Border hop, Normal, one hop made and a suggestion showing ----------------------

    @Test
    fun shot06_borderHop() = shoot("06_border_hop", NewModesBScreenshotScreens.borderHopMidRound())

    // ---- 07: States and territories, Australia, tap the map, mid-round ---------------------

    @Test
    fun shot07_australia() = shoot("07_australia", NewModesBScreenshotScreens.usCaTapMidRoundAu())

    // ---- 08: Results, a good (near-perfect) result ------------------------------------------

    @Test
    fun shot08_results() = shoot("08_results", goodResult())

    /** All 8 of [ScreenTestFixtures.EIGHT] named, no wrong guesses, the perfect-score bonus. */
    private fun goodResult(): @Composable () -> Unit {
        val codes = ScreenTestFixtures.EIGHT.map { it.code }
        val result = TestQuizData.completedQuiz(
            id = "result-store-good",
            countryCodes = codes,
            answeredCodes = codes
        ).copy(timeSeconds = 96, score = 9.6, perfectBonus = true)
        return ScreenshotScreens.results(result)
    }

    companion object {
        /** Portrait phone, 1080x1920 px (360x640 dp at xxhdpi, the 3x bucket). */
        const val DEVICE = "w360dp-h640dp-xxhdpi"
    }
}
