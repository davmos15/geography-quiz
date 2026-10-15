package com.geoquiz.app.domain.mode.tapmap

import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import javax.inject.Inject

/**
 * Tap the map's items (task 5.2): every member of the chosen [QuizCategory.MapRegion] (World or
 * one continent), or the exact codes of a [QuizCategory.Practice] set, each asked once, in random
 * order. [GetCountriesForQuizUseCase] already resolves both category types (task 5.0); this only
 * adds the shuffle, via the shared [QuizRandom] so a seeded test is deterministic.
 */
class TapMapQuestionGenerator @Inject constructor(
    private val getCountriesForQuiz: GetCountriesForQuizUseCase,
    private val quizRandom: QuizRandom
) : QuestionGenerator {
    override suspend fun items(category: QuizCategory): List<Country> =
        getCountriesForQuiz(category).shuffled(quizRandom.random)
}
