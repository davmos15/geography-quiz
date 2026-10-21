package com.geoquiz.app.domain.mode.borderhop

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
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import javax.inject.Inject

/**
 * Border hop (task 6.1, D42/D44; map version task P10-C): a fixed start/target route puzzle over
 * the land-border graph ([BorderGraph]). A blank world map shows the start, the target and the
 * route so far; the player extends the route one land border at a time by naming a country that
 * borders the end of it (typed at Normal/Hard, with suggestions at Normal; one of four options at
 * Easy, [BorderHopChoiceBuilder]). [BorderHopMoves] judges each guess: a non-neighbour is a wrong
 * guess (a strike at Hard). "Undo last hop" backtracks (a core mechanic, free) and the one hint,
 * [HintType.SHOW_VALID_HOP], reveals one working next move (D44, capped per round, D55/D62).
 * Its own screen and ViewModel own the gameplay entirely (D37: no Room resume save;
 * [validator] is not used for answering, because a Border hop guess is judged against every
 * country rather than a quiz set); [scoring] is [BorderHopScoring], which documents how the
 * round packs itself into a plain [com.geoquiz.app.domain.model.QuizState] for
 * [com.geoquiz.app.domain.usecase.CompleteQuizUseCase].
 *
 * [generator] ([BorderHopQuestionGenerator]) resolves the category to its candidate countries,
 * used by the setup screen and the game's own puzzle generator to pick a start/target pair
 * ([BorderGraph.randomPuzzle]); [choiceGenerator] is never called (Easy's options depend on the
 * route, so [BorderHopChoiceBuilder] builds them) and throws if it ever is.
 */
class BorderHopGameMode @Inject constructor(
    override val generator: BorderHopQuestionGenerator,
    validateAnswer: ValidateAnswerUseCase
) : GameMode {
    override val spec: GameModeSpec = SPEC
    override val validator = AnswerValidator { input, state, allowFuzzy -> validateAnswer(input, state, allowFuzzy) }
    override val scoring = ScoringRule { state -> HintCosts.applyTo(BorderHopScoring.score(state), state.hintsUsed) }
    override val choiceGenerator: ChoiceQuestionGenerator =
        ChoiceQuestionGenerator { _, _, _, _, _ -> error("Border hop builds its Easy options with BorderHopChoiceBuilder") }

    /**
     * Border hop offers the categories that make sense for picking a start/target pair on a
     * border graph: every country, a region or a subregion (the pair is drawn from this filter;
     * the path itself may hop through any country, task 6.1 review). No letter, word, flag or
     * capital categories: they say nothing about who borders whom, and a filtered-down set is
     * more likely to have no valid pair at a given difficulty's hop range. No practice sets
     * either ([BorderHopScoring] never produces a meaningful "missed" set to practise).
     */
    override fun offers(category: QuizCategory): Boolean = when (category) {
        is QuizCategory.AllCountries,
        is QuizCategory.ByRegion,
        is QuizCategory.BySubregion -> true
        else -> false
    }

    companion object {
        /** Never change: persisted in Room, DataStore, routes and this mode's own screens. */
        const val MODE_ID = "border_hop"

        /** Hop-distance range for each difficulty tier (D44): the shortest path must fall in it. */
        val EASY_HOPS = 2..3
        val NORMAL_HOPS = 4..5

        /** Natural Earth/mledoze graph's diameter is 21 hops (`data/borders_report.md`); a sensible cap. */
        val HARD_HOPS = 6..21

        fun hopRangeFor(difficulty: Difficulty): IntRange = when (difficulty) {
            Difficulty.EASY -> EASY_HOPS
            Difficulty.NORMAL -> NORMAL_HOPS
            Difficulty.HARD -> HARD_HOPS
        }

        val SPEC = GameModeSpec(
            id = MODE_ID,
            labels = ModeLabels(
                name = R.string.mode_border_hop_name,
                // Never shown: Border hop answers by tapping a neighbour, never typing.
                inputLabel = R.string.mode_border_hop_input_label,
                itemsNoun = R.string.mode_border_hop_items,
                // These are @StringRes in ModeLabels but, like a11yProgress, are now plurals
                // (P10-R2 L4): never read generically (Border hop has its own screen, which reads
                // the plurals directly rather than through GameModeSpec.labels), only kept here
                // so the spec is fully populated.
                pausedProgress = R.string.mode_border_hop_paused_progress_label,
                giveUpMessage = R.string.mode_border_hop_give_up_label,
                a11yProgress = R.plurals.a11y_progress_border_hop
            ),
            icon = ModeIcon.BORDER_HOP,
            answerType = AnswerType.TYPED_NAME,
            supportedDifficulties = Difficulty.entries.toSet(),
            hintTypes = setOf(HintType.SHOW_VALID_HOP),
            featureFlag = FeatureFlag.BORDER_HOP,
            sortOrder = 12
        )
    }
}
