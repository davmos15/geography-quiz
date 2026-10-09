package com.geoquiz.app.ui.play

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.geoquiz.app.R
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagColorEntity
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.data.local.db.FlagElementEntity
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.FlagCategoryGroup
import com.geoquiz.app.domain.model.QuizMode
import javax.inject.Inject

/** A category group tile on Play; opens `category/{mode}/{id}`. */
data class GroupTile(
    val id: String,
    val title: String,
    val quizCount: Int
)

/** The featured "All countries" / "All capitals" tile, which starts a quiz in one tap. */
data class AllItemsTile(
    @StringRes val title: Int,
    @PluralsRes val countLabel: Int,
    val count: Int
)

/** What Play shows below the mode switch for one classic mode. */
data class ModeContent(
    val modeId: String,
    val allItems: AllItemsTile?,
    val groups: List<GroupTile>
)

/**
 * Builds the category group tiles of the three classic modes (formerly in the Countries, Capitals
 * and Flags home ViewModels). Groups with no quizzes are left out.
 */
class PlayCategoryGroups @Inject constructor(
    private val flagColorDao: FlagColorDao,
    private val flagElementDao: FlagElementDao
) {

    /** Content for every classic mode, keyed by mode id. */
    suspend fun build(countries: List<Country>): Map<String, ModeContent> {
        val flags = flagGroups(flagColorDao.getAllMappings(), flagElementDao.getAllMappings())
        return listOf(
            countriesContent(countries),
            capitalsContent(countries),
            flags
        ).associateBy { it.modeId }
    }

    companion object {
        /** Letter patterns: double letter, consonant cluster, repeated 3, repeated 4+, etc. */
        private const val LETTER_PATTERN_QUIZZES = 9

        /** Word patterns: word counts, suffixes and keywords. */
        private const val WORD_PATTERN_QUIZZES = 8

        fun countriesContent(countries: List<Country>): ModeContent {
            val names = countries.map { it.name }
            val islandCount = countries.count { it.name.contains("island", ignoreCase = true) }
            val groups = nameGroups(countries, names) +
                tile(CategoryGroup.ISLAND_COUNTRIES, if (islandCount > 0) 1 else 0)
            return ModeContent(
                modeId = QuizMode.COUNTRIES.id,
                allItems = AllItemsTile(R.string.play_all_countries, R.plurals.play_all_countries_count, countries.size),
                groups = groups.filter { it.quizCount > 0 }
            )
        }

        /** Letter-based groups use the capital's name, not the country's. */
        fun capitalsContent(countries: List<Country>): ModeContent {
            val withCapital = countries.filter { it.capital.isNotBlank() }
            return ModeContent(
                modeId = QuizMode.CAPITALS.id,
                allItems = AllItemsTile(R.string.play_all_capitals, R.plurals.play_all_capitals_count, withCapital.size),
                groups = nameGroups(withCapital, withCapital.map { it.capital }).filter { it.quizCount > 0 }
            )
        }

        fun flagGroups(colours: List<FlagColorEntity>, elements: List<FlagElementEntity>): ModeContent {
            val colourToCountries = mutableMapOf<String, MutableSet<String>>()
            val countryToColours = mutableMapOf<String, MutableSet<String>>()
            for (mapping in colours) {
                colourToCountries.getOrPut(mapping.color) { mutableSetOf() }.add(mapping.countryCca3)
                countryToColours.getOrPut(mapping.countryCca3) { mutableSetOf() }.add(mapping.color)
            }
            val sets = colourToCountries.keys.sorted().map { colourToCountries.getValue(it) }

            // Combos shared by at least two countries
            var twoColourCount = 0
            var threeColourCount = 0
            for (i in sets.indices) {
                for (j in i + 1 until sets.size) {
                    val inter12 = sets[i].intersect(sets[j])
                    if (inter12.size < 2) continue
                    twoColourCount++
                    for (k in j + 1 until sets.size) {
                        if (inter12.intersect(sets[k]).size >= 2) threeColourCount++
                    }
                }
            }

            val groups = listOf(
                tile(FlagCategoryGroup.FLAG_SINGLE_COLOR, sets.size),
                tile(FlagCategoryGroup.FLAG_TWO_COLOR_COMBO, twoColourCount),
                tile(FlagCategoryGroup.FLAG_THREE_COLOR_COMBO, threeColourCount),
                tile(FlagCategoryGroup.FLAG_COLOR_COUNT, countryToColours.values.map { it.size }.toSet().size),
                tile(FlagCategoryGroup.FLAG_ELEMENTS, elements.map { it.element }.distinct().size)
            ).filter { it.quizCount > 0 }
            return ModeContent(modeId = QuizMode.FLAGS.id, allItems = null, groups = groups)
        }

        /** Region and name-based groups shared by Countries and Capitals ([names] = the names asked). */
        private fun nameGroups(countries: List<Country>, names: List<String>): List<GroupTile> {
            val regions = countries.map { it.region }.filter { it.isNotBlank() }.distinct().size
            val subregions = countries.map { it.subregion }.filter { it.isNotBlank() }.distinct().size
            val nonBlank = names.filter { it.isNotEmpty() }
            val startLetters = nonBlank.map { it.first().uppercaseChar() }.filter { it in 'A'..'Z' }.distinct().size
            val endLetters = nonBlank.map { it.last().uppercaseChar() }.filter { it in 'A'..'Z' }.distinct().size
            val containLetters = ('A'..'Z').count { letter -> nonBlank.any { it.contains(letter, ignoreCase = true) } }
            val lengths = nonBlank.map { it.length }.distinct().size
            return listOf(
                tile(CategoryGroup.REGIONS, regions),
                tile(CategoryGroup.SUBREGIONS, subregions),
                tile(CategoryGroup.STARTING_LETTER, startLetters),
                tile(CategoryGroup.ENDING_LETTER, endLetters),
                tile(CategoryGroup.CONTAINING_LETTER, containLetters),
                tile(CategoryGroup.NAME_LENGTH, lengths),
                tile(CategoryGroup.LETTER_PATTERNS, LETTER_PATTERN_QUIZZES),
                tile(CategoryGroup.WORD_PATTERNS, WORD_PATTERN_QUIZZES)
            )
        }

        private fun tile(group: CategoryGroup, count: Int) = GroupTile(group.id, group.displayName, count)

        private fun tile(group: FlagCategoryGroup, count: Int) = GroupTile(group.id, group.displayName, count)
    }
}
