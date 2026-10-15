package com.geoquiz.app.ui.navigation

import com.geoquiz.app.domain.model.QuizCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Routes use `android.net.Uri.encode` (classic [Screen.Quiz] did already), so this needs Robolectric. */
@RunWith(RobolectricTestRunner::class)
class ModeRoutesTest {

    // Classic ids go to Screen.Quiz, exactly as before this task.

    @Test
    fun `a classic mode starts a Screen Quiz route`() {
        assertEquals(
            Screen.Quiz.createRoute("countries", "all", "_"),
            ModeRoutes.start("countries", "all", "_")
        )
    }

    @Test
    fun `a classic mode keeps its difficulty and challenge id`() {
        assertEquals(
            Screen.Quiz.createRoute("capitals", "region", "Europe", challengeId = "c1", difficulty = "hard"),
            ModeRoutes.start("capitals", "region", "Europe", difficulty = "hard", challengeId = "c1")
        )
    }

    @Test
    fun `play again and practise build the same route as start for a classic mode`() {
        assertEquals(
            ModeRoutes.start("flags", "flagcolor", "red", difficulty = "easy"),
            ModeRoutes.playAgain("flags", "flagcolor", "red", difficulty = "easy")
        )
        assertEquals(
            ModeRoutes.start("countries", "practice", "FRA+DEU", difficulty = "normal"),
            ModeRoutes.practise("countries", "practice", "FRA+DEU", difficulty = "normal")
        )
    }

    // Silhouettes goes to its own screen.

    @Test
    fun `silhouettes starts its own screen, ignoring any challenge id`() {
        assertEquals(
            Screen.Silhouettes.createRoute("region", "Africa", "hard"),
            ModeRoutes.start("silhouettes", "region", "Africa", difficulty = "hard", challengeId = "ignored")
        )
    }

    @Test
    fun `silhouettes practise keeps the practice category and tier`() {
        assertEquals(
            Screen.Silhouettes.createRoute("practice", "FRA+DEU", "normal"),
            ModeRoutes.practise("silhouettes", "practice", "FRA+DEU", "normal")
        )
    }

    // Tap the map carries its variant, defaulting to "find".

    @Test
    fun `tap_map starts its own screen with the given variant`() {
        assertEquals(
            Screen.TapMap.createRoute("name_all", "all", "_", "easy"),
            ModeRoutes.start("tap_map", "all", "_", difficulty = "easy", variantId = "name_all")
        )
    }

    @Test
    fun `tap_map defaults to the find variant when none is given`() {
        assertEquals(
            Screen.TapMap.createRoute("find", "all", "_", null),
            ModeRoutes.start("tap_map", "all", "_")
        )
    }

    @Test
    fun `tap_map play again and practise keep the variant`() {
        assertEquals(
            Screen.TapMap.createRoute("name_all", "region", "Oceania", "hard"),
            ModeRoutes.playAgain("tap_map", "region", "Oceania", difficulty = "hard", variantId = "name_all")
        )
        assertEquals(
            Screen.TapMap.createRoute("name_all", "practice", "FRA+DEU", "hard"),
            ModeRoutes.practise("tap_map", "practice", "FRA+DEU", difficulty = "hard", variantId = "name_all")
        )
    }

    // Category values with route-sensitive characters (task 5.0 review).

    @Test
    fun `apostrophes, slashes and plus signs in the category value are encoded the same way for every mode`() {
        val value = "O'Brien's/Café+Co"

        assertEquals(
            Screen.Quiz.createRoute("countries", "containword", value),
            ModeRoutes.start("countries", "containword", value)
        )
        assertEquals(
            Screen.Silhouettes.createRoute("containword", value, "hard"),
            ModeRoutes.start("silhouettes", "containword", value, difficulty = "hard")
        )
        assertEquals(
            Screen.TapMap.createRoute("find", "containword", value, "hard"),
            ModeRoutes.start("tap_map", "containword", value, difficulty = "hard")
        )
    }

    // MapRegion categories (task 5.0 review): tap_map's own setup screen offers these.

    @Test
    fun `tap_map play again and practise work with a map region category`() {
        val region = QuizCategory.MapRegion("north_america")

        assertEquals(
            Screen.TapMap.createRoute("find", region.typeKey, region.valueKey, "normal"),
            ModeRoutes.playAgain("tap_map", region.typeKey, region.valueKey, difficulty = "normal")
        )
        assertEquals(
            Screen.TapMap.createRoute("name_all", region.typeKey, region.valueKey, "hard"),
            ModeRoutes.practise(
                "tap_map", region.typeKey, region.valueKey, difficulty = "hard", variantId = "name_all"
            )
        )
    }
}
