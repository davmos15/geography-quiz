package com.geoquiz.app.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The back stack helpers of [AppNavigation] ([returnToPlay], [navigateToTab],
 * [ConsumeRequestedPlayMode]) on a real NavHost with the app's routes. The screens are stand-ins,
 * because the real ones need Hilt ViewModels; Play's stand-in uses the same
 * [ConsumeRequestedPlayMode] as the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AppNavigationBackStackUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    /** Modes Play was asked to show, in order. */
    private val selectedModes = mutableListOf<String>()

    private fun launch() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = Screen.Play.route) {
                composable(Screen.Play.route) { entry ->
                    ConsumeRequestedPlayMode(entry) { selectedModes += it }
                    // Screen state that must survive switching tabs.
                    var taps by rememberSaveable { mutableIntStateOf(0) }
                    Button(onClick = { taps++ }) { Text("Play taps $taps") }
                }
                composable(Screen.Stats.route) {
                    var taps by rememberSaveable { mutableIntStateOf(0) }
                    Button(onClick = { taps++ }) { Text("Stats taps $taps") }
                }
                composable(Screen.Challenges.route) { Text("Challenges screen") }
                composable(
                    Screen.CategoryList.route,
                    arguments = listOf(
                        navArgument("quizMode") { type = NavType.StringType },
                        navArgument("groupId") { type = NavType.StringType }
                    )
                ) { Text("Category ${it.arguments?.getString("groupId")}") }
                composable(
                    Screen.Results.route,
                    arguments = listOf(navArgument(Screen.Results.ARG_RESULT_ID) { type = NavType.StringType })
                ) { Text("Results screen") }
            }
        }
        compose.waitForIdle()
    }

    private fun onUi(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    private val currentRoute get() = nav.currentBackStackEntry?.destination?.route

    @Test
    fun `returnToPlay with a mode pops back to Play and shows that mode once`() {
        launch()
        onUi { nav.navigate(Screen.CategoryList.createRoute("capitals", "regions")) }
        onUi { nav.navigate(Screen.Results.createRoute("result-1")) }
        compose.onNodeWithText("Results screen").assertIsDisplayed()

        onUi { nav.returnToPlay("capitals") }

        assertEquals(Screen.Play.route, currentRoute)
        compose.onNodeWithText("Play taps 0").assertIsDisplayed()
        assertEquals(listOf("capitals"), selectedModes)
        // Handed over once: the key is cleared, so returning to Play later doesn't switch again.
        assertNull(nav.currentBackStackEntry?.savedStateHandle?.get<String>(Screen.Play.RESULT_SHOW_MODE))
        // Play is the only entry left (nothing to go back to).
        assertEquals(false, nav.previousBackStackEntry != null)
    }

    @Test
    fun `returnToPlay without a mode leaves Play's mode alone`() {
        launch()
        onUi { nav.navigate(Screen.Results.createRoute("result-1")) }

        onUi { nav.returnToPlay() }

        assertEquals(Screen.Play.route, currentRoute)
        assertEquals(emptyList<String>(), selectedModes)
    }

    @Test
    fun `returnToPlay from another tab's stack pops back to Play and shows the mode`() {
        launch()
        onUi { nav.navigateToTab(Screen.Stats.route) }
        onUi { nav.navigate(Screen.Results.createRoute("result-1")) }

        onUi { nav.returnToPlay("flags") }

        assertEquals(Screen.Play.route, currentRoute)
        compose.onNodeWithText("Play taps 0").assertIsDisplayed()
        assertEquals(listOf("flags"), selectedModes)
    }

    @Test
    fun `Play keeps its state while another tab is shown`() {
        launch()
        compose.onNodeWithText("Play taps 0").performClick()
        compose.onNodeWithText("Play taps 1").performClick()

        onUi { nav.navigateToTab(Screen.Stats.route) }
        compose.onNodeWithText("Stats taps 0").assertIsDisplayed()
        onUi { nav.navigateToTab(Screen.Play.route) }

        assertEquals(Screen.Play.route, currentRoute)
        compose.onNodeWithText("Play taps 2").assertIsDisplayed()
    }

    @Test
    fun `Stats keeps its state and its own back stack between tab switches`() {
        launch()
        onUi { nav.navigateToTab(Screen.Stats.route) }
        compose.onNodeWithText("Stats taps 0").performClick()
        onUi { nav.navigate(Screen.Challenges.route) }
        compose.onNodeWithText("Challenges screen").assertIsDisplayed()

        onUi { nav.navigateToTab(Screen.Play.route) }
        compose.onNodeWithText("Play taps 0").assertIsDisplayed()
        onUi { nav.navigateToTab(Screen.Stats.route) }

        // Back where the player left the tab, with Stats under it as before.
        assertEquals(Screen.Challenges.route, currentRoute)
        compose.onNodeWithText("Challenges screen").assertIsDisplayed()
        onUi { nav.popBackStack() }
        compose.onNodeWithText("Stats taps 1").assertIsDisplayed()
    }

    @Test
    fun `Play's own back stack comes back after another tab`() {
        launch()
        onUi { nav.navigate(Screen.CategoryList.createRoute("countries", "regions")) }

        onUi { nav.navigateToTab(Screen.Stats.route) }
        onUi { nav.navigateToTab(Screen.Play.route) }

        assertEquals(Screen.CategoryList.route, currentRoute)
        compose.onNodeWithText("Category regions").assertIsDisplayed()
    }

    @Test
    fun `selecting a tab twice does not stack it`() {
        launch()
        onUi { nav.navigateToTab(Screen.Stats.route) }
        onUi { nav.navigateToTab(Screen.Stats.route) }

        assertEquals(Screen.Stats.route, currentRoute)
        assertEquals(Screen.Play.route, nav.previousBackStackEntry?.destination?.route)
    }
}
