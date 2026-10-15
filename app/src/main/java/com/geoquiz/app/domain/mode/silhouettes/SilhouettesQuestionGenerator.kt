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

    override suspend fun items(category: QuizCategory): List<Country> {
        val candidates = pool.items(category)
        if (category is QuizCategory.Practice) return candidates
        return candidates.shuffled(quizRandom.random).take(ROUND_SIZE)
    }

    companion object {
        /** A round asks about this many countries, or fewer if the filter is smaller (D34). */
        const val ROUND_SIZE = 15
    }
}
