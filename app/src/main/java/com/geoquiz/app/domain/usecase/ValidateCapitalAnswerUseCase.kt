package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import javax.inject.Inject

/** Checks a capital-city answer. See [resolveAnswer] for the rules. */
class ValidateCapitalAnswerUseCase @Inject constructor(
    private val repository: CountryRepository,
    private val normalizeInput: NormalizeInputUseCase
) {

    /** @param allowFuzzy accept answers one typo away (off in hard mode). */
    suspend operator fun invoke(input: String, state: QuizState, allowFuzzy: Boolean): AnswerResult =
        resolveAnswer(
            normalizedInput = normalizeInput(input),
            state = state,
            allowFuzzy = allowFuzzy,
            exactMatch = { repository.findCountryByCapitalAnswer(input) },
            aliases = { repository.getCapitalAnswerAliases() }
        )
}
