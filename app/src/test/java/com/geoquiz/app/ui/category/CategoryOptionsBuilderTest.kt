package com.geoquiz.app.ui.category

import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.FlagCategoryGroup
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.category.CategoryOptionsFixture.COUNTRIES
import com.geoquiz.app.ui.category.CategoryOptionsFixture.FLAG_COLOURS
import com.geoquiz.app.ui.category.CategoryOptionsFixture.FLAG_ELEMENTS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The options extracted into [CategoryOptionsBuilder] (3.4b) are exactly those the category list
 * built before, compared with the verbatim pre-3.4b copy in [LegacyCategoryOptions].
 */
class CategoryOptionsBuilderTest {

    private val builder = CategoryOptionsFixture.builder()

    private fun legacy(mode: QuizMode) = LegacyCategoryOptions(mode, FLAG_COLOURS, FLAG_ELEMENTS)

    private suspend fun built(mode: QuizMode, groupId: String) =
        builder.build(mode, groupId, builder.countriesFor(mode, COUNTRIES))

    private val allGroupIds = CategoryGroup.entries.map { it.id } + FlagCategoryGroup.entries.map { it.id }

    @Test
    fun `countries options match the old category list for every group`() = runTest {
        for (groupId in allGroupIds) {
            assertEquals(groupId, legacy(QuizMode.COUNTRIES).options(groupId, COUNTRIES), built(QuizMode.COUNTRIES, groupId))
        }
    }

    @Test
    fun `capitals options match the old category list for every group`() = runTest {
        for (groupId in allGroupIds) {
            assertEquals(groupId, legacy(QuizMode.CAPITALS).options(groupId, COUNTRIES), built(QuizMode.CAPITALS, groupId))
        }
    }

    @Test
    fun `flags options match the old category list for every group`() = runTest {
        for (groupId in allGroupIds) {
            assertEquals(groupId, legacy(QuizMode.FLAGS).options(groupId, COUNTRIES), built(QuizMode.FLAGS, groupId))
        }
    }

    @Test
    fun `the fixture exercises every group`() = runTest {
        for (group in CategoryGroup.entries) {
            assertTrue(group.id, built(QuizMode.COUNTRIES, group.id).isNotEmpty())
        }
        for (group in FlagCategoryGroup.entries) {
            assertTrue(group.id, built(QuizMode.FLAGS, group.id).isNotEmpty())
        }
    }

    @Test
    fun `spot checks of the built options`() = runTest {
        // Capitals leave out countries without a capital and use the capital's letters.
        assertEquals(COUNTRIES.size - 1, builder.countriesFor(QuizMode.CAPITALS, COUNTRIES).size)
        assertEquals(COUNTRIES.size, builder.countriesFor(QuizMode.FLAGS, COUNTRIES).size)
        val capitalStart = built(QuizMode.CAPITALS, CategoryGroup.STARTING_LETTER.id).map { it.categoryValue }
        assertTrue("H" in capitalStart) // Honiara
        assertFalse("K" in capitalStart) // Kazakhstan's capital is Astana
        val all = built(QuizMode.CAPITALS, CategoryGroup.ALL_COUNTRIES.id)
        assertEquals(listOf("all", "capitalmatches"), all.map { it.categoryType })

        val subregions = built(QuizMode.COUNTRIES, CategoryGroup.SUBREGIONS.id)
        assertTrue(subregions.any { it.name == "Australasia" && it.categoryValue == "Australia and New Zealand" })

        val trios = built(QuizMode.FLAGS, FlagCategoryGroup.FLAG_THREE_COLOR_COMBO.id)
        assertEquals(QuizOptionInfo("Only Blue & Red & White", 5, "flagcombo", "blue+red+white"), trios.first())
        val pairs = built(QuizMode.FLAGS, FlagCategoryGroup.FLAG_TWO_COLOR_COMBO.id)
        assertEquals(setOf("blue+yellow", "red+white"), pairs.map { it.categoryValue }.toSet())

        // Mappings for countries outside the list are ignored.
        assertFalse(built(QuizMode.FLAGS, FlagCategoryGroup.FLAG_SINGLE_COLOR.id).any { it.categoryValue == "purple" })
        assertFalse(built(QuizMode.FLAGS, FlagCategoryGroup.FLAG_ELEMENTS.id).any { it.categoryValue == "dragon" })
    }

    @Test
    fun `an unknown group has no options`() = runTest {
        assertEquals(emptyList<QuizOptionInfo>(), built(QuizMode.COUNTRIES, "nope"))
    }
}
