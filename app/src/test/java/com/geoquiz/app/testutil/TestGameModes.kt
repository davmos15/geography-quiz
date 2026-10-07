package com.geoquiz.app.testutil

import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.classic.CapitalsGameMode
import com.geoquiz.app.domain.mode.classic.CapitalsQuestionGenerator
import com.geoquiz.app.domain.mode.classic.CountriesGameMode
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.classic.FlagsGameMode
import com.geoquiz.app.domain.mode.classic.FlagsQuestionGenerator
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForCapitalQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase
import io.mockk.mockk

/**
 * The production registry with the three classic modes, wired to the given use cases
 * (strict mocks by default, so an unexpected call fails the test) and the real scoring.
 */
object TestGameModes {

    fun registry(
        getCountriesForQuiz: GetCountriesForQuizUseCase = mockk(),
        getCountriesForCapitalQuiz: GetCountriesForCapitalQuizUseCase = mockk(),
        getCountriesForFlagQuiz: GetCountriesForFlagQuizUseCase = mockk(),
        validateAnswer: ValidateAnswerUseCase = mockk(),
        validateCapitalAnswer: ValidateCapitalAnswerUseCase = mockk(),
        calculateScore: CalculateScoreUseCase = CalculateScoreUseCase()
    ): GameModeRegistry = GameModeRegistry(
        setOf(
            CountriesGameMode(
                CountriesQuestionGenerator(getCountriesForQuiz, getCountriesForFlagQuiz),
                validateAnswer,
                calculateScore
            ),
            CapitalsGameMode(
                CapitalsQuestionGenerator(getCountriesForCapitalQuiz, getCountriesForFlagQuiz),
                validateCapitalAnswer,
                calculateScore
            ),
            FlagsGameMode(
                FlagsQuestionGenerator(getCountriesForQuiz, getCountriesForFlagQuiz),
                validateAnswer,
                calculateScore
            )
        )
    )
}
