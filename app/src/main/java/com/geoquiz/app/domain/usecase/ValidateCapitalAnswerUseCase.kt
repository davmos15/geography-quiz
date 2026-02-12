package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import javax.inject.Inject

class ValidateCapitalAnswerUseCase @Inject constructor(
    private val repository: CountryRepository,
    private val normalizeInput: NormalizeInputUseCase
) {

    suspend operator fun invoke(input: String, state: QuizState): AnswerResult {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return AnswerResult.Incorrect

        val country = repository.findCountryByCapitalAnswer(input) ?: return AnswerResult.Incorrect

        val quizCountryCodes = state.quiz.countries.map { it.code }.toSet()
        if (country.code !in quizCountryCodes) return AnswerResult.Incorrect

        if (country.code in state.answeredCountries) return AnswerResult.AlreadyAnswered

        return AnswerResult.Correct(country.name)
    }
}
