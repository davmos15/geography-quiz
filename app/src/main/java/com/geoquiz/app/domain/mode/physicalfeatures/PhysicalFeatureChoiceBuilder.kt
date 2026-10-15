package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import kotlin.random.Random

/**
 * Easy's four-name question (task 7.2, D51): [pool] is every feature in the target's tab, any
 * continent (the brief's "4 feature names from the SAME type tab" - continent is not part of
 * that rule, so there are always enough distractors even for a one- or two-member continent
 * filter). Builds [ChoiceQuestion] directly with [ChoicePrompt.FeatureOf] rather than going
 * through [com.geoquiz.app.domain.mode.ChoiceQuestionGenerator] (D51 review): that interface's
 * `question()` is typed to [com.geoquiz.app.domain.model.Country]/[com.geoquiz.app.domain.model.QuizCategory]
 * throughout, neither of which this mode has a classic equivalent for (same reasoning as
 * [com.geoquiz.app.domain.mode.usca.UsCaRegionsChoiceGenerator], a plain object with its own
 * `question()` rather than that interface).
 */
object PhysicalFeatureChoiceBuilder {
    fun question(target: PhysicalFeature, pool: List<PhysicalFeature>, random: Random): ChoiceQuestion {
        val distractors = pool.asSequence()
            .filter { it.id != target.id }
            .distinctBy { it.id }
            .toList()
            .shuffled(random)
            .take(CHOICE_OPTION_COUNT - 1)
        val options = (listOf(target) + distractors)
            .map { ChoiceOption(it.id, it.name) }
            .distinctBy { it.code }
            .shuffled(random)
        return ChoiceQuestion(targetCode = target.id, options = options, prompt = ChoicePrompt.FeatureOf(target.name))
    }
}
