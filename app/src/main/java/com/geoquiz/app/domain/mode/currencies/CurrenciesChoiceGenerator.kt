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

/** "the Euro (€, EUR)": name, symbol and code together, so every clue the spec asks for is in one label. */
fun currencyLabel(entry: CurrencyEntry): String = "the ${entry.name} (${entry.symbol}, ${entry.code})"

/**
 * Currencies' multiple-choice question (task 7.1, D50): always used at Easy, and for any shared
 * currency at every tier - [CurrencyAnswerValidator] rejects a typed answer for one, so
 * `ui/currencies/CurrenciesGameViewModel.kt` must never show a text field for it either way.
 *
 * [target] is this item's representative country (see [CurrencyItems]); [currencyRepository]'s
 * already-warm cache (see [com.geoquiz.app.data.currencies.CurrencyRepository.cachedOrNull]'s
 * KDoc for why this is safe here) gives back the real [CurrencyEntry] to build the prompt and,
 * for a shared currency, the full membership list the one correct option must come from.
 *
 * The entry lookup is scoped to [quizSet] - the round's own items, i.e. exactly one
 * representative per currency already (task 7.1 review: looking the target up in a freshly
 * *global* [CurrencyItems.buildAll] call could pick a different representative than the one this
 * round actually has, since representative assignment is scoped to a candidate set - see
 * [CurrencyItems]'s KDoc). [quizSet] is always a safe, unambiguous universe for this: it already
 * contains exactly one true member per shared item in the round, so re-deriving against it just
 * confirms that same pick.
 *
 * - Shared: the correct option is [target] itself (always a true member, by construction); the
 *   three distractors are countries that are neither in [CurrencyEntry.countries] (D50: "exactly
 *   one valid option") nor in [CurrencyEntry.listedInBasketBy] (task 7.1 review: a basket country
 *   - currently only Zimbabwe - genuinely also circulates this currency unofficially, so it would
 *   be an ambiguous "wrong" option, not a safely wrong one); the target's region preferred where
 *   that still leaves three.
 * - Not shared: the same exclusion rule, for the same reason - a non-shared currency can still
 *   have a basket country (e.g. Zimbabwe also unofficially uses the Botswana pula), so the
 *   generic "exclude true members and basket countries" rule covers both cases with one path.
 *
 * Falls back to the target's own name as the label if the cache is somehow cold or the item
 * cannot be found (should never happen once a round has loaded), rather than crashing mid-quiz.
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
        val entry = currencyRepository.cachedOrNull()?.let { data ->
            val quizCodes = quizSet.mapTo(mutableSetOf()) { it.code }
            val quizCountriesByCode = quizSet.associateBy { it.code }
            CurrencyItems.build(data, quizCountriesByCode, quizCodes).find { it.country.code == target.code }?.entry
        }
        val prompt = ChoicePrompt.CurrencyOf(entry?.let(::currencyLabel) ?: target.name)
        val excluded = entry?.let { it.countries.toSet() + it.listedInBasketBy.toSet() } ?: emptySet()
        val distractorPool = if (excluded.isEmpty()) allCountries else allCountries.filter { it.code !in excluded }
        val distractors = pickSameRegionFirst(target, distractorPool, CHOICE_OPTION_COUNT - 1, random)
        return buildQuestion(target, distractors, prompt, random) { it.name }
    }
}
