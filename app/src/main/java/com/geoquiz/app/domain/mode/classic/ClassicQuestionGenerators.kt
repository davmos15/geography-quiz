package com.geoquiz.app.domain.mode.classic

import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.GetCountriesForCapitalQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import javax.inject.Inject

/*
 * The classic modes ask for every name in the set. A flag category (colour, colour count,
 * element) always uses the flag filter, whatever the mode, as QuizViewModel did before the
 * mode framework; any other category filters on the country name or, in Capitals, the capital.
 */

/** Countries mode: flag filter for flag categories, otherwise filters on the country name. */
class CountriesQuestionGenerator @Inject constructor(
    private val getCountriesForQuiz: GetCountriesForQuizUseCase,
    private val getCountriesForFlagQuiz: GetCountriesForFlagQuizUseCase
) : QuestionGenerator {
    override suspend fun items(category: QuizCategory): List<Country> =
        if (category.isFlagCategory) getCountriesForFlagQuiz(category) else getCountriesForQuiz(category)
}

/** Capitals mode: flag filter for flag categories, otherwise filters on the capital name. */
class CapitalsQuestionGenerator @Inject constructor(
    private val getCountriesForCapitalQuiz: GetCountriesForCapitalQuizUseCase,
    private val getCountriesForFlagQuiz: GetCountriesForFlagQuizUseCase
) : QuestionGenerator {
    override suspend fun items(category: QuizCategory): List<Country> =
        if (category.isFlagCategory) getCountriesForFlagQuiz(category) else getCountriesForCapitalQuiz(category)
}

/** Flags mode: flag filter for flag categories, otherwise filters on the country name. */
class FlagsQuestionGenerator @Inject constructor(
    private val getCountriesForQuiz: GetCountriesForQuizUseCase,
    private val getCountriesForFlagQuiz: GetCountriesForFlagQuizUseCase
) : QuestionGenerator {
    override suspend fun items(category: QuizCategory): List<Country> =
        if (category.isFlagCategory) getCountriesForFlagQuiz(category) else getCountriesForQuiz(category)
}
