package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.R
import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.ModeLabels
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import javax.inject.Inject

/**
 * Currencies (task 7.1, D47/D48/D50; symbol-only clue task P10-B): only a currency's *symbol* is
 * shown and the player names the country that uses it - typed at Normal/Hard (typos allowed at
 * Normal, like every typed mode), or one of four options at Easy. [CurrencyEntry.isChoiceOnly] is
 * always the four-option question, at every tier: a *shared* currency (the euro, the US dollar,
 * the two CFA francs, and nine more - D50), or a single-owner currency whose symbol is also used
 * by another currency in the data (task P10-B, since more than one country could then be a
 * valid-looking answer for the same symbol). [CurrencySymbolRules.EXCLUDED_CODES] drops a handful
 * of currencies whose symbol itself spells out the country (task P10-B). Flags are shown on every
 * option and a one-line fact (when the currency has one) is shown after answering - only after,
 * never before: the currency's name and code are never shown until the answer is resolved.
 *
 * [generator] ([CurrencyQuestionGenerator]) turns the category's filtered countries into
 * currency items; [choiceGenerator] ([CurrenciesChoiceGenerator]) and [validator]
 * ([CurrencyAnswerValidator]) carry D50's shared/not-shared split themselves. Its own screen and
 * ViewModel (`ui/currencies/`) own the gameplay, the same way every Phase 5-7 mode does (no Room
 * resume save, D37): unlike Border hop, [choiceGenerator] and [validator] *are* real and used
 * here, since both a typed and a multiple-choice tier genuinely exist, just split per item rather
 * than per difficulty tier. [quizRandom] is [CurrencyQuestionGenerator]'s one-per-symbol pick
 * (task P10-R1); defaults to [QuizRandom.random]'s production randomness, seeded only in tests,
 * the same pattern as `SilhouettesGameMode`.
 */
class CurrencyGameMode @Inject constructor(
    countriesQuestionGenerator: CountriesQuestionGenerator,
    countryRepository: CountryRepository,
    currencyRepository: CurrencyRepository,
    validateAnswer: ValidateAnswerUseCase,
    private val calculateScore: CalculateScoreUseCase,
    quizRandom: QuizRandom = QuizRandom()
) : GameMode {
    override val spec: GameModeSpec = SPEC
    override val generator: CurrencyQuestionGenerator =
        CurrencyQuestionGenerator(countriesQuestionGenerator, countryRepository, currencyRepository, quizRandom)
    override val choiceGenerator: ChoiceQuestionGenerator = CurrenciesChoiceGenerator(currencyRepository)
    override val validator: CurrencyAnswerValidator =
        CurrencyAnswerValidator(validateAnswer, countryRepository, currencyRepository)
    override val scoring = ScoringRule { state -> HintCosts.applyTo(calculateScore(state), state.hintsUsed) }

    /**
     * Currencies offers the categories that make sense for "countries that have a currency":
     * every country, a region, a subregion, and practice sets of previously missed currencies.
     * No flag, letter or word-pattern categories (a currency has nothing to do with a flag's
     * colours or how a country's name is spelled) and no island-nation theme either - currencies
     * are not a visual theme the way outlines or flags are.
     */
    override fun offers(category: QuizCategory): Boolean = when (category) {
        is QuizCategory.AllCountries,
        is QuizCategory.ByRegion,
        is QuizCategory.BySubregion,
        is QuizCategory.Practice -> true
        else -> false
    }

    companion object {
        /** Never change: persisted in Room, DataStore, routes and this mode's own screens. */
        const val MODE_ID = "currencies"

        val SPEC = GameModeSpec(
            id = MODE_ID,
            labels = ModeLabels(
                name = R.string.mode_currencies_name,
                inputLabel = R.string.mode_currencies_input_label,
                itemsNoun = R.string.mode_currencies_items,
                pausedProgress = R.string.mode_currencies_paused_progress,
                giveUpMessage = R.string.mode_currencies_give_up,
                a11yProgress = R.plurals.a11y_progress_currencies
            ),
            icon = ModeIcon.CURRENCY,
            // Normal/Hard mostly type the answer; a shared currency is the exception (D50), always
            // multiple choice. AnswerType otherwise only distinguishes the Normal/Hard mechanism.
            answerType = AnswerType.TYPED_NAME,
            supportedDifficulties = Difficulty.entries.toSet(),
            // No hint makes much sense for "what currency does this use" (task 7.1 spec).
            hintTypes = emptySet(),
            featureFlag = FeatureFlag.CURRENCIES,
            sortOrder = 14
        )
    }
}
