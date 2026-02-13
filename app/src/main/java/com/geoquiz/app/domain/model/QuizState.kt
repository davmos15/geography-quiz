package com.geoquiz.app.domain.model

data class QuizState(
    val quiz: Quiz,
    val answeredCountries: Set<String> = emptySet(),
    val currentInput: String = "",
    val timeElapsedSeconds: Int = 0,
    val isComplete: Boolean = false,
    val isPaused: Boolean = false,
    val lastAnswerResult: AnswerResult = AnswerResult.None,
    val incorrectGuesses: Int = 0
) {
    val progress: Float
        get() = if (quiz.countries.isEmpty()) 0f
        else answeredCountries.size.toFloat() / quiz.countries.size

    val remainingCount: Int
        get() = quiz.countries.size - answeredCountries.size

    val timerRemaining: Int?
        get() = quiz.timerSeconds?.let { (it - timeElapsedSeconds).coerceAtLeast(0) }
}

sealed class AnswerResult {
    data object None : AnswerResult()
    data class Correct(val countryName: String) : AnswerResult()
    data object AlreadyAnswered : AnswerResult()
    data object Incorrect : AnswerResult()
}
