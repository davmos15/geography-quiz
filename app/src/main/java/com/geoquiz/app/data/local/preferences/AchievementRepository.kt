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
        timeElapsedSeconds: Int
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
                is QuizCategory.OneWord, is QuizCategory.MultiWord -> "wordcount"
                is QuizCategory.DoubleLetter -> "patterns"
                is QuizCategory.IslandCountries -> "island"
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

            val percentage = if (totalCountries > 0) correctAnswers.toDouble() / totalCountries else 0.0

            // Check achievements
            fun unlock(a: Achievement) {
                if (a.id !in currentUnlocked) {
                    currentUnlocked.add(a.id)
                    newlyUnlocked.add(a)
                }
            }

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
    }
}
