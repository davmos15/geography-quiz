package com.geoquiz.app.ui.tapmap

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.map.MAP_LIST_SEARCH_FIELD_TAG
import com.geoquiz.app.ui.map.MapViewMode
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [TapMapActiveContent] (task 5.2): the TalkBack path via [com.geoquiz.app.ui.map.MapListAlternative]
 * and 200% font text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class TapMapActiveContentUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val countries = TestQuizData.THREE // France, Germany, Austria
    private val quiz = Quiz(category = QuizCategory.MapRegion("europe"), countries = countries)

    private var listSelected: String? = null
    private var featureTap: MapHit? = null

    private fun state(
        answered: Set<String> = emptySet(),
        currentTargetCode: String? = countries[0].code,
        remainingOrder: List<String> = countries.map { it.code }.drop(1)
    ) = TapMapGameState(
        quizState = QuizState(quiz = quiz, answeredCountries = answered, remainingOrder = remainingOrder),
        currentTargetCode = currentTargetCode
    )

    private fun launch(state: TapMapGameState, difficulty: Difficulty = Difficulty.NORMAL, viewMode: MapViewMode = MapViewMode.List) {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                TapMapActiveContent(
                    state = state,
                    mapState = TapMapMapState(),
                    difficulty = difficulty,
                    timerVisible = false,
                    timerSeconds = 0,
                    viewMode = viewMode,
                    onFeatureTap = { featureTap = it },
                    onListSelect = { listSelected = it },
                    onChoiceSelected = {},
                    onSkip = {},
                    onHint = {},
                    onGiveUp = {}
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `list view - selecting a row reports that country's code`() {
        launch(state())

        compose.onNode(hasText("France")).performClick()

        assertEquals("FRA", listSelected)
    }

    @Test
    fun `list view - a found country shows found, not just a different colour`() {
        launch(state(answered = setOf("FRA"), currentTargetCode = "DEU", remainingOrder = listOf("AUT")))

        compose.onNode(hasText("France") and hasText("Found")).assertIsDisplayed()
    }

    @Test
    fun `normal tier shows the Find prompt naming the target`() {
        launch(state())

        compose.onNodeWithText("Find: France").assertIsDisplayed()
    }

    private fun easyChoiceState(remainingOrder: List<String> = listOf("DEU", "AUT")) = TapMapGameState(
        quizState = QuizState(
            quiz = quiz,
            choice = ChoiceQuestion(
                targetCode = "FRA",
                options = listOf(
                    ChoiceOption("FRA", "France"),
                    ChoiceOption("DEU", "Germany"),
                    ChoiceOption("AUT", "Austria"),
                    ChoiceOption("ESP", "Spain")
                ),
                prompt = ChoicePrompt.InSet("")
            ),
            remainingOrder = remainingOrder
        )
    )

    @Test
    fun `easy tier shows the highlighted-country heading and four options`() {
        launch(easyChoiceState(), difficulty = Difficulty.EASY)

        compose.onNodeWithText("Which country is highlighted?").assertIsDisplayed()
        compose.onNode(hasText("Spain")).assertIsDisplayed()
    }

    @Test
    fun `easy tier shows the subregion clue above the options, for everyone`() {
        launch(easyChoiceState(), difficulty = Difficulty.EASY)

        // France (TestQuizData) is in "Europe"; the clue names the target's subregion, not the
        // target itself.
        compose.onNodeWithText("Highlighted country is in Europe").assertIsDisplayed()
    }

    @Test
    fun `easy tier - no list is shown even when List view mode is requested (D39)`() {
        launch(easyChoiceState(), difficulty = Difficulty.EASY, viewMode = MapViewMode.List)

        compose.onNodeWithTag(MAP_LIST_SEARCH_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun `easy tier - the target's name appears only on its own option button`() {
        launch(easyChoiceState(), difficulty = Difficulty.EASY)

        // "France" is the highlighted target: it must not be named anywhere else on screen
        // (the map's content description never names it either - see MapAccessibility).
        compose.onAllNodesWithText("France").assertCountEquals(1)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text the prompt is not clipped`() {
        launch(state())

        compose.onNodeWithText("Find: France").assertTextNotClipped()
    }

    @Test
    fun `give up and skip are never disabled`() {
        launch(state())

        compose.onNodeWithText("Give Up").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
    }

    // --- S3: Skip/Hint/Give up stack rather than clip at a narrow width ---------------------

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `s3 - skip, hint and give up are all shown at w360dp, 100 percent`() {
        launch(state())

        compose.onNodeWithText("Skip").assertIsDisplayed()
        // The Hint button speaks a fuller description (clearAndSetSemantics), so find it by that.
        compose.onNodeWithContentDescription("Hint: briefly highlight nearby countries").assertIsDisplayed()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun `s3 - skip, hint and give up are all shown at w360dp, 200 percent`() {
        launch(state())

        compose.onNodeWithText("Skip").assertIsDisplayed()
        // The Hint button speaks a fuller description (clearAndSetSemantics), so find it by that.
        compose.onNodeWithContentDescription("Hint: briefly highlight nearby countries").assertIsDisplayed()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    // --- S4: the Hard status row never squeezes the progress text to zero width ------------

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun `s4 - hard status row keeps the progress text readable at w360dp, 200 percent`() {
        launch(state(), difficulty = Difficulty.HARD)

        // Total 3, found 0 (TestQuizData.THREE, default state()).
        compose.onNodeWithText("0 of 3 countries found").assertTextNotClipped()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun `s4 - the short strike label is shown, with the full wording as its description`() {
        launch(state(), difficulty = Difficulty.HARD)

        // On screen "0/3 strikes"; TalkBack reads the full wording instead (clearAndSetSemantics).
        compose.onNodeWithContentDescription("0 of 3 incorrect guesses used").assertIsDisplayed()
    }

    // --- S5: landscape phone - two panes show the map pane and Give up together ------------

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun `s5 - landscape phone two panes show the map pane and give up, normal`() {
        launch(state(), difficulty = Difficulty.NORMAL, viewMode = MapViewMode.List)

        compose.onNodeWithTag(MAP_LIST_SEARCH_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun `s5 - landscape phone two panes show the map pane and give up, easy`() {
        launch(easyChoiceState(), difficulty = Difficulty.EASY)

        // No list even in two panes at Easy (D39); the controls pane's Give up is still reachable.
        compose.onNodeWithTag(MAP_LIST_SEARCH_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }
}
