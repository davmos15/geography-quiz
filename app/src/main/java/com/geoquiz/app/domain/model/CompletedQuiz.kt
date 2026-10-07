package com.geoquiz.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A finished quiz, persisted so Results and Answer review survive process death.
 *
 * Countries are stored by code (cca3) and looked up again from the static database. Only the
 * latest result is kept (see [com.geoquiz.app.domain.repository.CompletedQuizRepository]).
 *
 * [categoryType] and [categoryValue] are the category's route keys ([QuizCategory.typeKey] and
 * [QuizCategory.valueKey]), used for "Play again", challenge links and the review hints.
 */
@Serializable
data class CompletedQuiz(
    val id: String,
    val quizModeId: String,
    val categoryType: String,
    val categoryValue: String,
    val categoryName: String,
    /** Every country in the quiz, in quiz order. */
    val countryCodes: List<String>,
    val answeredCodes: List<String>,
    val incorrectGuessStrings: List<String>,
    val correct: Int,
    val total: Int,
    val timeSeconds: Int,
    val score: Double,
    val perfectBonus: Boolean,
    val incorrectGuesses: Int,
    /** True for a Hard quiz. Kept so records written before 3.2 (which only had this) still read. */
    val hardMode: Boolean,
    val challengeId: String?,
    val completedAtMillis: Long,
    /**
     * [Difficulty.id] of the tier the quiz was played at. Records from before 3.2 have none and
     * read as Normal (or Hard, if [hardMode] is set). Use [difficulty] rather than this.
     */
    val difficultyId: String = Difficulty.DEFAULT.id,
    /**
     * [Achievement.id]s this quiz unlocked, so Results can show them (also after process death).
     * Records from before 3.5b have none. Use [newAchievements] rather than this.
     */
    val newAchievementIds: List<String> = emptyList()
) {
    /** The tier the quiz was played at. */
    val difficulty: Difficulty
        get() = if (hardMode) Difficulty.HARD else Difficulty.fromIdOrDefault(difficultyId)

    /** The achievements this quiz unlocked, in unlock order; ids no longer known are skipped. */
    val newAchievements: List<Achievement>
        get() = newAchievementIds.mapNotNull { id -> Achievement.entries.firstOrNull { it.id == id } }

    val quizMode: QuizMode
        get() = QuizMode.fromId(quizModeId)

    val category: QuizCategory
        get() = QuizCategory.fromRoute(categoryType, categoryValue)
}
