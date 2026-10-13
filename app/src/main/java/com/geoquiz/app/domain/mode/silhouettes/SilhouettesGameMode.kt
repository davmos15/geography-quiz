package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.CountriesChoiceGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.ModeLabels
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import javax.inject.Inject

/**
 * Country silhouettes (task 5.1 builds the real screen; this registers the mode so shared
 * plumbing - results, history, routes - works end to end). An outline is shown and the player
 * types every country's name in the set, exactly like Countries at Normal and Hard; Easy is
 * multiple choice. Task 5.1 replaces [generator] and [choiceGenerator] with ones that draw from
 * the map outlines; for now it reuses the Countries behaviour so the [GameMode] contract is met.
 */
class SilhouettesGameMode @Inject constructor(
    override val generator: CountriesQuestionGenerator,
    validateAnswer: ValidateAnswerUseCase,
    calculateScore: CalculateScoreUseCase
) : GameMode {
    override val spec: GameModeSpec = SPEC
    override val validator = AnswerValidator { input, state, allowFuzzy -> validateAnswer(input, state, allowFuzzy) }
    override val scoring = ScoringRule { state -> calculateScore(state) }
    override val choiceGenerator: ChoiceQuestionGenerator = CountriesChoiceGenerator

    /**
     * Silhouettes offers the same categories as outlines make sense for: every country, region
     * and subregion, the three letter-position categories, islands (a visual theme that matches
     * outlines well) and practice sets. No flag, word-pattern or capital categories: an outline
     * has nothing to do with a flag's colours or a name's letters beyond where it starts or ends.
     */
    override fun offers(category: QuizCategory): Boolean = when (category) {
        is QuizCategory.AllCountries,
        is QuizCategory.ByRegion,
        is QuizCategory.BySubregion,
        is QuizCategory.StartingWithLetter,
        is QuizCategory.EndingWithLetter,
        is QuizCategory.ContainingLetter,
        is QuizCategory.IslandCountries,
        is QuizCategory.Practice -> true
        else -> false
    }

    companion object {
        /** Never change: persisted in Room, DataStore, routes and this mode's own screens. */
        const val MODE_ID = "silhouettes"

        val SPEC = GameModeSpec(
            id = MODE_ID,
            labels = ModeLabels(
                name = R.string.mode_silhouettes_name,
                inputLabel = R.string.mode_silhouettes_input_label,
                itemsNoun = R.string.mode_silhouettes_items,
                pausedProgress = R.string.mode_silhouettes_paused_progress,
                giveUpMessage = R.string.mode_silhouettes_give_up,
                a11yProgress = R.plurals.a11y_progress_silhouettes
            ),
            icon = ModeIcon.SILHOUETTE,
            answerType = AnswerType.TYPED_NAME,
            supportedDifficulties = Difficulty.entries.toSet(),
            hintTypes = setOf(HintType.SHOW_REGION, HintType.FIRST_LETTER, HintType.SHOW_NEIGHBOURS),
            featureFlag = FeatureFlag.COUNTRY_SILHOUETTES,
            sortOrder = 10
        )
    }
}
