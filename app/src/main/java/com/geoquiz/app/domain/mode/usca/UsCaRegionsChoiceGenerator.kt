package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.buildQuestion
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import kotlin.random.Random

/**
 * Multiple choice's question (task 6.2): the target plus three other members of this round's own
 * group, shuffled. Unlike
 * [com.geoquiz.app.domain.mode.tapmap.TapMapChoiceGenerator] there is no "same region first"
 * preference to apply - a US state or Canadian province has no finer region/subregion to prefer
 * within its own 50- or 13-member set (task 6.2 review) - and unlike
 * [com.geoquiz.app.domain.mode.CountriesChoiceGenerator] the quiz set itself (13 provinces, or 50
 * states) is always at least [CHOICE_OPTION_COUNT], so a question never needs to top up from
 * outside it. [quizSet] is passed as [allCountries] too by the screen that calls this (there is
 * nothing else to draw distractors from).
 */
object UsCaRegionsChoiceGenerator : ChoiceQuestionGenerator {
    override fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion {
        val distractors = quizSet.filter { it.code != target.code }.shuffled(random).take(DISTRACTOR_COUNT)
        // The prompt is never shown (showPrompt = false): the map highlights the target instead.
        return buildQuestion(target, distractors, ChoicePrompt.InSet(""), random) { it.name }
    }

    private const val DISTRACTOR_COUNT = CHOICE_OPTION_COUNT - 1
}
