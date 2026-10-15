package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import kotlinx.coroutines.flow.first

/**
 * Judges a typed Currencies answer (task 7.1). Only reached at Normal/Hard for a non-shared
 * currency: Easy, and every shared currency at any tier, never show a text field
 * (`ui/currencies/CurrenciesGameViewModel.kt` always builds a
 * [com.geoquiz.app.domain.mode.ChoiceQuestion] for those instead, D50) - this validator still
 * enforces the rule itself as defence in depth, rather than relying only on the screen never
 * calling it.
 *
 * Field-repurposing convention, like
 * [com.geoquiz.app.domain.mode.borderhop.BorderHopScoring]: [QuizState.quiz]'s country list is
 * never the whole round here, only the single representative
 * [com.geoquiz.app.domain.model.Country] being asked about right now (set by the caller before
 * calling [validate]). This turns [validateAnswer]'s usual "is this one of the quiz's countries"
 * check into "is this the current target" - the same trick
 * `SilhouettesGameViewModel.onSubmit`'s ephemeral [QuizState] uses, but scoped to one country
 * instead of every country in the game, which is what lets this validator look the target's
 * currency back up and reject a shared one outright.
 */
class CurrencyAnswerValidator(
    private val validateAnswer: ValidateAnswerUseCase,
    private val countryRepository: CountryRepository,
    private val currencyRepository: CurrencyRepository
) : AnswerValidator {

    override suspend fun validate(input: String, state: QuizState, allowFuzzy: Boolean): AnswerResult {
        val target = state.quiz.countries.singleOrNull() ?: return AnswerResult.Incorrect
        val allCountriesByCode = countryRepository.getAllCountries().first().associateBy { it.code }
        val data = currencyRepository.currencies()
        val entry = CurrencyItems.buildAll(data, allCountriesByCode).find { it.country.code == target.code }?.entry
        if (entry != null && entry.shared) return AnswerResult.Incorrect
        return validateAnswer(input, state, allowFuzzy)
    }
}
