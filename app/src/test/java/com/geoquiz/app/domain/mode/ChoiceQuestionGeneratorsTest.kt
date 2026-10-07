package com.geoquiz.app.domain.mode

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The Easy tier's question generators (3.2b, D14), over many seeds. */
class ChoiceQuestionGeneratorsTest {

    private val france = country("FRA", "France", "Paris")
    private val germany = country("DEU", "Germany", "Berlin")
    private val austria = country("AUT", "Austria", "Vienna")
    private val italy = country("ITA", "Italy", "Rome")
    private val spain = country("ESP", "Spain", "Madrid")
    private val portugal = country("PRT", "Portugal", "Lisbon")
    private val japan = country("JPN", "Japan", "Tokyo", region = "Asia")
    private val china = country("CHN", "China", "Beijing", region = "Asia")
    private val korea = country("KOR", "South Korea", "Seoul", region = "Asia")
    private val peru = country("PER", "Peru", "Lima", region = "Americas")
    private val brazil = country("BRA", "Brazil", "Brasília", region = "Americas")
    private val kenya = country("KEN", "Kenya", "Nairobi", region = "Africa")

    private val all: List<Country> =
        listOf(france, germany, austria, italy, spain, portugal, japan, china, korea, peru, brazil, kenya)
    private val europeSet = listOf(france, germany, austria)
    private val category = QuizCategory.ByRegion("Somewhere")
    private val seeds = 0 until 50

    private fun ChoiceQuestion.codes() = options.map { it.code }

    private fun assertWellFormed(question: ChoiceQuestion, target: Country, size: Int = CHOICE_OPTION_COUNT) {
        assertEquals(target.code, question.targetCode)
        assertEquals(size, question.options.size)
        assertEquals("target exactly once", 1, question.codes().count { it == target.code })
        assertEquals("codes distinct", size, question.codes().toSet().size)
        assertEquals("labels distinct", size, question.options.map { it.label.lowercase() }.toSet().size)
    }

    // Countries

    @Test
    fun `countries asks which option is in the set, with outsiders from the same region`() {
        val setCodes = europeSet.map { it.code }.toSet()
        for (seed in seeds) {
            val question = CountriesChoiceGenerator.question(france, europeSet, all, category, Random(seed))

            assertWellFormed(question, france)
            assertEquals(ChoicePrompt.InSet("Somewhere"), question.prompt)
            assertEquals("France", question.correctOption.label)
            val distractors = question.options.filter { it.code != "FRA" }
            assertTrue("distractors never in the set", distractors.none { it.code in setCodes })
            // Italy, Spain and Portugal are the only European outsiders.
            assertEquals(setOf("ITA", "ESP", "PRT"), distractors.map { it.code }.toSet())
        }
    }

    @Test
    fun `countries tops up from other regions when the region has too few outsiders`() {
        val set = listOf(japan)
        for (seed in seeds) {
            val question = CountriesChoiceGenerator.question(japan, set, all, category, Random(seed))

            assertWellFormed(question, japan)
            assertTrue(question.prompt is ChoicePrompt.InSet)
            assertTrue("same region first", question.codes().containsAll(listOf("CHN", "KOR")))
        }
    }

    @Test
    fun `countries practice always uses the flag prompt even with outsiders`() {
        // 3.5c: "which of these did you miss" would test memory, not geography.
        val practice = QuizCategory.Practice(europeSet.map { it.code })
        val setCodes = europeSet.map { it.code }.toSet()
        for (seed in seeds) {
            val question = CountriesChoiceGenerator.question(france, europeSet, all, practice, Random(seed))

            assertWellFormed(question, france)
            assertEquals(ChoicePrompt.FlagOf("FRA"), question.prompt)
            assertEquals("France", question.correctOption.label)
            // The other practice items come first, topped up with an outsider.
            assertTrue(question.codes().containsAll(setCodes))
        }
    }

    @Test
    fun `countries falls back to a flag prompt with set members when there are no outsiders`() {
        val allCategory = QuizCategory.AllCountries
        for (seed in seeds) {
            val question = CountriesChoiceGenerator.question(japan, all, all, allCategory, Random(seed))

            assertWellFormed(question, japan)
            assertEquals(ChoicePrompt.FlagOf("JPN"), question.prompt)
            assertTrue("same region first", question.codes().containsAll(listOf("CHN", "KOR")))
        }
    }

    @Test
    fun `countries falls back to a flag prompt when fewer than 3 outsiders exist`() {
        val nearlyAll = all - italy - kenya
        val setCodes = nearlyAll.map { it.code }.toSet()
        for (seed in seeds) {
            val question = CountriesChoiceGenerator.question(france, nearlyAll, all, category, Random(seed))

            assertWellFormed(question, france)
            assertEquals(ChoicePrompt.FlagOf("FRA"), question.prompt)
            assertTrue("options are set members", question.codes().all { it in setCodes })
        }
    }

    // Capitals

    @Test
    fun `capitals asks for the capital, with distinct non-blank distractors that are never the answer`() {
        val tricky = all + listOf(
            country("AAA", "Alphaland", " paris ", region = "Europe"), // same text as the answer
            country("BBB", "Betaland", "", region = "Europe"), // no capital
            country("CCC", "Gammaland", "Santiago", region = "Europe"),
            country("DDD", "Deltaland", "Santiago", region = "Europe") // shared capital
        )
        for (seed in seeds) {
            val question = CapitalsChoiceGenerator.question(france, europeSet, tricky, category, Random(seed))

            assertWellFormed(question, france)
            assertEquals(ChoicePrompt.CapitalOf("France"), question.prompt)
            assertEquals("Paris", question.correctOption.label)
            val distractors = question.options.filter { it.code != "FRA" }
            assertTrue(distractors.none { it.label.isBlank() })
            assertTrue(distractors.none { it.label.trim().equals("Paris", ignoreCase = true) })
            assertTrue("same region first", distractors.all { c -> tricky.first { it.code == c.code }.region == "Europe" })
        }
    }

    @Test
    fun `capital labels are the capitals of the countries the options stand for`() {
        val question = CapitalsChoiceGenerator.question(peru, listOf(peru), all, category, Random(1))

        for (option in question.options) {
            assertEquals(all.first { it.code == option.code }.capital, option.label)
        }
        assertTrue("same region first", question.codes().contains("BRA"))
    }

    // Flags

    @Test
    fun `flags shows the flag and offers country names, same region first`() {
        for (seed in seeds) {
            val question = FlagsChoiceGenerator.question(peru, listOf(peru), all, category, Random(seed))

            assertWellFormed(question, peru)
            assertEquals(ChoicePrompt.FlagOf("PER"), question.prompt)
            assertEquals("Peru", question.correctOption.label)
            assertTrue("same region first", question.codes().contains("BRA"))
            for (option in question.options) {
                assertEquals(all.first { it.code == option.code }.name, option.label)
            }
        }
    }

    @Test
    fun `flags distractors may come from outside the set`() {
        val question = FlagsChoiceGenerator.question(france, europeSet, all, category, Random(3))

        assertWellFormed(question, france)
        assertTrue(question.codes().any { it !in europeSet.map { c -> c.code } })
    }

    // All generators

    private val generators = listOf(CountriesChoiceGenerator, CapitalsChoiceGenerator, FlagsChoiceGenerator)

    @Test
    fun `the same seed gives the same question`() {
        for (generator in generators) {
            for (seed in seeds) {
                val a = generator.question(austria, europeSet, all, category, Random(seed))
                val b = generator.question(austria, europeSet, all, category, Random(seed))
                assertEquals(a, b)
            }
        }
    }

    @Test
    fun `options are shuffled`() {
        for (generator in generators) {
            val positions = seeds.map { seed ->
                generator.question(austria, europeSet, all, category, Random(seed)).codes().indexOf("AUT")
            }.toSet()
            assertTrue("target appears in more than one position", positions.size > 1)
        }
    }

    @Test
    fun `tiny data still gives at least 2 options`() {
        val two = listOf(france, germany)
        for (generator in generators) {
            for (seed in seeds) {
                val question = generator.question(france, listOf(france), two, category, Random(seed))
                assertWellFormed(question, france, size = 2)
            }
        }
        // Countries with one outsider: flag prompt, topped up with the outsider.
        val countries = CountriesChoiceGenerator.question(france, listOf(france), two, category, Random(0))
        assertEquals(ChoicePrompt.FlagOf("FRA"), countries.prompt)
        assertFalse(countries.prompt is ChoicePrompt.InSet)
    }
}
