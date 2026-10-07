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
    val hardMode: Boolean,
    val challengeId: String?,
    val completedAtMillis: Long
) {
    val quizMode: QuizMode
        get() = QuizMode.fromId(quizModeId)

    val category: QuizCategory
        get() = QuizCategory.fromRoute(categoryType, categoryValue)
}
