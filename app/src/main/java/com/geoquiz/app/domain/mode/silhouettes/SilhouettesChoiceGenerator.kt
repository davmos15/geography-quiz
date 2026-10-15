package com.geoquiz.app.domain.mode.silhouettes

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
 * Silhouettes Easy (task 5.1): the outline is already on screen (the prompt), so the question is
 * simply four name options - the target plus three other countries, from its region where
 * possible (any country in the game, not just this round's set, following
 * [com.geoquiz.app.domain.mode.FlagsChoiceGenerator]'s pattern since an outline, like a flag, has
 * nothing to do with set membership). [ChoiceQuestion.prompt] is built for data-shape parity with
 * every other [ChoiceQuestionGenerator], but the screen passes `showPrompt = false` to
 * [com.geoquiz.app.ui.quiz.components.MultipleChoicePanel] and shows its own "Which country is
 * this?" next to the outline instead (task 5.1 review, B1): the outline is the real prompt.
 */
object SilhouettesChoiceGenerator : ChoiceQuestionGenerator {
    override fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion {
        val distractors = pickSameRegionFirst(target, allCountries, CHOICE_OPTION_COUNT - 1, random)
        return buildQuestion(target, distractors, ChoicePrompt.InSet(category.displayName), random) { it.name }
    }
}
