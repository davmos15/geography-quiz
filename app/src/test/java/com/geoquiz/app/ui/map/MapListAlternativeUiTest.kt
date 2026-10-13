package com.geoquiz.app.ui.map

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [MapListAlternative]: the TalkBack path for every map interaction (task 4.6). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class MapListAlternativeUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val france = Country("FRA", "France", "French Republic", "Europe", "Western Europe", 6, "Paris")
    private val germany = Country("DEU", "Germany", "Federal Republic of Germany", "Europe", "Western Europe", 7, "Berlin")
    private val mali = Country("MLI", "Mali", "Republic of Mali", "Africa", "Western Africa", 4, "Bamako")
    private val countries = listOf(france, germany, mali)

    private val selected = mutableListOf<String>()

    private fun launch(featureStates: Map<String, MapFeatureState> = emptyMap(), showSearch: Boolean = true) {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                MapListAlternative(
                    countries = countries,
                    featureStates = featureStates,
                    onSelect = { selected += it },
                    showSearch = showSearch,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a default row reads just the country name`() {
        launch()
        compose.onNode(hasText("France")).assertIsDisplayed()
    }

    /** The icon is a descendant of the merged, clickable row, so these need the unmerged tree. */
    private fun iconCount(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true)

    @Test
    fun `a found row merges the name with its state, and shows a tick, never colour alone`() {
        launch(mapOf("FRA" to MapFeatureState.Found))
        compose.onNode(hasText("France") and hasText("Found")).assertIsDisplayed()
        iconCount(MAP_LIST_FOUND_ICON_TAG).assertCountEquals(1)
        iconCount(MAP_LIST_WRONG_ICON_TAG).assertCountEquals(0)
    }

    @Test
    fun `a wrong row merges the name with its state, and shows a cross`() {
        launch(mapOf("DEU" to MapFeatureState.Wrong))
        compose.onNode(hasText("Germany") and hasText("Wrong")).assertIsDisplayed()
        iconCount(MAP_LIST_WRONG_ICON_TAG).assertCountEquals(1)
        iconCount(MAP_LIST_FOUND_ICON_TAG).assertCountEquals(0)
    }

    @Test
    fun `highlighted, start and end are labelled with no icon`() {
        launch(
            mapOf("FRA" to MapFeatureState.Highlighted, "DEU" to MapFeatureState.Start, "MLI" to MapFeatureState.End)
        )
        compose.onNode(hasText("France") and hasText("Highlighted")).assertIsDisplayed()
        compose.onNode(hasText("Germany") and hasText("Start")).assertIsDisplayed()
        compose.onNode(hasText("Mali") and hasText("End")).assertIsDisplayed()
        iconCount(MAP_LIST_FOUND_ICON_TAG).assertCountEquals(0)
        iconCount(MAP_LIST_WRONG_ICON_TAG).assertCountEquals(0)
    }

    @Test
    fun `selecting a row calls onSelect with its cca3 code`() {
        launch()
        compose.onNode(hasText("France")).performClick()
        assertEquals(listOf("FRA"), selected)
    }

    @Test
    fun `search filters by name, normalised like a typed answer`() {
        launch()
        compose.onNodeWithTag(MAP_LIST_SEARCH_FIELD_TAG).performTextInput("mali")
        compose.onNode(hasText("Mali")).assertIsDisplayed()
        compose.onNodeWithText("France").assertDoesNotExist()
        compose.onNodeWithText("Germany").assertDoesNotExist()
    }

    @Test
    fun `search ignores accents and case, as typed answers do`() {
        launch()
        // "France" has no diacritics in its own name, so search by a near match that would only
        // work through normalisation: extra case and surrounding space.
        compose.onNodeWithTag(MAP_LIST_SEARCH_FIELD_TAG).performTextInput("  FRANCE  ")
        compose.onNode(hasText("France")).assertIsDisplayed()
        compose.onNodeWithText("Germany").assertDoesNotExist()
    }

    @Test
    fun `no matches shows the empty state`() {
        launch()
        compose.onNodeWithTag(MAP_LIST_SEARCH_FIELD_TAG).performTextInput("Atlantis")
        compose.onNodeWithText("No countries match your search.").assertIsDisplayed()
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the name and state are not clipped`() {
        launch(mapOf("FRA" to MapFeatureState.Found))
        compose.onNodeWithText("France", useUnmergedTree = true).assertTextNotClipped()
        compose.onNodeWithText("Found", useUnmergedTree = true).assertTextNotClipped()
    }
}
