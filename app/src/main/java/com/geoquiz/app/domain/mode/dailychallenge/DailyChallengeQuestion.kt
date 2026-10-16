package com.geoquiz.app.domain.mode.dailychallenge

import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.model.Country

/**
 * What kind of answer one of the daily challenge's 10 items asks for (task 8.1 brief: exactly
 * these four kinds in v1 - no map, outline or currency questions, which would need their own
 * per-item rendering).
 */
enum class DailyChallengeQuestionKind {
    /** Type the country's name (reuses [com.geoquiz.app.domain.usecase.ValidateAnswerUseCase]). */
    TYPED_COUNTRY,

    /** Type the country's capital (reuses [com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase]). */
    TYPED_CAPITAL,

    /** Pick the country for a shown flag ([com.geoquiz.app.domain.mode.ChoicePrompt.FlagOf]). */
    CHOICE_FLAG,

    /** Pick the capital of a named country ([com.geoquiz.app.domain.mode.ChoicePrompt.CapitalOf]). */
    CHOICE_CAPITAL
}

/**
 * One of the daily challenge's 10 items, as built by [DailyChallengeGenerator]. [target] is the
 * country the question is about either way; [choice] is the pre-built multiple-choice question
 * for [DailyChallengeQuestionKind.CHOICE_FLAG]/[DailyChallengeQuestionKind.CHOICE_CAPITAL] (built
 * at generation time, with the same seeded [kotlin.random.Random], so it is part of what makes a
 * date's round deterministic) and is always null for the two typed kinds.
 */
data class DailyChallengeQuestion(
    val target: Country,
    val kind: DailyChallengeQuestionKind,
    val choice: ChoiceQuestion? = null
)
