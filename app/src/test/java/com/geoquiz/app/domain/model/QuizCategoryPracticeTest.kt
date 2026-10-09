package com.geoquiz.app.domain.model

import com.geoquiz.app.testutil.TestQuizData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Practise the ones you missed" (3.5c): the Practice category and how it is built from a result. */
class QuizCategoryPracticeTest {

    // Route round trip

    @Test
    fun `practice route round trips and keeps quiz order`() {
        val practice = QuizCategory.Practice(listOf("FRA", "AUT", "DEU"))

        assertEquals("practice", practice.typeKey)
        assertEquals("FRA+AUT+DEU", practice.valueKey)
        assertEquals(practice, QuizCategory.fromRoute(practice.typeKey, practice.valueKey))
    }

    @Test
    fun `junk and repeated codes are dropped, order kept`() {
        val parsed = QuizCategory.fromRoute("practice", "PER+fra+FRANCE+FR+PER++JPN+12A+DEU")

        assertEquals(QuizCategory.Practice(listOf("PER", "JPN", "DEU")), parsed)
    }

    @Test
    fun `a practice route without valid codes falls back like any bad route`() {
        assertEquals(QuizCategory.AllCountries, QuizCategory.fromRoute("practice", ""))
        assertEquals(QuizCategory.AllCountries, QuizCategory.fromRoute("practice", "_"))
        assertEquals(QuizCategory.AllCountries, QuizCategory.fromRoute("practice", "abc+12+"))
    }

    @Test
    fun `strict parse never returns a practice set`() {
        assertNull(QuizCategory.fromRouteOrNull("practice", "FRA+DEU"))
        assertNull(QuizCategory.fromRouteOrNull("practice", "FRA"))
        assertNull(QuizCategory.fromRouteOrNull("practice", "_"))
    }

    @Test
    fun `practice is offered in no mode and is not recorded`() {
        val practice = QuizCategory.Practice(listOf("FRA"))

        QuizMode.entries.forEach { assertFalse("offered in $it", practice.isOfferedIn(it)) }
        assertFalse(practice.isRecorded)
        assertFalse(practice.isFlagCategory)
        assertTrue(practice.displayName.isNotBlank())
    }

    @Test
    fun `every other category is recorded`() {
        listOf(
            QuizCategory.AllCountries,
            QuizCategory.StartingWithLetter('A'),
            QuizCategory.FlagSingleColor("red"),
            QuizCategory.CapitalMatchesCountry
        ).forEach { assertTrue("$it", it.isRecorded) }
    }

    // Missed items from a finished quiz

    @Test
    fun `missed codes are the unanswered items in quiz order`() {
        val result = TestQuizData.completedQuiz(
            countryCodes = listOf("FRA", "DEU", "AUT", "PER"),
            answeredCodes = listOf("DEU")
        )

        assertEquals(listOf("FRA", "AUT", "PER"), result.missedCodes())
        assertEquals(QuizCategory.Practice(listOf("FRA", "AUT", "PER")), result.practiceCategoryOrNull())
    }

    @Test
    fun `easy wrong picks count as missed`() {
        // At Easy a wrong pick never reaches answeredCodes; the item is a miss.
        val result = TestQuizData.completedQuiz(
            countryCodes = listOf("FRA", "DEU", "AUT"),
            answeredCodes = listOf("FRA", "AUT"),
            incorrectGuessStrings = listOf("Italy")
        ).copy(difficultyId = Difficulty.EASY.id)

        assertEquals(listOf("DEU"), result.missedCodes())
        assertEquals(QuizCategory.Practice(listOf("DEU")), result.practiceCategoryOrNull())
    }

    @Test
    fun `giving up with nothing answered misses every item`() {
        val result = TestQuizData.completedQuiz(
            countryCodes = listOf("PER", "FRA", "DEU"),
            answeredCodes = emptyList()
        )

        assertEquals(listOf("PER", "FRA", "DEU"), result.missedCodes())
    }

    @Test
    fun `a perfect quiz has no misses and no practice set`() {
        val result = TestQuizData.completedQuiz(
            countryCodes = listOf("FRA", "DEU", "AUT"),
            answeredCodes = listOf("FRA", "DEU", "AUT")
        )

        assertTrue(result.missedCodes().isEmpty())
        assertNull(result.practiceCategoryOrNull())
    }

    @Test
    fun `a practice result knows it is practice and offers its own misses again`() {
        val practice = QuizCategory.Practice(listOf("FRA", "DEU", "AUT"))
        val result = TestQuizData.completedQuiz(
            categoryType = practice.typeKey,
            categoryValue = practice.valueKey,
            countryCodes = practice.codes,
            answeredCodes = listOf("DEU")
        )

        assertTrue(result.isPractice)
        assertEquals(practice, result.category)
        assertEquals(QuizCategory.Practice(listOf("FRA", "AUT")), result.practiceCategoryOrNull())
        assertFalse(TestQuizData.completedQuiz().isPractice)
    }
}
