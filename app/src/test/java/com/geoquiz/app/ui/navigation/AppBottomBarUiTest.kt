package com.geoquiz.app.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The four-tab bottom bar (3.4a, D22) at normal and 200% text size. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AppBottomBarUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val names = listOf("Play", "Stats", "Achievements", "Settings")
    private val selectedRoutes = mutableListOf<String>()

    private fun launch(selectedRoute: String = Screen.Play.route) {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                AppBottomBar(
                    isSelected = { it == selectedRoute },
                    onSelect = { selectedRoutes += it }
                )
            }
        }
        compose.waitForIdle()
    }

    /** A tab by its name, shown as a label or (large text) as the icon's description. */
    private fun tab(name: String) = compose.onNode(hasText(name).or(hasContentDescription(name)))

    @Test
    fun `the four tabs show their labels with the current one selected`() {
        launch(selectedRoute = Screen.Stats.route)

        names.forEach { tab(it).assertIsDisplayed() }
        compose.onNode(hasText("Achievements")).assertIsDisplayed()
        tab("Stats").assertIsSelected()
        tab("Play").assertIsNotSelected()

        tab("Achievements").performClick()
        assertEquals(listOf(Screen.Achievements.route), selectedRoutes)
    }

    @Test
    fun `clicking the selected tab does nothing`() {
        launch(selectedRoute = Screen.Play.route)

        tab("Play").performClick()

        assertEquals(emptyList<String>(), selectedRoutes)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the tabs are icons named by description and still work`() {
        launch(selectedRoute = Screen.Play.route)

        names.forEach { name ->
            compose.onNode(hasContentDescription(name)).assertIsDisplayed()
            compose.onNode(hasText(name)).assertDoesNotExist()
        }
        tab("Play").assertIsSelected()
        tab("Settings").assertIsNotSelected()

        tab("Settings").performClick()
        assertEquals(listOf(Screen.Settings.route), selectedRoutes)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h640dp", fontScale = 1.3f)
    fun `on a narrow phone with larger text the bar drops the labels before they would be cut short`() {
        launch(selectedRoute = Screen.Play.route)

        names.forEach { name ->
            compose.onNode(hasContentDescription(name)).assertIsDisplayed()
            compose.onNode(hasText(name)).assertDoesNotExist()
        }
        tab("Play").assertIsSelected()
    }
}
