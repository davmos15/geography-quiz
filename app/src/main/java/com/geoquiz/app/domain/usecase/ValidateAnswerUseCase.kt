package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import javax.inject.Inject

/** Checks a country-name answer. See [resolveAnswer] for the rules. */
class ValidateAnswerUseCase @Inject constructor(
    private val repository: CountryRepository,
    private val normalizeInput: NormalizeInputUseCase
) {

    /** @param allowFuzzy accept answers one typo away (off in hard mode). */
    suspend operator fun invoke(input: String, state: QuizState, allowFuzzy: Boolean): AnswerResult =
        resolveAnswer(
            normalizedInput = normalizeInput(input),
            state = state,
            allowFuzzy = allowFuzzy,
            exactMatch = { repository.findCountryByAnswer(input) },
            aliases = { repository.getCountryAnswerAliases() }
        )
}
