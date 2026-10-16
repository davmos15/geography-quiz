package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.buildQuestion
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import kotlin.random.Random

/**
 * Builds one flag speed question (task 7.3, D52): the drawn target's flag
 * ([ChoicePrompt.FlagOf], reusing the existing shape exactly, D52), with the OTHER members of its
 * lookalike set as the only distractors - never generic same-region countries, which would defeat
 * a lookalike round's whole point.
 *
 * Implements the shared [ChoiceQuestionGenerator] so [FlagSpeedGameMode.choiceGenerator] has a
 * value (every tier of this mode is multiple choice, D52), but repurposes its [quizSet]
 * parameter: there is no fixed quiz set in this mode's open-ended timed round, so the caller
 * ([com.geoquiz.app.ui.flagspeed.FlagSpeedGameViewModel]) resolves the drawn lookalike set's
 * other members to [Country] and passes them as [quizSet], instead of "every item in the quiz".
 * [allCountries] and [category] are unused.
 *
 * A set with only two members gives a two-option question; this is never padded with unrelated
 * countries (D52), so fewer than [com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT] options is
 * expected and correct, not a bug.
 */
object FlagSpeedChoiceGenerator : ChoiceQuestionGenerator {
    override fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion {
        val distractors = quizSet.filter { it.code != target.code }.distinctBy { it.code }
        return buildQuestion(target, distractors, ChoicePrompt.FlagOf(target.code), random) { it.name }
    }
}
