package com.geoquiz.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCategoryStrictParseTest {

    /** One of every category, with route values as the category list builds them. */
    private val samples: List<QuizCategory> = listOf(
        QuizCategory.AllCountries,
        QuizCategory.StartingWithLetter('A'),
        QuizCategory.EndingWithLetter('Z'),
        QuizCategory.ContainingLetter('Q'),
        QuizCategory.ByRegion("Americas"),
        QuizCategory.BySubregion("South-Eastern Asia"),
        QuizCategory.BySubregion("Australia and New Zealand"),
        QuizCategory.ByNameLengthRange(6, 6, ""),
        QuizCategory.ByWordCount(1, ""),
        QuizCategory.ByWordCount(2, ""),
        QuizCategory.ByWordCount(-2, ""),
        QuizCategory.EndingWithSuffix("stan"),
        QuizCategory.ContainingWord("Guinea"),
        QuizCategory.DoubleLetter,
        QuizCategory.ConsonantCluster,
        QuizCategory.RepeatedLetter3,
        QuizCategory.RepeatedLetter4,
        QuizCategory.StartsEndsSame,
        QuizCategory.AllVowelsPresent,
        QuizCategory.IslandCountries,
        QuizCategory.UniqueLetters,
        QuizCategory.CardinalDirection,
        QuizCategory.CapitalMatchesCountry,
        QuizCategory.EndingInVowel,
        QuizCategory.SingleVowelType,
        QuizCategory.FlagSingleColor("red"),
        QuizCategory.FlagColorCombo(listOf("blue", "red", "white")),
        QuizCategory.FlagColorCount(3),
        QuizCategory.FlagElement("coat_of_arms")
    )

    @Test
    fun `every category the app builds parses strictly to the same route`() {
        samples.forEach { category ->
            val parsed = QuizCategory.fromRouteOrNull(category.typeKey, category.valueKey)
            assertNotNull("$category should parse", parsed)
            assertEquals(category.typeKey, parsed!!.typeKey)
            assertEquals(category.valueKey, parsed.valueKey)
            assertTrue("$category display name", parsed.displayName.isNotBlank())
        }
    }

    @Test
    fun `strict parse agrees with the lenient parse for valid routes`() {
        samples.forEach { category ->
            assertEquals(
                QuizCategory.fromRoute(category.typeKey, category.valueKey).typeKey,
                QuizCategory.fromRouteOrNull(category.typeKey, category.valueKey)!!.typeKey
            )
        }
    }

    @Test
    fun `unknown types return null instead of all countries`() {
        assertNull(QuizCategory.fromRouteOrNull("nonsense", "_"))
        assertEquals(QuizCategory.AllCountries, QuizCategory.fromRoute("nonsense", "_"))
    }

    @Test
    fun `length and word count routes get readable labels`() {
        assertEquals("6 letters", QuizCategory.fromRouteOrNull("lengthrange", "6-6")!!.displayName)
        assertEquals("4–8 letters", QuizCategory.fromRouteOrNull("lengthrange", "4-8")!!.displayName)
        assertEquals("Multi-Word Names", QuizCategory.fromRouteOrNull("wordcount", "-2")!!.displayName)
    }

    @Test
    fun `mode rules match the category screens`() {
        val flag = QuizCategory.FlagSingleColor("red")
        assertTrue(flag.isOfferedIn(QuizMode.FLAGS))
        assertFalse(flag.isOfferedIn(QuizMode.COUNTRIES))
        assertFalse(flag.isOfferedIn(QuizMode.CAPITALS))

        assertTrue(QuizCategory.CapitalMatchesCountry.isOfferedIn(QuizMode.CAPITALS))
        assertFalse(QuizCategory.CapitalMatchesCountry.isOfferedIn(QuizMode.COUNTRIES))

        assertTrue(QuizCategory.AllCountries.isOfferedIn(QuizMode.COUNTRIES))
        assertTrue(QuizCategory.AllCountries.isOfferedIn(QuizMode.CAPITALS))
        assertFalse(QuizCategory.AllCountries.isOfferedIn(QuizMode.FLAGS))
    }
}
