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
 * - Shared: the correct option is [target] itself (always a true member, by construction); the
 *   three distractors are countries that are *not* in [CurrencyEntry.countries] (D50: "exactly
 *   one valid option"), the target's region preferred where that still leaves three.
 * - Not shared: a plain four-name question, like every other mode's Easy tier - there is exactly
 *   one country in the data for a non-shared code, so no membership exclusion is needed.
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
            val allCountriesByCode = allCountries.associateBy { it.code }
            CurrencyItems.buildAll(data, allCountriesByCode).find { it.country.code == target.code }?.entry
        }
        val prompt = ChoicePrompt.CurrencyOf(entry?.let(::currencyLabel) ?: target.name)
        val distractorPool = if (entry != null && entry.shared) {
            val members = entry.countries.toSet()
            allCountries.filter { it.code !in members }
        } else {
            allCountries
        }
        val distractors = pickSameRegionFirst(target, distractorPool, CHOICE_OPTION_COUNT - 1, random)
        return buildQuestion(target, distractors, prompt, random) { it.name }
    }
}
