package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import javax.inject.Inject

/**
 * Border hop's candidate pool (task 6.1): every country [category] offers (All countries, a
 * region or a subregion, [BorderHopGameMode.offers]), the same filter the classic Countries mode
 * uses. [BorderHopSetupViewModel][com.geoquiz.app.ui.borderhop.BorderHopSetupViewModel] and
 * [BorderHopGameViewModel][com.geoquiz.app.ui.borderhop.BorderHopGameViewModel] pass this list to
 * [BorderGraph.randomPuzzle] to pick a start/target pair; the pair is not otherwise restricted to
 * stay inside the category once the round starts (hops may pass through any country in the
 * graph, task 6.1 review).
 */
class BorderHopQuestionGenerator @Inject constructor(
    private val getCountriesForQuiz: GetCountriesForQuizUseCase
) : QuestionGenerator {
    override suspend fun items(category: QuizCategory): List<Country> = getCountriesForQuiz(category)
}
