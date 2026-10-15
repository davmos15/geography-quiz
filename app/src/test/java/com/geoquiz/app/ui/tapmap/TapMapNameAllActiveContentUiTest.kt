package com.geoquiz.app.ui.tapmap

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.map.MapViewMode
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [TapMapNameAllActiveContent] (task 5.2b, D40): the pinned answer field/Submit/Give Up at every
 * size, and the found-only list alternative.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class TapMapNameAllActiveContentUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val countries = TestQuizData.THREE // France, Germany, Austria
    private val quiz = Quiz(category = QuizCategory.MapRegion("europe"), countries = countries)

    private fun launch(
        state: QuizState = QuizState(quiz = quiz),
        difficulty: Difficulty = Difficulty.NORMAL,
        viewMode: MapViewMode = MapViewMode.Map
    ) {
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                TapMapNameAllActiveContent(
                    state = state,
                    mapState = TapMapMapState(),
                    difficulty = difficulty,
                    timerVisible = false,
                    timerSeconds = 0,
                    viewMode = viewMode,
                    onInputChange = {},
                    onSubmit = {},
                    onGiveUp = {}
                )
            }
        }
        compose.waitForIdle()
    }

    private val field get() = compose.onNode(hasSetTextAction())

    @Test
    fun `the field, submit and give up are all shown at w360dp, 100 percent`() {
        launch()

        field.assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsDisplayed()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `the field, submit and give up are all shown at w360dp 100 percent, narrow`() {
        launch()

        field.assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsDisplayed()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun `the field, submit and give up are all shown at w360dp, 200 percent`() {
        launch()

        field.assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsDisplayed()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun `the field, submit and give up stay pinned at w360dp, 200 percent, with recent answers`() {
        // S8/D27: recent answers scroll with the status row; the field and buttons must still be
        // reachable, pinned below them, even when that pushes the scrolling part off screen.
        launch(state = QuizState(quiz = quiz, answeredCountries = setOf("FRA"), recentCorrect = listOf("FRA")))

        field.assertIsDisplayed()
        compose.onNodeWithText("Submit").assertIsDisplayed()
        // RecentAnswers merges its chips behind clearAndSetSemantics (its own content description),
        // so "Give Up" (not merged) is still found by text; a merged node would need its description.
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2f)
    fun `the status row text is not clipped at 200 percent`() {
        launch()

        compose.onNodeWithText("0 of 3 countries found").assertTextNotClipped()
    }

    @Test
    fun `the list view shows only found countries, never an unnamed one`() {
        launch(state = QuizState(quiz = quiz, answeredCountries = setOf("FRA")), viewMode = MapViewMode.List)

        compose.onNode(hasText("France") and hasText("Found")).assertIsDisplayed()
        compose.onNodeWithText("Germany").assertDoesNotExist()
        compose.onNodeWithText("Austria").assertDoesNotExist()
        compose.onNodeWithText("2 countries left to find").assertIsDisplayed()
    }

    @Test
    fun `the list view with nothing found yet shows the empty state, no countries named`() {
        launch(viewMode = MapViewMode.List)

        compose.onNodeWithText("No countries found yet.").assertIsDisplayed()
        compose.onAllNodesWithText("France").assertCountEquals(0)
        compose.onNodeWithText("3 countries left to find").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun `landscape phone - two panes show the found list and the field`() {
        launch(state = QuizState(quiz = quiz, answeredCountries = setOf("FRA")), viewMode = MapViewMode.List)

        compose.onNode(hasText("France") and hasText("Found")).assertIsDisplayed()
        field.assertIsDisplayed()
        compose.onNodeWithText("Give Up").assertIsDisplayed()
    }

    @Test
    fun `hard tier shows the strike count`() {
        launch(
            state = QuizState(quiz = quiz, incorrectGuesses = 1),
            difficulty = Difficulty.HARD
        )

        // The on-screen "1/3 strikes" label uses clearAndSetSemantics (StatusRow), so TalkBack's
        // (and this test's) only way to it is the full-wording content description.
        compose.onNodeWithContentDescription("1 of 3 incorrect guesses used").assertIsDisplayed()
    }
}
