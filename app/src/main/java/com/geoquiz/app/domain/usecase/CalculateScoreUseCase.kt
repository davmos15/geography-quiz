package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.QuizResult
import com.geoquiz.app.domain.model.QuizState
import javax.inject.Inject

class CalculateScoreUseCase @Inject constructor() {

    operator fun invoke(state: QuizState): QuizResult {
        val total = state.quiz.countries.size
        val correct = state.answeredCountries.size
        val percentage = if (total == 0) 0.0 else correct.toDouble() / total
        val baseScore = percentage * correct
        val isPerfect = correct == total && total > 0
        val perfectBonusMultiplier = if (isPerfect) 1.2 else 1.0
        val finalScore = baseScore * perfectBonusMultiplier

        return QuizResult(
            category = state.quiz.category,
            totalCountries = total,
            correctAnswers = correct,
            timeElapsedSeconds = state.timeElapsedSeconds,
            score = finalScore,
            perfectBonus = isPerfect,
            incorrectGuesses = state.incorrectGuesses
        )
    }
}
