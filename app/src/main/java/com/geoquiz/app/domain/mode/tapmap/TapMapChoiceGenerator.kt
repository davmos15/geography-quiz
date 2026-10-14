package com.geoquiz.app.domain.mode.tapmap

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
 * Tap the map's Easy tier (D37): the target is highlighted on the map, so this question's
 * [ChoiceQuestion.prompt] is never shown (the screen reads only [ChoiceQuestion.targetCode] and
 * [ChoiceQuestion.options]; the prompt field is set to [ChoicePrompt.InSet] only to satisfy the
 * shared type). Distractors are three other countries from this quiz's own set (the chosen
 * region or practice set), the target's region first; a quiz set smaller than
 * [CHOICE_OPTION_COUNT] (a small continent, or a short practice set) tops up from every country.
 */
object TapMapChoiceGenerator : ChoiceQuestionGenerator {
    override fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion {
        val distractors = pickSameRegionFirst(target, quizSet, DISTRACTOR_COUNT, random)
        val topUp = if (distractors.size < DISTRACTOR_COUNT) {
            val used = (listOf(target) + distractors).mapTo(HashSet()) { it.code }
            pickSameRegionFirst(target, allCountries.filter { it.code !in used }, DISTRACTOR_COUNT - distractors.size, random)
        } else {
            emptyList()
        }
        return buildQuestion(target, distractors + topUp, ChoicePrompt.InSet(category.displayName), random) { it.name }
    }

    private const val DISTRACTOR_COUNT = CHOICE_OPTION_COUNT - 1
}
