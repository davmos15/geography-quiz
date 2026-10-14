package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.ModeLabels
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import javax.inject.Inject

/**
 * Country silhouettes (task 5.1): an outline is shown and the player types the country's name
 * (Normal/Hard, typos allowed at Normal, D12) or picks one of four options (Easy,
 * [SilhouettesChoiceGenerator]); 3 lives (D34) and placeholder hint costs (D37,
 * [HintCosts.applyTo]) apply. [generator] is [SilhouettesQuestionGenerator]: 15 random countries
 * from the category's filter (fewer if smaller), or a practice set's exact codes in order. The
 * typed validator is unchanged from Countries ([ValidateAnswerUseCase] over the whole country
 * list): `ui/silhouettes/SilhouettesGameViewModel.kt` judges one answer at a time by checking
 * which country (if any) the result names against the silhouette on screen.
 *
 * [countriesQuestionGenerator] (every country matching the category, unchanged constructor shape
 * for `testutil/TestGameModes.kt`) is this mode's filter; [quizRandom] defaults to
 * [QuizRandom.random]'s production randomness, seeded only in tests.
 */
class SilhouettesGameMode @Inject constructor(
    countriesQuestionGenerator: CountriesQuestionGenerator,
    validateAnswer: ValidateAnswerUseCase,
    calculateScore: CalculateScoreUseCase,
    quizRandom: QuizRandom = QuizRandom()
) : GameMode {
    override val spec: GameModeSpec = SPEC
    override val generator: QuestionGenerator = SilhouettesQuestionGenerator(countriesQuestionGenerator, quizRandom)
    override val validator = AnswerValidator { input, state, allowFuzzy -> validateAnswer(input, state, allowFuzzy) }
    override val scoring = ScoringRule { state -> HintCosts.applyTo(calculateScore(state), state.hintsUsed) }
    override val choiceGenerator: ChoiceQuestionGenerator = SilhouettesChoiceGenerator

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
