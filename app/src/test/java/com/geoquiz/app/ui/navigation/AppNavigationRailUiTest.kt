package com.geoquiz.app.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The navigation rail used on wide windows instead of the bottom bar (3.7). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h600dp")
class AppNavigationRailUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val names = listOf("Play", "Stats", "Achievements", "Settings")
    private val selectedRoutes = mutableListOf<String>()

    private fun launch(selectedRoute: String = Screen.Play.route) {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                AppNavigationRail(
                    isSelected = { it == selectedRoute },
                    onSelect = { selectedRoutes += it }
                )
            }
        }
        compose.waitForIdle()
    }

    private fun tab(name: String) = compose.onNode(hasText(name).or(hasContentDescription(name)))

    @Test
    fun `the rail has the same four tabs, in order, with the current one selected`() {
        launch(selectedRoute = Screen.Achievements.route)

        names.forEach { tab(it).assertIsDisplayed() }
        names.zipWithNext().forEach { (above, below) -> tab(below).assertIsBelow(tab(above)) }
        tab("Achievements").assertIsSelected()
        tab("Play").assertIsNotSelected()

        tab("Stats").performClick()
        tab("Achievements").performClick()
        assertEquals(listOf(Screen.Stats.route), selectedRoutes)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `labels show in full, the rail widening for the longest`() {
        launch()

        names.forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed().assertTextNotClipped() }
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the rail shows icons named by description`() {
        launch(selectedRoute = Screen.Settings.route)

        names.forEach { name ->
            compose.onNode(hasContentDescription(name)).assertIsDisplayed()
            compose.onNode(hasText(name)).assertDoesNotExist()
        }
        tab("Settings").assertIsSelected()

        tab("Play").performClick()
        assertEquals(listOf(Screen.Play.route), selectedRoutes)
    }

    @Test
    fun `the rail is Material's 80 dp unless a label needs more`() {
        assertEquals(80.dp, navigationRailWidth(widestLabel = 40.dp, iconOnly = false))
        assertEquals(80.dp, navigationRailWidth(widestLabel = 64.dp, iconOnly = false))
        assertEquals(96.dp, navigationRailWidth(widestLabel = 80.dp, iconOnly = false))
        assertEquals(80.dp, navigationRailWidth(widestLabel = 200.dp, iconOnly = true))
    }
}
