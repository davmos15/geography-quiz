package com.geoquiz.app.ui.credits

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Credits screen (L1, task 4.6): the maps section credits Natural Earth and UN M49 now that
 * Phase 4 bundles that data; the facts section credits Wikidata (Phase 6-7 data prep).
 */
@RunWith(RobolectricTestRunner::class)
class CreditsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val openedUrls = mutableListOf<String>()

    private fun launch() {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalUriHandler provides object : UriHandler {
                        override fun openUri(uri: String) {
                            openedUrls += uri
                        }
                    }
                ) {
                    CreditsScreen(onNavigateBack = {}, onOpenSourceLicences = {})
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `credits Natural Earth with a working link`() {
        launch()
        compose.onNodeWithText("Maps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Made with Natural Earth. Free vector and raster map data @ naturalearthdata.com."
        ).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("naturalearthdata.com").performScrollTo().performClick()
        assertEquals(listOf("https://www.naturalearthdata.com"), openedUrls)
    }

    @Test
    fun `credits the UN M49 standard for regions`() {
        launch()
        compose.onNodeWithText("Regions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Regions follow the UN M49 standard country or area codes for statistical use, " +
                "United Nations Statistics Division."
        ).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("unstats.un.org").performScrollTo().performClick()
        assertEquals(listOf("https://unstats.un.org/unsd/methodology/m49/"), openedUrls)
    }

    @Test
    fun `credits Wikidata with a working link`() {
        launch()
        compose.onNodeWithText("Facts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Currency facts come from Wikidata, and the names and countries of rivers, " +
                "mountains and lakes are checked against it. Available under CC0."
        ).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("wikidata.org").performScrollTo().performClick()
        assertEquals(listOf("https://www.wikidata.org"), openedUrls)
    }
}
