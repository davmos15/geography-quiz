package com.geoquiz.app.ui.play

import com.geoquiz.app.R
import com.geoquiz.app.data.local.db.FlagColorEntity
import com.geoquiz.app.data.local.db.FlagElementEntity
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.testutil.TestQuizData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayCategoryGroupsTest {

    private val countries = listOf(
        TestQuizData.country("FRA", "France", "Paris"),
        TestQuizData.country("AUT", "Austria", "Vienna"),
        TestQuizData.country("PER", "Peru", "Lima", region = "Americas"),
        TestQuizData.country("SLB", "Solomon Islands", "Honiara", region = "Oceania"),
        TestQuizData.country("ATA", "Nowhere", "", region = "Antarctic")
    )

    private fun ModeContent.count(id: String): Int? = groups.find { it.id == id }?.quizCount

    @Test
    fun `countries groups count the country names and keep letter and word patterns`() {
        val content = PlayCategoryGroups.countriesContent(countries)

        assertEquals("countries", content.modeId)
        assertEquals(AllItemsTile(R.string.play_all_countries, R.plurals.play_all_countries_count, 5), content.allItems)
        assertEquals(
            listOf(
                "regions", "subregions", "starting_letter", "ending_letter", "containing_letter",
                "name_length", "letter_patterns", "word_patterns", "island_countries"
            ),
            content.groups.map { it.id }
        )
        assertEquals(4, content.count("regions"))
        // F, A, P, S, N
        assertEquals(5, content.count("starting_letter"))
        assertEquals(9, content.count("letter_patterns"))
        assertEquals(8, content.count("word_patterns"))
        assertEquals(1, content.count("island_countries"))
        assertEquals("Letter Patterns",content.groups.first { it.id == "letter_patterns" }.title)
    }

    @Test
    fun `islands are hidden when no country name has an island`() {
        val content = PlayCategoryGroups.countriesContent(countries.filter { it.code != "SLB" })
        assertFalse(content.groups.any { it.id == "island_countries" })
    }

    @Test
    fun `capitals groups use capital names, skip countries without a capital and have no islands`() {
        val content = PlayCategoryGroups.capitalsContent(countries)

        assertEquals("capitals", content.modeId)
        assertEquals(4, content.allItems?.count)
        assertEquals(R.plurals.play_all_capitals_count, content.allItems?.countLabel)
        // P, V, L, H (capital initials), not the country initials
        assertEquals(4, content.count("starting_letter"))
        // Paris 5, Vienna 6, Lima 4, Honiara 7
        assertEquals(4, content.count("name_length"))
        assertFalse(content.groups.any { it.id == "island_countries" })
        assertTrue(content.groups.any { it.id == "word_patterns" })
    }

    @Test
    fun `flag groups count colours, shared combos, colour counts and elements`() {
        val content = PlayCategoryGroups.flagGroups(ScreenTestFixtures.FLAG_COLOURS, ScreenTestFixtures.FLAG_ELEMENTS)

        assertEquals("flags", content.modeId)
        assertEquals(null, content.allItems)
        // blue, red, white
        assertEquals(3, content.count("flag_single_color"))
        // red + white is shared by three flags; blue pairs only by France
        assertEquals(1, content.count("flag_two_color_combo"))
        // no trio shared by two flags, so the group is hidden
        assertEquals(null, content.count("flag_three_color_combo"))
        // France 3 colours, Austria and Peru 2
        assertEquals(2, content.count("flag_color_count"))
        assertEquals(1, content.count("flag_elements"))
    }

    @Test
    fun `a trio shared by two flags counts as a three colour combo`() {
        val colours = listOf("FRA", "NLD").flatMap { code ->
            listOf("red", "white", "blue").map { FlagColorEntity(code, it) }
        }
        val content = PlayCategoryGroups.flagGroups(colours, listOf(FlagElementEntity("NLD", "stripe")))
        assertEquals(1, content.count("flag_three_color_combo"))
        assertEquals(3, content.count("flag_two_color_combo"))
    }

    @Test
    fun `no flag data means no flag groups`() {
        assertTrue(PlayCategoryGroups.flagGroups(emptyList(), emptyList()).groups.isEmpty())
    }
}
