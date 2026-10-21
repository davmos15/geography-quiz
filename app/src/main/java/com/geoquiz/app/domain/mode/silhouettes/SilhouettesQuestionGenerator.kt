package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory

/**
 * A silhouettes round (task 5.1, D34): 15 random countries from [pool]'s filtered set (fewer if
 * the filter has fewer than 15), or, for a practice set ([QuizCategory.Practice]), exactly its
 * codes in order (already how [pool] returns them, see `GetCountriesForQuizUseCase.practiceItems`).
 *
 * [pool] is this mode's [CountriesQuestionGenerator][com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator]
 * (every country matching the category, flag categories never offered by this mode's [SilhouettesGameMode.offers]).
 * [quizRandom] is seeded in tests for a deterministic round.
 */
class SilhouettesQuestionGenerator(
    private val pool: QuestionGenerator,
    private val quizRandom: QuizRandom
) : QuestionGenerator {

    /**
     * [pool]'s candidates, minus [SilhouetteLegibility.EXCLUDED_CCA3] (task P10-E, Dav's device
     * feedback: some silhouettes, like Tuvalu's, are unrecognisable). Applied before the practice
     * short-circuit below, so a practice set made entirely of excluded codes correctly resolves to
     * an empty list rather than quietly asking about countries that weren't missed -
     * `SilhouettesGameViewModel` turns that into its `Unavailable` state.
     */
    override suspend fun items(category: QuizCategory): List<Country> {
        val candidates = pool.items(category).filterNot { it.code in SilhouetteLegibility.EXCLUDED_CCA3 }
        if (category is QuizCategory.Practice) return candidates
        return candidates.shuffled(quizRandom.random).take(ROUND_SIZE)
    }

    companion object {
        /** A round asks about this many countries, or fewer if the filter is smaller (D34). */
        const val ROUND_SIZE = 15
    }
}
