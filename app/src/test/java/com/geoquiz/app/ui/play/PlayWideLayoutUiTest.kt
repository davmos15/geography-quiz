package com.geoquiz.app.ui.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.assertIsRightOf
import com.geoquiz.app.ui.components.WidthClass
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Play tab on tablets (3.7): on a large tablet the cards get their own column beside the
 * mode switch and tiles, which use more columns. Phones are covered by [PlayScreenUiTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h800dp")
class PlayWideLayoutUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val quizzes = mutableListOf<Triple<String, String, String>>()
    private val categories = mutableListOf<Pair<String, String>>()

    private fun launch(viewModel: PlayViewModel = ScreenTestFixtures.playViewModel()) {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                GeographyQuizTheme(darkTheme = false) {
                    PlayScreen(
                        onOpenCategory = { mode, group -> categories += mode to group },
                        onStartQuiz = { mode, type, value -> quizzes += Triple(mode, type, value) },
                        onOpenMode = {},
                        viewModel = viewModel
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `on a large tablet the cards sit beside the mode switch and tiles`() {
        launch()

        val continueCard = compose.onNodeWithText("Continue")
        val recommended = compose.onNodeWithText("Recommended next")
        continueCard.assertIsDisplayed()
        recommended.assertIsDisplayed().assertIsBelow(continueCard)

        val allCountries = compose.onNodeWithText("All countries").assertIsDisplayed()
        val cardBounds = continueCard.getBoundsInRoot()
        val allBounds = allCountries.getBoundsInRoot()
        assertTrue("tiles $allBounds are not beside the cards $cardBounds", allBounds.left >= cardBounds.right)
        // Side by side, so the tiles start at the top rather than under the cards.
        assertTrue("tiles $allBounds start below the cards $cardBounds", allBounds.top < cardBounds.bottom)

        allCountries.performClick()
        assertEquals(listOf(Triple("countries", "all", "_")), quizzes)
    }

    @Test
    fun `on a large tablet the group tiles take three columns`() {
        launch()

        assertThreeTilesOnALine()
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `on a small tablet the cards stay above the switch`() {
        launch()

        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.onNodeWithText("All countries").assertIsDisplayed()
            .assertIsBelow(compose.onNodeWithText("Continue"))
    }

    @Test
    fun `phones keep two tile columns, tablets allow three`() {
        assertEquals(2, maxTileColumns(WidthClass.Compact))
        assertEquals(3, maxTileColumns(WidthClass.Medium))
        assertEquals(3, maxTileColumns(WidthClass.Expanded))
    }

    @Test
    @Config(qualifiers = "w700dp-h1000dp")
    fun `on a small tablet three group tiles share a line`() {
        launch()

        assertThreeTilesOnALine()
    }

    @Test
    @Config(qualifiers = "w1300dp-h900dp")
    fun `the tiles keep their scroll position when the layout switches`() {
        var width by mutableStateOf(1280.dp)
        val viewModel = ScreenTestFixtures.playViewModel()
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                GeographyQuizTheme(darkTheme = false) {
                    Box(modifier = Modifier.size(width, 500.dp)) {
                        PlayScreen(
                            onOpenCategory = { _, _ -> },
                            onStartQuiz = { _, _, _ -> },
                            onOpenMode = {},
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("By Region")))
            .performScrollToNode(hasText("Word Patterns"))
        compose.onNodeWithText("Word Patterns").assertIsDisplayed()

        // To one grid with the cards above the tiles: still at the tiles, not back at the top.
        width = 700.dp
        compose.waitForIdle()

        compose.onNodeWithText("Word Patterns").assertIsDisplayed()
        val continueCard = compose.onAllNodes(hasText("Continue"))
        if (continueCard.fetchSemanticsNodes().isNotEmpty()) continueCard[0].assertIsNotDisplayed()
    }

    /** The first three group tiles of Countries side by side, in order. */
    private fun assertThreeTilesOnALine() {
        val tiles = listOf("By Region", "By Subregion", "Starting Letter").map { compose.onNodeWithText(it) }
        tiles.forEach { it.assertIsDisplayed() }
        tiles[1].assertIsRightOf(tiles[0])
        tiles[2].assertIsRightOf(tiles[1])
    }
}
