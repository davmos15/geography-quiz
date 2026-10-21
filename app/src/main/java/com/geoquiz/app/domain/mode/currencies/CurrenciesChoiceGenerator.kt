package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.buildQuestion
import com.geoquiz.app.domain.mode.pickSameRegionFirst
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import kotlin.random.Random

/**
 * Currencies' multiple-choice question (task 7.1, D50; symbol-only clue task P10-B): always used
 * at Easy, and for any [CurrencyEntry.isChoiceOnly] currency at every tier - [CurrencyAnswerValidator]
 * rejects a typed answer for one, so `ui/currencies/CurrenciesGameViewModel.kt` must never show a
 * text field for it either way.
 *
 * [target] is this item's representative country (see [CurrencyItems]); [currencyRepository]'s
 * already-warm cache (see [com.geoquiz.app.data.currencies.CurrencyRepository.cachedOrNull]'s
 * KDoc for why this is safe here) gives back the real [CurrencyEntry] to build the prompt (just
 * [CurrencyEntry.symbol] - task P10-B, Dav 2026-10-20: never the name or code before the answer is
 * resolved) and, for a shared or ambiguous-symbol currency, the full membership list the one
 * correct option must come from.
 *
 * The entry lookup is scoped to [quizSet] - the round's own items, i.e. exactly one
 * representative per currency already (task 7.1 review: looking the target up in a freshly
 * *global* [CurrencyItems.buildAll] call could pick a different representative than the one this
 * round actually has, since representative assignment is scoped to a candidate set - see
 * [CurrencyItems]'s KDoc). [quizSet] is always a safe, unambiguous universe for this: it already
 * contains exactly one true member per shared item in the round, so re-deriving against it just
 * confirms that same pick.
 *
 * The correct option is always [target] itself (a true member, by construction). Distractors
 * exclude any country that [CurrencySymbolRules.countryUsesSymbol] for this entry's symbol (task
 * P10-B) - official member or basket listing (D48), of *this* entry or any other currency in the
 * data sharing the same symbol (e.g. a "$" question's distractors exclude every $-symbol
 * currency's countries, not just this one's) - since any of them could, just as validly, be "the"
 * answer for the same symbol, which is now the only clue shown. The target's region is preferred
 * where that still leaves three distractors.
 *
 * Falls back to a "?" label if the cache is somehow cold or the item cannot be found (should
 * never happen once a round has loaded), rather than crashing mid-quiz or showing the target's
 * name.
 */
class CurrenciesChoiceGenerator(
    private val currencyRepository: CurrencyRepository
) : ChoiceQuestionGenerator {

    override fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion {
        val data = currencyRepository.cachedOrNull()
        val entry = data?.let { d ->
            val quizCodes = quizSet.mapTo(mutableSetOf()) { it.code }
            val quizCountriesByCode = quizSet.associateBy { it.code }
            CurrencyItems.build(d, quizCountriesByCode, quizCodes).find { it.country.code == target.code }?.entry
        }
        val prompt = ChoicePrompt.CurrencyOf(entry?.symbol ?: UNKNOWN_SYMBOL)
        val allEntries = data?.entries ?: emptyList()
        val excluded = entry?.let { e ->
            allCountries.filter { CurrencySymbolRules.countryUsesSymbol(it.code, e.symbol, allEntries) }
                .mapTo(mutableSetOf()) { it.code }
        } ?: emptySet()
        val distractorPool = if (excluded.isEmpty()) allCountries else allCountries.filter { it.code !in excluded }
        val distractors = pickSameRegionFirst(target, distractorPool, CHOICE_OPTION_COUNT - 1, random)
        return buildQuestion(target, distractors, prompt, random) { it.name }
    }

    private companion object {
        /** Shown only if the cache is cold; never the target's name, which would give it away. */
        const val UNKNOWN_SYMBOL = "?"
    }
}
