package com.geoquiz.app.testutil

import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.mode.classic.CapitalsGameMode
import com.geoquiz.app.domain.mode.classic.CapitalsQuestionGenerator
import com.geoquiz.app.domain.mode.classic.CountriesGameMode
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.classic.FlagsGameMode
import com.geoquiz.app.domain.mode.classic.FlagsQuestionGenerator
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizMode
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
        calculateScore: CalculateScoreUseCase = CalculateScoreUseCase(),
        extraModes: Set<GameMode> = emptySet()
    ): GameModeRegistry = GameModeRegistry(
        extraModes + setOf(
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

    /**
     * A non-classic mode behind [featureFlag] for registry and Play tests. Only its spec is
     * real; its labels are borrowed from Countries.
     */
    fun flaggedMode(
        id: String,
        featureFlag: FeatureFlag?,
        sortOrder: Int,
        icon: ModeIcon = ModeIcon.GLOBE
    ): GameMode = object : GameMode {
        override val spec = QuizMode.COUNTRIES.spec.copy(
            id = id,
            icon = icon,
            featureFlag = featureFlag,
            sortOrder = sortOrder
        )
        override val generator = mockk<QuestionGenerator>()
        override val validator = mockk<AnswerValidator>()
        override val scoring = mockk<ScoringRule>()
        override val choiceGenerator = mockk<ChoiceQuestionGenerator>()
    }
}
