package com.geoquiz.app.ui.components

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Screen reader descriptions built from the real strings.xml (English). */
@RunWith(RobolectricTestRunner::class)
class A11yTextTest {

    private lateinit var res: Resources

    private val france = Country(
        code = "FRA",
        name = "France",
        officialName = "French Republic",
        region = "Europe",
        subregion = "Western Europe",
        nameLength = 6,
        capital = "Paris"
    )

    @Before
    fun setUp() {
        res = ApplicationProvider.getApplicationContext<Context>().resources
    }

    @Test
    fun `duration uses minutes and seconds with plurals`() {
        assertEquals("0 seconds", A11yText.duration(res, 0))
        assertEquals("1 second", A11yText.duration(res, 1))
        assertEquals("45 seconds", A11yText.duration(res, 45))
        assertEquals("1 minute", A11yText.duration(res, 60))
        assertEquals("2 minutes", A11yText.duration(res, 120))
        assertEquals("1 minute 1 second", A11yText.duration(res, 61))
        assertEquals("3 minutes 12 seconds", A11yText.duration(res, 192))
        assertEquals("75 minutes 5 seconds", A11yText.duration(res, 75 * 60 + 5))
    }

    @Test
    fun `duration treats negative time as zero`() {
        assertEquals("0 seconds", A11yText.duration(res, -5))
    }

    @Test
    fun `timer description`() {
        assertEquals("Time 3 minutes 12 seconds", A11yText.timer(res, 192))
    }

    @Test
    fun `progress names the mode and follows the total for plurals`() {
        assertEquals("12 of 197 countries named", A11yText.progress(res, QuizMode.COUNTRIES, 12, 197))
        assertEquals("0 of 54 capitals named", A11yText.progress(res, QuizMode.CAPITALS, 0, 54))
        assertEquals("3 of 10 flags identified", A11yText.progress(res, QuizMode.FLAGS, 3, 10))
        assertEquals("1 of 1 country named", A11yText.progress(res, QuizMode.COUNTRIES, 1, 1))
    }

    @Test
    fun `incorrect guess counts`() {
        assertEquals("1 of 3 incorrect guesses used", A11yText.strikes(res, 1, 3))
        assertEquals("1 incorrect guess", A11yText.incorrectGuesses(res, 1))
        assertEquals("4 incorrect guesses", A11yText.incorrectGuesses(res, 4))
    }

    @Test
    fun `answered quiz rows name the answer`() {
        assertEquals("France, answered", row(QuizMode.COUNTRIES, answered = true))
        assertEquals("France, answered", row(QuizMode.FLAGS, answered = true))
        assertEquals("Paris, answered", row(QuizMode.CAPITALS, answered = true))
        assertEquals(
            "Paris, capital of France, answered",
            row(QuizMode.CAPITALS, answered = true, hint = true)
        )
    }

    @Test
    fun `unanswered quiz rows never reveal the answer`() {
        for (mode in QuizMode.entries) {
            val text = row(mode, answered = false)
            assertEquals("Row 12, not yet answered", text)
            assertFalse(text.contains("France") || text.contains("Paris"))
        }
        // The country hint is already visible on screen in capitals mode; the capital is not.
        val hinted = row(QuizMode.CAPITALS, answered = false, hint = true)
        assertEquals("Capital of France, not yet answered", hinted)
        assertFalse(hinted.contains("Paris"))
    }

    @Test
    fun `country hint only applies in capitals mode`() {
        assertEquals("Row 12, not yet answered", row(QuizMode.COUNTRIES, answered = false, hint = true))
    }

    @Test
    fun `review rows`() {
        assertEquals("France, answered", A11yText.reviewRow(res, QuizMode.COUNTRIES, france, true))
        assertEquals("France, missed", A11yText.reviewRow(res, QuizMode.FLAGS, france, false))
        assertEquals(
            "Paris, capital of France, missed",
            A11yText.reviewRow(res, QuizMode.CAPITALS, france, false)
        )
    }

    @Test
    fun `quiz option count`() {
        assertEquals("54 countries", A11yText.quizOptionCount(res, QuizMode.COUNTRIES, 54))
        assertEquals("1 country", A11yText.quizOptionCount(res, QuizMode.FLAGS, 1))
        assertEquals("54 capitals", A11yText.quizOptionCount(res, QuizMode.CAPITALS, 54))
    }

    private fun row(mode: QuizMode, answered: Boolean, hint: Boolean = false): String =
        A11yText.quizRow(
            res = res,
            mode = mode,
            country = france,
            rowNumber = 12,
            isAnswered = answered,
            showCountryHint = hint
        )
}
