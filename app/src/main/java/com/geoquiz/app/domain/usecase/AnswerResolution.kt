package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizState

/**
 * Shared answer rules for [ValidateAnswerUseCase] and [ValidateCapitalAnswerUseCase].
 *
 * 1. An exact alias match always wins, for any country, so a typo rule can never turn
 *    one valid answer into another country.
 * 2. Otherwise [FuzzyAnswerMatcher] looks for aliases one edit away:
 *    - with [allowFuzzy], a single matching country is judged like an exact answer
 *      ([AnswerResult.Correct] with `viaTypo`, AlreadyAnswered or Incorrect);
 *    - without it (hard mode), a match on an unanswered country in this quiz is a
 *      [AnswerResult.NearMiss] (never accepted, never a strike); an answered one is
 *      AlreadyAnswered; one outside the quiz is Incorrect, as it would be if spelled correctly;
 *    - a match on two or more countries is a NearMiss if any of them is in the quiz, else Incorrect.
 * 3. Anything else is Incorrect.
 */
internal suspend fun resolveAnswer(
    normalizedInput: String,
    state: QuizState,
    allowFuzzy: Boolean,
    exactMatch: suspend () -> Country?,
    aliases: suspend () -> List<AnswerAlias>
): AnswerResult {
    if (normalizedInput.isBlank()) return AnswerResult.Incorrect

    exactMatch()?.let { return judge(it, state, viaTypo = false) }

    return when (val match = FuzzyAnswerMatcher.match(normalizedInput, aliases())) {
        FuzzyAnswerMatcher.Match.None -> AnswerResult.Incorrect
        // "Close" only makes sense if one of the candidates could still count in this quiz.
        is FuzzyAnswerMatcher.Match.Ambiguous ->
            if (match.countries.none { it.inQuiz(state) }) AnswerResult.Incorrect else AnswerResult.NearMiss
        is FuzzyAnswerMatcher.Match.Unique ->
            when {
                allowFuzzy -> judge(match.country, state, viaTypo = true)
                !match.country.inQuiz(state) -> AnswerResult.Incorrect
                match.country.code in state.answeredCountries -> AnswerResult.AlreadyAnswered
                else -> AnswerResult.NearMiss
            }
    }
}

private fun Country.inQuiz(state: QuizState) = state.quiz.countries.any { it.code == code }

private fun judge(country: Country, state: QuizState, viaTypo: Boolean): AnswerResult {
    if (!country.inQuiz(state)) return AnswerResult.Incorrect
    if (country.code in state.answeredCountries) return AnswerResult.AlreadyAnswered
    return AnswerResult.Correct(country.name, viaTypo)
}
