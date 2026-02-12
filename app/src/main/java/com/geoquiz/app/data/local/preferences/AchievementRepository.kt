package com.geoquiz.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.achievementDataStore: DataStore<Preferences> by preferencesDataStore(name = "achievements")

@Singleton
class AchievementRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.achievementDataStore

    val unlockedAchievements: Flow<Set<String>> = dataStore.data.map { prefs ->
        val raw = prefs[UNLOCKED_KEY] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    val quizzesCompleted: Flow<Int> = dataStore.data.map { prefs ->
        prefs[QUIZZES_COMPLETED_KEY] ?: 0
    }

    private val completedRegions: Flow<Set<String>> = dataStore.data.map { prefs ->
        val raw = prefs[COMPLETED_REGIONS_KEY] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    private val completedStartLetters: Flow<Set<String>> = dataStore.data.map { prefs ->
        val raw = prefs[COMPLETED_START_LETTERS_KEY] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    private val categoryGroupsPlayed: Flow<Set<String>> = dataStore.data.map { prefs ->
        val raw = prefs[CATEGORY_GROUPS_KEY] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    suspend fun onQuizCompleted(
        category: QuizCategory,
        correctAnswers: Int,
        totalCountries: Int,
        timeElapsedSeconds: Int,
        quizMode: QuizMode = QuizMode.COUNTRIES
    ): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()
        val currentUnlocked = unlockedAchievements.first().toMutableSet()

        dataStore.edit { prefs ->
            // Increment quiz count
            val count = (prefs[QUIZZES_COMPLETED_KEY] ?: 0) + 1
            prefs[QUIZZES_COMPLETED_KEY] = count

            // Track category group
            val groupKey = when (category) {
                is QuizCategory.AllCountries -> "all"
                is QuizCategory.ByRegion -> "region"
                is QuizCategory.BySubregion -> "subregion"
                is QuizCategory.StartingWithLetter -> "startletter"
                is QuizCategory.EndingWithLetter -> "endletter"
                is QuizCategory.ContainingLetter -> "containletter"
                is QuizCategory.ByNameLengthRange -> "length"
                is QuizCategory.DoubleLetter,
                is QuizCategory.ConsonantCluster,
                is QuizCategory.RepeatedLetter3,
                is QuizCategory.RepeatedLetter4,
                is QuizCategory.StartsEndsSame,
                is QuizCategory.AllVowelsPresent -> "patterns"
                is QuizCategory.ByWordCount,
                is QuizCategory.EndingWithSuffix,
                is QuizCategory.ContainingWord -> "wordpatterns"
                is QuizCategory.IslandCountries -> "island"
                is QuizCategory.FlagSingleColor -> "flagcolor"
                is QuizCategory.FlagColorCombo -> "flagcombo"
                is QuizCategory.FlagColorCount -> "flagcount"
            }
            val groups = (prefs[CATEGORY_GROUPS_KEY] ?: "").let {
                if (it.isBlank()) mutableSetOf() else it.split(",").toMutableSet()
            }
            groups.add(groupKey)
            prefs[CATEGORY_GROUPS_KEY] = groups.joinToString(",")

            // Track completed regions
            if (category is QuizCategory.ByRegion) {
                val regions = (prefs[COMPLETED_REGIONS_KEY] ?: "").let {
                    if (it.isBlank()) mutableSetOf() else it.split(",").toMutableSet()
                }
                regions.add(category.region)
                prefs[COMPLETED_REGIONS_KEY] = regions.joinToString(",")
            }

            // Track completed starting letters
            if (category is QuizCategory.StartingWithLetter) {
                val letters = (prefs[COMPLETED_START_LETTERS_KEY] ?: "").let {
                    if (it.isBlank()) mutableSetOf() else it.split(",").toMutableSet()
                }
                letters.add(category.letter.toString())
                prefs[COMPLETED_START_LETTERS_KEY] = letters.joinToString(",")
            }

            // Track completed name length quizzes
            if (category is QuizCategory.ByNameLengthRange) {
                val lengths = (prefs[COMPLETED_LENGTH_QUIZZES_KEY] ?: "").let {
                    if (it.isBlank()) mutableSetOf() else it.split(",").toMutableSet()
                }
                lengths.add("${category.min}-${category.max}")
                prefs[COMPLETED_LENGTH_QUIZZES_KEY] = lengths.joinToString(",")
            }

            // Track completed pattern quizzes
            val patternTypeKey = when (category) {
                is QuizCategory.DoubleLetter -> "doubleletter"
                is QuizCategory.ConsonantCluster -> "consonantcluster"
                is QuizCategory.RepeatedLetter3 -> "repeatedletter3"
                is QuizCategory.RepeatedLetter4 -> "repeatedletter4"
                is QuizCategory.StartsEndsSame -> "startsendssame"
                is QuizCategory.AllVowelsPresent -> "allvowels"
                else -> null
            }
            if (patternTypeKey != null) {
                val patterns = (prefs[COMPLETED_PATTERN_QUIZZES_KEY] ?: "").let {
                    if (it.isBlank()) mutableSetOf() else it.split(",").toMutableSet()
                }
                patterns.add(patternTypeKey)
                prefs[COMPLETED_PATTERN_QUIZZES_KEY] = patterns.joinToString(",")
            }

            // Track capital quizzes
            if (quizMode == QuizMode.CAPITALS) {
                val capitalCount = (prefs[CAPITAL_QUIZZES_COMPLETED_KEY] ?: 0) + 1
                prefs[CAPITAL_QUIZZES_COMPLETED_KEY] = capitalCount
            }

            // Track flag quizzes
            if (quizMode == QuizMode.FLAGS) {
                val flagCount = (prefs[FLAG_QUIZZES_COMPLETED_KEY] ?: 0) + 1
                prefs[FLAG_QUIZZES_COMPLETED_KEY] = flagCount

                // Track completed flag colors
                val flagColorKey = when (category) {
                    is QuizCategory.FlagSingleColor -> category.color
                    else -> null
                }
                if (flagColorKey != null) {
                    val flagColors = (prefs[COMPLETED_FLAG_COLORS_KEY] ?: "").let {
                        if (it.isBlank()) mutableSetOf() else it.split(",").toMutableSet()
                    }
                    flagColors.add(flagColorKey)
                    prefs[COMPLETED_FLAG_COLORS_KEY] = flagColors.joinToString(",")
                }
            }

            // Track completed subregions
            if (category is QuizCategory.BySubregion) {
                val subregions = (prefs[COMPLETED_SUBREGIONS_KEY] ?: "").let {
                    if (it.isBlank()) mutableSetOf() else it.split(",").toMutableSet()
                }
                subregions.add(category.subregion)
                prefs[COMPLETED_SUBREGIONS_KEY] = subregions.joinToString(",")
            }

            // Track region scores (best percentage per region)
            val percentage = if (totalCountries > 0) correctAnswers.toDouble() / totalCountries else 0.0

            if (category is QuizCategory.ByRegion) {
                val regionScores = (prefs[REGION_SCORES_KEY] ?: "").let {
                    if (it.isBlank()) mutableMapOf()
                    else it.split(";").associate { entry ->
                        val parts = entry.split(":")
                        parts[0] to parts.getOrElse(1) { "0.0" }.toDouble()
                    }.toMutableMap()
                }
                val current = regionScores[category.region] ?: 0.0
                if (percentage > current) {
                    regionScores[category.region] = percentage
                }
                prefs[REGION_SCORES_KEY] = regionScores.entries.joinToString(";") { "${it.key}:${it.value}" }
            }

            // Check achievements
            fun unlock(a: Achievement) {
                if (a.id !in currentUnlocked) {
                    currentUnlocked.add(a.id)
                    newlyUnlocked.add(a)
                }
            }

            // --- Original achievements ---
            unlock(Achievement.FIRST_STEPS)

            if (category is QuizCategory.AllCountries) {
                unlock(Achievement.WORLD_TRAVELER)
                if (percentage >= 0.5) unlock(Achievement.HALF_WAY_THERE)
            }

            if (percentage >= 1.0 && totalCountries > 0) unlock(Achievement.PERFECTIONIST)
            if (timeElapsedSeconds < 120 && totalCountries > 0) unlock(Achievement.SPEED_DEMON)
            if (correctAnswers >= 100) unlock(Achievement.CENTURY_CLUB)
            if (count >= 20) unlock(Achievement.GEOGRAPHY_BUFF)

            val regions = (prefs[COMPLETED_REGIONS_KEY] ?: "").let {
                if (it.isBlank()) emptySet() else it.split(",").toSet()
            }
            if (regions.size >= 5) unlock(Achievement.REGION_MASTER)

            val letters = (prefs[COMPLETED_START_LETTERS_KEY] ?: "").let {
                if (it.isBlank()) emptySet() else it.split(",").toSet()
            }
            if (letters.size >= 10) unlock(Achievement.ALPHABET_SOUP)

            if (groups.size >= 5) unlock(Achievement.EXPLORER)

            // --- New achievements ---

            // Quick Study: under 5 minutes
            if (timeElapsedSeconds < 300 && totalCountries > 0) unlock(Achievement.QUICK_STUDY)

            // Island Hopper: complete island quiz
            if (category is QuizCategory.IslandCountries) unlock(Achievement.ISLAND_HOPPER)

            // Pattern Finder: any pattern quiz
            if (patternTypeKey != null) unlock(Achievement.PATTERN_FINDER)

            // World Scholar: 75%+ in All Countries
            if (category is QuizCategory.AllCountries && percentage >= 0.75) unlock(Achievement.WORLD_SCHOLAR)

            // Length Master: 5 different name length quizzes
            val lengthQuizzes = (prefs[COMPLETED_LENGTH_QUIZZES_KEY] ?: "").let {
                if (it.isBlank()) emptySet() else it.split(",").toSet()
            }
            if (lengthQuizzes.size >= 5) unlock(Achievement.LENGTH_MASTER)

            // Vowel Hunter: AllVowelsPresent quiz
            if (category is QuizCategory.AllVowelsPresent) unlock(Achievement.VOWEL_HUNTER)

            // Continental: all 5 regions with 80%+
            val regionScores = (prefs[REGION_SCORES_KEY] ?: "").let {
                if (it.isBlank()) emptyMap()
                else it.split(";").associate { entry ->
                    val parts = entry.split(":")
                    parts[0] to parts.getOrElse(1) { "0.0" }.toDouble()
                }
            }
            if (regionScores.count { it.value >= 0.8 } >= 5) unlock(Achievement.CONTINENTAL)

            // Letter Collector: 15 starting letters
            if (letters.size >= 15) unlock(Achievement.LETTER_COLLECTOR)

            // Ultimate Geographer: 100% All Countries
            if (category is QuizCategory.AllCountries && percentage >= 1.0) unlock(Achievement.ULTIMATE_GEOGRAPHER)

            // Speed Master: All Countries under 15 minutes
            if (category is QuizCategory.AllCountries && timeElapsedSeconds < 900 && totalCountries > 0) unlock(Achievement.SPEED_MASTER)

            // Pattern Master: all 6 pattern types completed
            val patternQuizzes = (prefs[COMPLETED_PATTERN_QUIZZES_KEY] ?: "").let {
                if (it.isBlank()) emptySet() else it.split(",").toSet()
            }
            if (patternQuizzes.size >= 6) unlock(Achievement.PATTERN_MASTER)

            // Subregion Explorer: 10 different subregions
            val subregions = (prefs[COMPLETED_SUBREGIONS_KEY] ?: "").let {
                if (it.isBlank()) emptySet() else it.split(",").toSet()
            }
            if (subregions.size >= 10) unlock(Achievement.SUBREGION_EXPLORER)

            // --- Capital achievements ---
            if (quizMode == QuizMode.CAPITALS) {
                unlock(Achievement.CAPITAL_BEGINNER)

                if (percentage >= 0.8) unlock(Achievement.CAPITAL_EXPERT)

                if (category is QuizCategory.AllCountries) {
                    unlock(Achievement.WORLD_CAPITALS)
                    if (percentage >= 1.0) unlock(Achievement.CAPITAL_MASTER)
                }

                if (timeElapsedSeconds < 120 && totalCountries > 0) unlock(Achievement.CAPITAL_SPEED_RUN)

                val capitalCount = prefs[CAPITAL_QUIZZES_COMPLETED_KEY] ?: 0
                if (capitalCount >= 10) unlock(Achievement.CAPITAL_SCHOLAR)
            }

            // --- Flag achievements ---
            if (quizMode == QuizMode.FLAGS) {
                unlock(Achievement.FLAG_SPOTTER)

                if (percentage >= 1.0 && totalCountries > 0) unlock(Achievement.FLAG_PERFECTIONIST)

                if (category is QuizCategory.AllCountries && percentage >= 0.8) {
                    unlock(Achievement.FLAG_MASTER)
                }

                val flagCount = prefs[FLAG_QUIZZES_COMPLETED_KEY] ?: 0
                if (flagCount >= 10) unlock(Achievement.VEXILLOLOGIST)

                val flagColors = (prefs[COMPLETED_FLAG_COLORS_KEY] ?: "").let {
                    if (it.isBlank()) emptySet() else it.split(",").toSet()
                }
                if (flagColors.size >= 5) unlock(Achievement.COLOR_EXPERT)
                if (flagColors.size >= 6) unlock(Achievement.RAINBOW)
            }

            prefs[UNLOCKED_KEY] = currentUnlocked.joinToString(",")
        }

        return newlyUnlocked
    }

    companion object {
        private val UNLOCKED_KEY = stringPreferencesKey("unlocked_achievements")
        private val QUIZZES_COMPLETED_KEY = intPreferencesKey("quizzes_completed")
        private val COMPLETED_REGIONS_KEY = stringPreferencesKey("completed_regions")
        private val COMPLETED_START_LETTERS_KEY = stringPreferencesKey("completed_start_letters")
        private val CATEGORY_GROUPS_KEY = stringPreferencesKey("category_groups_played")
        private val COMPLETED_LENGTH_QUIZZES_KEY = stringPreferencesKey("completed_length_quizzes")
        private val COMPLETED_PATTERN_QUIZZES_KEY = stringPreferencesKey("completed_pattern_quizzes")
        private val COMPLETED_SUBREGIONS_KEY = stringPreferencesKey("completed_subregions")
        private val REGION_SCORES_KEY = stringPreferencesKey("region_scores")
        private val CAPITAL_QUIZZES_COMPLETED_KEY = intPreferencesKey("capital_quizzes_completed")
        private val FLAG_QUIZZES_COMPLETED_KEY = intPreferencesKey("flag_quizzes_completed")
        private val COMPLETED_FLAG_COLORS_KEY = stringPreferencesKey("completed_flag_colors")
    }
}
