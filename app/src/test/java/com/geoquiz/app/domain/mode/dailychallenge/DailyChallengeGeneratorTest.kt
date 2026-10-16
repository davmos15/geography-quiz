package com.geoquiz.app.domain.mode.dailychallenge

import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.model.Country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 8.1: determinism, kind distribution, no duplicate targets, different dates differ. */
class DailyChallengeGeneratorTest {

    /** 40 synthetic countries, alternating region, all with a non-blank capital. */
    private val countries: List<Country> = (1..40).map { i ->
        Country(
            code = "C%02d".format(i),
            name = "Country $i",
            officialName = "Country $i",
            region = if (i % 2 == 0) "Europe" else "Asia",
            subregion = if (i % 2 == 0) "Northern Europe" else "Eastern Asia",
            nameLength = 9,
            capital = "Capital $i"
        )
    }

    @Test
    fun `the same date always produces the same 10 questions, including choice option order`() {
        val a = DailyChallengeGenerator.generate("2026-10-16", countries)
        val b = DailyChallengeGenerator.generate("2026-10-16", countries)

        assertEquals(a.map { it.target.code to it.kind }, b.map { it.target.code to it.kind })
        assertEquals(
            a.mapNotNull { it.choice }.map { it.options.map(ChoiceOption::code) },
            b.mapNotNull { it.choice }.map { it.options.map(ChoiceOption::code) }
        )
    }

    @Test
    fun `determinism holds across many different dates, not just one`() {
        listOf("2020-01-01", "2024-02-29", "2026-10-16", "2099-12-31").forEach { date ->
            val a = DailyChallengeGenerator.generate(date, countries)
            val b = DailyChallengeGenerator.generate(date, countries)
            assertEquals("date $date", a.map { it.target.code to it.kind }, b.map { it.target.code to it.kind })
        }
    }

    @Test
    fun `two far-apart dates produce different rounds`() {
        val a = DailyChallengeGenerator.generate("2020-01-01", countries)
        val b = DailyChallengeGenerator.generate("2099-12-31", countries)

        assertNotEquals(a.map { it.target.code }, b.map { it.target.code })
    }

    @Test
    fun `ten questions are drawn with the documented 3-3-2-2 kind mix`() {
        val questions = DailyChallengeGenerator.generate("2026-01-01", countries)

        assertEquals(10, questions.size)
        val counts = questions.groupingBy { it.kind }.eachCount()
        assertEquals(3, counts[DailyChallengeQuestionKind.TYPED_COUNTRY])
        assertEquals(3, counts[DailyChallengeQuestionKind.TYPED_CAPITAL])
        assertEquals(2, counts[DailyChallengeQuestionKind.CHOICE_FLAG])
        assertEquals(2, counts[DailyChallengeQuestionKind.CHOICE_CAPITAL])
    }

    @Test
    fun `no duplicate target countries within one day's round`() {
        val questions = DailyChallengeGenerator.generate("2026-05-05", countries)

        assertEquals(questions.size, questions.map { it.target.code }.distinct().size)
    }

    @Test
    fun `choice questions target the right country with the right prompt type, typed kinds have none`() {
        val questions = DailyChallengeGenerator.generate("2026-03-03", countries)

        questions.filter { it.kind == DailyChallengeQuestionKind.CHOICE_FLAG }.forEach { q ->
            assertEquals(q.target.code, q.choice!!.targetCode)
            assertTrue(q.choice!!.prompt is ChoicePrompt.FlagOf)
        }
        questions.filter { it.kind == DailyChallengeQuestionKind.CHOICE_CAPITAL }.forEach { q ->
            assertEquals(q.target.code, q.choice!!.targetCode)
            assertTrue(q.choice!!.prompt is ChoicePrompt.CapitalOf)
        }
        questions.filter {
            it.kind == DailyChallengeQuestionKind.TYPED_COUNTRY || it.kind == DailyChallengeQuestionKind.TYPED_CAPITAL
        }.forEach { q -> assertNull(q.choice) }
    }

    @Test
    fun `fewer countries than the question count still returns one question per country, no crash`() {
        val few = countries.take(3)

        val questions = DailyChallengeGenerator.generate("2026-07-07", few)

        assertEquals(3, questions.size)
        assertEquals(3, questions.map { it.target.code }.distinct().size)
    }

    @Test
    fun `an empty country list returns an empty round`() {
        assertTrue(DailyChallengeGenerator.generate("2026-07-08", emptyList()).isEmpty())
    }

    @Test
    fun `seedFor matches the documented String hashCode derivation`() {
        assertEquals("2026-10-16".hashCode().toLong(), DailyChallengeGenerator.seedFor("2026-10-16"))
        assertEquals("2020-01-01".hashCode().toLong(), DailyChallengeGenerator.seedFor("2020-01-01"))
    }
}
