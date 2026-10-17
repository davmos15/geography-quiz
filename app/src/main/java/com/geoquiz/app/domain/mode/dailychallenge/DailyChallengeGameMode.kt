package com.geoquiz.app.domain.mode.dailychallenge

import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.ModeLabels
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import javax.inject.Inject

/**
 * Daily challenge (task 8.1, D4): 10 mixed questions, the same for everyone on a given local
 * date. Its own setup and game screens/ViewModels (D37's pattern, as every mode since Border hop
 * has used) start a round directly from [DailyChallengeDate.today] rather than through a category
 * list, so - exactly like [com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesGameMode]
 * (D43's pattern) - [generator], [validator] and [choiceGenerator] below are unused placeholders:
 * [com.geoquiz.app.ui.dailychallenge.DailyChallengeGameViewModel] builds its round from
 * [DailyChallengeGenerator] directly, validates typed answers with the existing
 * [com.geoquiz.app.domain.usecase.ValidateAnswerUseCase]/[com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase]
 * per question, and judges the two choice kinds by comparing the picked code to the question's own
 * [com.geoquiz.app.domain.mode.ChoiceQuestion.targetCode] - never through this mode's [validator]
 * or [choiceGenerator] at all. [scoring] ([DailyChallengeScoring]) is the one member
 * [com.geoquiz.app.domain.usecase.CompleteQuizUseCase] actually uses.
 */
class DailyChallengeGameMode @Inject constructor() : GameMode {
    override val spec: GameModeSpec = SPEC

    /** Unused: this mode never resolves a [QuizCategory] to items (see the class doc). */
    override val generator: QuestionGenerator = QuestionGenerator { emptyList() }

    /** Unused: each question is judged directly by the screen's own ViewModel (see the class doc). */
    override val validator = AnswerValidator { _, _, _ ->
        error("DailyChallengeGameMode.validator is unused; DailyChallengeGameViewModel judges each question directly")
    }

    override val scoring = ScoringRule { state -> DailyChallengeScoring.score(state) }

    /** Unused: [DailyChallengeGenerator] builds the round's choice questions instead. */
    override val choiceGenerator: ChoiceQuestionGenerator = ChoiceQuestionGenerator { _, _, _, _, _ ->
        error("DailyChallengeGameMode.choiceGenerator is unused; see DailyChallengeGenerator")
    }

    /**
     * This mode has no classic-style category list (see the class doc): its own setup screen
     * starts a round directly, same pattern as Border hop and Silhouettes' own setup screens.
     */
    override fun offers(category: QuizCategory): Boolean = false

    companion object {
        /** Never change: persisted in Room history, routes and this mode's own screens. */
        const val MODE_ID = "daily_challenge"

        val SPEC = GameModeSpec(
            id = MODE_ID,
            labels = ModeLabels(
                name = R.string.mode_daily_challenge_name,
                inputLabel = R.string.mode_daily_challenge_input_label,
                itemsNoun = R.string.mode_daily_challenge_items,
                pausedProgress = R.string.mode_daily_challenge_paused_progress,
                giveUpMessage = R.string.mode_daily_challenge_give_up,
                a11yProgress = R.plurals.a11y_progress_daily_challenge
            ),
            // Closest existing icon (task 8.1 brief): a new ModeIcon entry would need a change to
            // ui/mode/ModeIcons.kt, which this task's file list does not include.
            icon = ModeIcon.GLOBE,
            // Mixed per item (typed for 6 of the 10, multiple choice for 4); this field is not
            // read anywhere in the app today (confirmed by a repo-wide search for `.answerType`),
            // so the exact value has no behavioural effect. Kept as TYPED_NAME since more items
            // are typed than not.
            answerType = AnswerType.TYPED_NAME,
            // The challenge has one fixed tier (task 8.1 brief); hint costs (task 8.4) are not
            // built yet either.
            supportedDifficulties = setOf(Difficulty.NORMAL),
            hintTypes = emptySet(),
            featureFlag = FeatureFlag.DAILY_CHALLENGE,
            sortOrder = 17,
            // Targets are real cca3 codes, so AnswerReviewViewModel's CountryRepository lookup
            // works normally.
            supportsAnswerReview = true,
            // A day's specific mixed-kind question set (10 items, each with its own kind) has no
            // meaningful "category" to replay via QuizCategory.Practice - unlike a classic miss
            // list, replaying these codes would just restart an ordinary quiz with no connection
            // to what was actually asked or how (same reasoning as PhysicalFeaturesGameMode and
            // UsCaRegionsGameMode, D43).
            supportsPractice = false,
            // One attempt per local date (D54): replaying from Results would only reach the
            // "already played" screen, so Results hides Play Again (task 9.2).
            supportsPlayAgain = false
        )
    }
}
