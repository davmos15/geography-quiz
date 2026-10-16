package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.ModeLabels
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import javax.inject.Inject

/**
 * Flag speed round (task 7.3, D52): one continuous 60-second round of flag-lookalike questions.
 * [com.geoquiz.app.ui.flagspeed.FlagSpeedGameViewModel] runs the live draw
 * ([FlagSpeedRoundDrawer]) and the countdown itself (D37-style: its own screen and ViewModel, no
 * Room resume save); [generator] ([FlagSpeedQuestionGenerator]) and [validator] are thin wrappers
 * that satisfy [GameMode] but are not used for answering - see their own KDoc. Every tier is
 * [AnswerType.MULTIPLE_CHOICE] (there is no typed tier: the point of a speed round is fast
 * recognition, not typing), so [choiceGenerator] ([FlagSpeedChoiceGenerator]) is effectively the
 * only generator this mode needs for play. [scoring] is [FlagSpeedScoring], which documents how a
 * finished round packs into a plain [com.geoquiz.app.domain.model.QuizState]. No hints
 * ([hintTypes] empty): a speed round has no pause to spend one in.
 */
class FlagSpeedGameMode @Inject constructor(
    override val generator: FlagSpeedQuestionGenerator
) : GameMode {
    override val spec: GameModeSpec = SPEC

    /** Never used: this mode never takes a typed answer, at any tier (D52). */
    override val validator = AnswerValidator { _, _, _ -> AnswerResult.None }

    override val scoring = ScoringRule { state -> FlagSpeedScoring.score(state) }

    override val choiceGenerator: ChoiceQuestionGenerator = FlagSpeedChoiceGenerator

    companion object {
        /** Never change: persisted in Room, DataStore and routes. */
        const val MODE_ID = "flag_speed_round"

        /** Fixed round length (D52); "Give up" ends it early with whatever time actually elapsed. */
        const val ROUND_DURATION_SECONDS = 60

        val SPEC = GameModeSpec(
            id = MODE_ID,
            labels = ModeLabels(
                name = R.string.mode_flag_speed_round_name,
                // Never shown: this mode is multiple choice only, at every tier.
                inputLabel = R.string.mode_flag_speed_round_input_label,
                itemsNoun = R.string.mode_flag_speed_round_items,
                pausedProgress = R.string.mode_flag_speed_round_paused_progress,
                giveUpMessage = R.string.mode_flag_speed_round_give_up,
                a11yProgress = R.plurals.a11y_progress_flag_speed_round
            ),
            icon = ModeIcon.FLAG,
            answerType = AnswerType.MULTIPLE_CHOICE,
            supportedDifficulties = Difficulty.entries.toSet(),
            hintTypes = emptySet(),
            featureFlag = FeatureFlag.FLAG_SPEED_ROUND,
            sortOrder = 16,
            // Task 7.3 fix round, item 2: `ModeRoutes.start`/`practise` only ever build
            // `Screen.FlagSpeed.createRoute(difficulty)` for this mode's id - there is no category
            // argument on that route at all, so a `QuizCategory.Practice` route value from
            // "Practise the ones you missed" would be silently dropped and this mode would start
            // an ordinary full 60-second round instead (and that round would get recorded,
            // breaking D21). Same precedent and reasoning as `PhysicalFeaturesGameMode`: hide the
            // button entirely rather than let it quietly do the wrong thing.
            supportsPractice = false
        )
    }
}
