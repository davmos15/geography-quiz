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

    /**
     * Pins the exact output for three fixed dates (Phase 9 review). Everyone on the same date must
     * get the same round on every app version, so if this fails, today's questions changed for
     * players (a Kotlin `Random` algorithm change, or a generator change). That needs a decision,
     * not a golden update.
     */
    @Test
    fun `fixed dates keep producing exactly the same rounds`() {
        val expected = mapOf(
            "2026-01-01" to GOLDEN_2026_01_01,
            "2026-10-17" to GOLDEN_2026_10_17,
            "2027-02-28" to GOLDEN_2027_02_28
        )
        val actual = expected.keys.associateWith { signature(DailyChallengeGenerator.generate(it, countries)) }
        assertEquals(expected, actual)
    }

    private fun signature(questions: List<DailyChallengeQuestion>): String =
        questions.joinToString(" ") { q ->
            val options = q.choice?.options?.joinToString(",") { it.code }.orEmpty()
            "${q.target.code}:${q.kind.name.first()}${q.kind.name.last()}:$options"
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

// Each item is "<target>:<kind first+last letter>:<choice options in order>" (TY typed country,
// TL typed capital, CG flag choice, CL capital choice). Recorded 2026-10-17 (Phase 9).
private const val GOLDEN_2026_01_01 =
    "C02:CG:C02,C16,C06,C40 C37:TL: C08:TL: C36:CG:C30,C36,C02,C06 C27:TL: C35:TY: C20:CL:C10,C18,C06,C20 C16:TY: C40:TY: C11:CL:C35,C11,C25,C07"
private const val GOLDEN_2026_10_17 =
    "C35:CL:C09,C39,C25,C35 C06:TL: C20:TL: C04:CL:C38,C30,C32,C04 C24:TL: C40:TY: C14:TY: C29:TY: C01:CG:C39,C01,C25,C29 C27:CG:C13,C03,C05,C27"
private const val GOLDEN_2027_02_28 =
    "C07:TY: C22:TL: C11:TL: C16:TL: C25:TY: C10:CL:C20,C24,C10,C34 C21:CG:C17,C03,C21,C07 C03:TY: C23:CL:C17,C05,C29,C23 C38:CG:C20,C10,C06,C38"
