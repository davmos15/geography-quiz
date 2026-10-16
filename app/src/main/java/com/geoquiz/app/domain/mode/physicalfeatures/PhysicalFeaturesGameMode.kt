package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.AnswerValidator
import com.geoquiz.app.domain.mode.ChoiceQuestionGenerator
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.mode.ModeLabels
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.ScoringRule
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import javax.inject.Inject

/**
 * Rivers, mountains and lakes (task 7.2, D47, D49, D51): its own screens/ViewModel (D37/D43's
 * pattern, as Border hop and US states and Canadian provinces also used), not a
 * [QuestionGenerator] over a filtered [QuizCategory] - there are 101 curated features with no
 * region/subregion/letter structure of their own (D51 review). [generator], [validator] and
 * [choiceGenerator] are unused placeholders (D43's precedent exactly): `ui/physicalfeatures`'s
 * own screens build a round straight from
 * [com.geoquiz.app.data.physicalfeatures.PhysicalFeatureRepository] filtered by
 * [PhysicalFeatureFilter] (tab/continent), validate typed answers with
 * [PhysicalFeatureAnswerValidator] and build Easy's questions with [PhysicalFeatureChoiceBuilder].
 * [scoring] is the one member [com.geoquiz.app.domain.usecase.CompleteQuizUseCase] actually uses:
 * the classic percentage formula ([CalculateScoreUseCase]) over whichever
 * [com.geoquiz.app.domain.model.QuizState] a round packs itself into (every feature asked is one
 * "country" in that state, [PhysicalFeature.toQuizCountry]'s synthetic-Country convention,
 * exactly like Tap the map, Silhouettes and US/CA regions).
 *
 * Integration note (task 7.2 report, L4 review fix: this note was stale - the lead's integration
 * pass has since wired this mode in everywhere it needs to be): registered in
 * `di/GameModeModule.kt` (`@Binds @IntoSet`), routed in `ui/navigation/ModeRoutes.kt`
 * (`start`/`playAgain`/`practise`) and given its own destination in `Screen.kt`/
 * `AppNavigation.kt`.
 */
class PhysicalFeaturesGameMode @Inject constructor(
    calculateScore: CalculateScoreUseCase
) : GameMode {
    override val spec: GameModeSpec = SPEC

    /** Unused: this mode never resolves a [QuizCategory] to items (see the class doc). */
    override val generator: QuestionGenerator = QuestionGenerator { emptyList() }

    /** Unused: [PhysicalFeatureAnswerValidator] checks typed answers instead. */
    override val validator = AnswerValidator { _, _, _ ->
        error("PhysicalFeaturesGameMode.validator is unused; see PhysicalFeatureAnswerValidator")
    }

    override val scoring = ScoringRule { state -> HintCosts.applyTo(calculateScore(state), state.hintsUsed) }

    /** Unused: [PhysicalFeatureChoiceBuilder] builds Easy's questions instead. */
    override val choiceGenerator: ChoiceQuestionGenerator = ChoiceQuestionGenerator { _, _, _, _, _ ->
        error("PhysicalFeaturesGameMode.choiceGenerator is unused; see PhysicalFeatureChoiceBuilder")
    }

    /** This mode has no classic category filter (see the class doc): never offered in the
     * generic category system. */
    override fun offers(category: QuizCategory): Boolean = false

    companion object {
        /** Never change: persisted in Room, DataStore, routes and this mode's own screens. */
        const val MODE_ID = "physical_features"

        val SPEC = GameModeSpec(
            id = MODE_ID,
            labels = ModeLabels(
                name = R.string.mode_physical_features_name,
                inputLabel = R.string.mode_physical_features_input_label,
                itemsNoun = R.string.mode_physical_features_items,
                pausedProgress = R.string.mode_physical_features_paused_progress,
                giveUpMessage = R.string.mode_physical_features_give_up,
                a11yProgress = R.plurals.a11y_progress_physical_features
            ),
            icon = ModeIcon.TERRAIN_FEATURE,
            answerType = AnswerType.TYPED_NAME,
            supportedDifficulties = Difficulty.entries.toSet(),
            // No hints for v1 (task 7.2 brief, same reasoning as Currencies, task 7.1): none of
            // the existing hint types fit a one-feature-at-a-time round cleanly, and the four
            // names at Easy are already this mode's "help", same as Silhouettes/Tap the map at
            // Easy (D41/D39).
            hintTypes = emptySet(),
            featureFlag = FeatureFlag.PHYSICAL_FEATURES,
            sortOrder = 15,
            // This mode's codes are feature ids (e.g. "aral-sea"), not cca3, so
            // AnswerReviewViewModel's CountryRepository lookup would come back blank - same
            // reasoning as US states and Canadian provinces (D43).
            supportsAnswerReview = false,
            // Same feature-id codes would silently mis-parse back into a filter via
            // PhysicalFeaturesCategory.filterFromLabel's safe default (falls back to Rivers with
            // no continent), starting an unrelated round instead of practising the real misses -
            // worse than D43's blank Answer Review, so this hides the button entirely rather than
            // let it quietly do the wrong thing (lead, integration pass, task 7.2).
            supportsPractice = false
        )
    }
}
