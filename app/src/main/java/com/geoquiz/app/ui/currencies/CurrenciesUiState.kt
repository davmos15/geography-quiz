package com.geoquiz.app.ui.currencies

import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.currencies.CurrencyItem
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory

/** One answer's result, shown until the player taps Next. */
sealed class CurrencyFeedback {
    data object None : CurrencyFeedback()

    /** Answered correctly (typed or picked). [fact] is shown alongside it when the currency has one. */
    data class Correct(val countryName: String, val fact: String?) : CurrencyFeedback()

    /** A typo in Hard, or an ambiguous one in Normal; never a strike. */
    data object NearMiss : CurrencyFeedback()

    /** Typed input that named no country, or a real country that was not the target. */
    data object Incorrect : CurrencyFeedback()

    /** Easy, or a shared currency's choice question, answered wrong: the right country and its fact. */
    data class Revealed(val countryName: String, val fact: String?) : CurrencyFeedback()
}

/**
 * One Currencies round (task 7.1): [items] is fixed for the round (every currency the category
 * filter allows, [com.geoquiz.app.domain.mode.currencies.CurrencyQuestionGenerator]'s order);
 * [currentIndex] points at the item on screen. [answeredCodes] are correctly named; everything
 * else in [items] not answered counts as missed once the round finishes (give-up included).
 *
 * Unlike every classic mode, the answer mechanism is decided per item, not per [difficulty]
 * (D50): [isCurrentChoice] is true at [Difficulty.EASY] or whenever the current item's currency
 * is shared, whatever the tier.
 */
data class CurrencyRoundState(
    val category: QuizCategory,
    val difficulty: Difficulty,
    val items: List<CurrencyItem>,
    val currentIndex: Int = 0,
    val answeredCodes: Set<String> = emptySet(),
    /** Wrong typed answers and wrong Easy/shared picks; three end the round at Hard. */
    val incorrectGuesses: Int = 0,
    val incorrectGuessStrings: List<String> = emptyList(),
    val currentInput: String = "",
    val feedback: CurrencyFeedback = CurrencyFeedback.None,
    /** Set whenever the current item is a choice question (Easy, or any shared currency). */
    val choice: ChoiceQuestion? = null,
    val choiceFeedback: ChoiceFeedback? = null,
    val isComplete: Boolean = false,
    /** True once the current item has been answered: input/options are locked until Next. */
    val isAdvancing: Boolean = false,
    val paused: Boolean = false,
    /** Bumped on every commit so repeated identical feedback is still a new value to announce. */
    val feedbackToken: Int = 0
) {
    val current: CurrencyItem? get() = items.getOrNull(currentIndex)
    val progress: Int get() = currentIndex.coerceAtMost(items.size)

    /** D50: Easy, or any shared currency, is always a four-option question, never typed. */
    val isCurrentChoice: Boolean
        get() = difficulty == Difficulty.EASY || current?.entry?.shared == true
}

sealed class CurrenciesUiState {
    data object Loading : CurrenciesUiState()
    data class Active(val state: CurrencyRoundState) : CurrenciesUiState()
}

/** A recorded round on its way to Results, mirroring `SilhouettesCompletion`/`QuizCompletion`. */
data class CurrenciesCompletion(val resultId: String, val step: Step) {
    enum class Step { NAVIGATE, DONE }
}
