package com.geoquiz.app.domain.mode.classic

import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.mode.CapitalsChoiceGenerator
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.CountriesChoiceGenerator
import com.geoquiz.app.domain.mode.FlagsChoiceGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.ModeLabels
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase
import javax.inject.Inject

/*
 * Registrations for the three original modes. Each is "type every name in the set" at Normal and
 * Hard, multiple choice at Easy (domain/mode/MultipleChoice.kt), and scores with
 * CalculateScoreUseCase.
 * They declare no hints yet: hints are built in Phase 8, and a mode lists a HintType only once
 * the hint works for it.
 */

private val ALL_DIFFICULTIES = Difficulty.entries.toSet()

/** Countries: type every country in the set. */
class CountriesGameMode @Inject constructor(
    override val generator: CountriesQuestionGenerator,
    validateAnswer: ValidateAnswerUseCase,
    calculateScore: CalculateScoreUseCase
) : GameMode {
    override val spec: GameModeSpec = SPEC
    override val validator = AnswerValidator { input, state, allowFuzzy -> validateAnswer(input, state, allowFuzzy) }
    override val scoring = ScoringRule { state -> calculateScore(state) }
    override val choiceGenerator: ChoiceQuestionGenerator = CountriesChoiceGenerator

    companion object {
        val SPEC = GameModeSpec(
            id = QuizMode.COUNTRIES.id,
            labels = ModeLabels(
                name = R.string.mode_countries_name,
                inputLabel = R.string.mode_countries_input_label,
                itemsNoun = R.string.mode_countries_items,
                pausedProgress = R.string.mode_countries_paused_progress,
                giveUpMessage = R.string.mode_countries_give_up,
                a11yProgress = R.plurals.a11y_progress_countries
            ),
            icon = ModeIcon.GLOBE,
            answerType = AnswerType.TYPED_NAME,
            supportedDifficulties = ALL_DIFFICULTIES,
            hintTypes = emptySet(),
            featureFlag = null,
            sortOrder = 0
        )
    }
}

/** Capitals: type the capital city of every country in the set. */
class CapitalsGameMode @Inject constructor(
    override val generator: CapitalsQuestionGenerator,
    validateCapitalAnswer: ValidateCapitalAnswerUseCase,
    calculateScore: CalculateScoreUseCase
) : GameMode {
    override val spec: GameModeSpec = SPEC
    override val validator = AnswerValidator { input, state, allowFuzzy ->
        validateCapitalAnswer(input, state, allowFuzzy)
    }
    override val scoring = ScoringRule { state -> calculateScore(state) }
    override val choiceGenerator: ChoiceQuestionGenerator = CapitalsChoiceGenerator

    companion object {
        val SPEC = GameModeSpec(
            id = QuizMode.CAPITALS.id,
            labels = ModeLabels(
                name = R.string.mode_capitals_name,
                inputLabel = R.string.mode_capitals_input_label,
                itemsNoun = R.string.mode_capitals_items,
                pausedProgress = R.string.mode_capitals_paused_progress,
                giveUpMessage = R.string.mode_capitals_give_up,
                a11yProgress = R.plurals.a11y_progress_capitals
            ),
            icon = ModeIcon.LANDMARK,
            answerType = AnswerType.TYPED_NAME,
            supportedDifficulties = ALL_DIFFICULTIES,
            hintTypes = emptySet(),
            featureFlag = null,
            sortOrder = 1
        )
    }
}

/** Flags: type every country whose flag matches the category; answers are country names. */
class FlagsGameMode @Inject constructor(
    override val generator: FlagsQuestionGenerator,
    validateAnswer: ValidateAnswerUseCase,
    calculateScore: CalculateScoreUseCase
) : GameMode {
    override val spec: GameModeSpec = SPEC
    override val validator = AnswerValidator { input, state, allowFuzzy -> validateAnswer(input, state, allowFuzzy) }
    override val scoring = ScoringRule { state -> calculateScore(state) }
    override val choiceGenerator: ChoiceQuestionGenerator = FlagsChoiceGenerator

    companion object {
        val SPEC = GameModeSpec(
            id = QuizMode.FLAGS.id,
            labels = ModeLabels(
                name = R.string.mode_flags_name,
                inputLabel = R.string.mode_flags_input_label,
                itemsNoun = R.string.mode_flags_items,
                pausedProgress = R.string.mode_flags_paused_progress,
                giveUpMessage = R.string.mode_flags_give_up,
                a11yProgress = R.plurals.a11y_progress_flags
            ),
            icon = ModeIcon.FLAG,
            answerType = AnswerType.TYPED_NAME,
            supportedDifficulties = ALL_DIFFICULTIES,
            hintTypes = emptySet(),
            featureFlag = null,
            sortOrder = 2
        )
    }
}
