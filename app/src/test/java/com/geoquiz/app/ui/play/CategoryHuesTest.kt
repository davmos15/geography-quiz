package com.geoquiz.app.ui.play

import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.FlagCategoryGroup
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.theme.CategoryHue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * [categoryHue] (task P10-A.4; licence audit round, task P10-R1): every real group id Play can
 * ever show for a classic mode maps to a hue, no hue repeats within one mode's page (so no two
 * tiles on the same page are the same colour), no page uses [CategoryHue.AMBER] at all (L9: amber
 * sitting next to green or brown tiles read too close to other trade dress), and
 * [CategoryHue.GREEN]/[CategoryHue.BROWN] - the two hues closest to each other in hue angle -
 * never sit next to each other in a 2- or 3-column grid.
 */
class CategoryHuesTest {

    /** Real [CategoryGroup] ids Countries' page can show, in [com.geoquiz.app.ui.play.PlayCategoryGroups]'s tile order. */
    private val countriesGroupIds = listOf(
        CategoryGroup.REGIONS, CategoryGroup.SUBREGIONS, CategoryGroup.STARTING_LETTER,
        CategoryGroup.ENDING_LETTER, CategoryGroup.CONTAINING_LETTER, CategoryGroup.NAME_LENGTH,
        CategoryGroup.LETTER_PATTERNS, CategoryGroup.WORD_PATTERNS, CategoryGroup.ISLAND_COUNTRIES
    ).map { it.id }

    /** Capitals never gets the island group (task 7.1's `capitalsContent` has no island tile). */
    private val capitalsGroupIds = listOf(
        CategoryGroup.REGIONS, CategoryGroup.SUBREGIONS, CategoryGroup.STARTING_LETTER,
        CategoryGroup.ENDING_LETTER, CategoryGroup.CONTAINING_LETTER, CategoryGroup.NAME_LENGTH,
        CategoryGroup.LETTER_PATTERNS, CategoryGroup.WORD_PATTERNS
    ).map { it.id }

    private val flagsGroupIds = FlagCategoryGroup.entries.map { it.id }

    private val pages: Map<String, List<String>> = mapOf(
        QuizMode.COUNTRIES.id to countriesGroupIds,
        QuizMode.CAPITALS.id to capitalsGroupIds,
        QuizMode.FLAGS.id to flagsGroupIds
    )

    @Test
    fun `every real group id of every classic mode maps to a non-null hue`() {
        pages.forEach { (modeId, groupIds) ->
            groupIds.forEach { groupId ->
                assertNotNull("$modeId/$groupId must have a hue", categoryHue(modeId, groupId))
            }
        }
    }

    @Test
    fun `no hue repeats within one mode's page`() {
        pages.forEach { (modeId, groupIds) ->
            val hues = groupIds.map { categoryHue(modeId, it) }
            assertEquals(
                "$modeId's hues must all be distinct: $hues",
                hues.size,
                hues.toSet().size
            )
        }
    }

    @Test
    fun `no page ever uses AMBER (L9 - task P10-R1)`() {
        pages.forEach { (modeId, groupIds) ->
            val hues = groupIds.map { categoryHue(modeId, it) }
            assertFalse("$modeId must never use AMBER", CategoryHue.AMBER in hues)
        }
    }

    @Test
    fun `an unknown mode or group id has no hue (falls back to the default tile colours)`() {
        assertEquals(null, categoryHue("not_a_mode", "regions"))
        assertEquals(null, categoryHue(QuizMode.COUNTRIES.id, "not_a_group"))
    }

    // --- GREEN and BROWN are never adjacent in the Play grid (licence audit, task P10-R1) ------

    /** Every (row, col) pair adjacent to [index] in a grid of [columns] columns. */
    private fun adjacentIndices(index: Int, columns: Int): List<Int> {
        val row = index / columns
        val col = index % columns
        return listOfNotNull(
            if (col > 0) index - 1 else null,
            if (col < columns - 1) index + 1 else null,
            index - columns,
            index + columns
        )
    }

    private fun assertGreenAndBrownNeverAdjacent(groupIds: List<String>, modeId: String, columns: Int) {
        val hues = groupIds.map { categoryHue(modeId, it) }
        hues.indices.forEach { i ->
            if (hues[i] != CategoryHue.GREEN) return@forEach
            adjacentIndices(i, columns).forEach { j ->
                if (j in hues.indices) {
                    assertFalse(
                        "$modeId ($columns columns): GREEN at $i and BROWN at $j must not be adjacent",
                        hues[j] == CategoryHue.BROWN
                    )
                }
            }
        }
    }

    @Test
    fun `GREEN and BROWN tiles are never adjacent in a 2-column grid`() {
        pages.forEach { (modeId, groupIds) -> assertGreenAndBrownNeverAdjacent(groupIds, modeId, columns = 2) }
    }

    @Test
    fun `GREEN and BROWN tiles are never adjacent in a 3-column grid`() {
        pages.forEach { (modeId, groupIds) -> assertGreenAndBrownNeverAdjacent(groupIds, modeId, columns = 3) }
    }
}
