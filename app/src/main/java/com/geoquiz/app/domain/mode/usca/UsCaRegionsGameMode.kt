package com.geoquiz.app.domain.mode.usca

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
 * States and territories (task 6.2, D43; renamed and Australia added P10-D2): an admin-1 layer mode with its own
 * `GameMode`, `QuestionGenerator`, answer validator and screens/ViewModels (D37's pattern, as
 * Border hop also used), not literal reuse of Tap the map's classes, which are typed to
 * [com.geoquiz.app.domain.model.Country] throughout. Three variants, carried on the route and the
 * completed quiz exactly like Tap the map's ([com.geoquiz.app.domain.model.CompletedQuiz.variantId]):
 * [VARIANT_TAP] (Normal/Hard: tap the highlighted state/province on the map), [VARIANT_NAME_ALL]
 * (Normal/Hard: type every member's name, D40's precedent) and [VARIANT_CHOICE] (Easy only: four
 * name options for the highlighted target - this mode's multiple-choice tier is its own named
 * variant rather than nested inside [VARIANT_TAP], task 6.2 review; `ui/usca`'s own setup/game
 * screens enforce the Easy-only / Normal-Hard-only split via [difficultiesFor], since
 * [GameModeSpec.supportedDifficulties] is one set for the whole mode).
 *
 * [generator], [validator] and [choiceGenerator] are unused placeholders: this mode's own screens
 * build their round straight from the bundled `admin1_50m.bin` layer via [UsCaRegionSet] (D43;
 * there is no [QuizCategory] filter to resolve - [offers] is always false), validate typed
 * answers with [UsCaRegionsAnswerValidator] and build Easy's questions with
 * [UsCaRegionsChoiceGenerator]. [scoring] is the one member actually used (by
 * [com.geoquiz.app.domain.usecase.CompleteQuizUseCase]): the classic percentage formula
 * ([CalculateScoreUseCase]) over whatever [com.geoquiz.app.domain.model.QuizState] a variant packs
 * its round into (every variant's "quiz" is the full member list, "answered" the ones correctly
 * found/typed/picked), exactly like Tap the map and Silhouettes.
 */
class UsCaRegionsGameMode @Inject constructor(
    calculateScore: CalculateScoreUseCase
) : GameMode {
    override val spec: GameModeSpec = SPEC

    /** Unused: this mode never resolves a [QuizCategory] to items (D43; see the class doc). */
    override val generator: QuestionGenerator = QuestionGenerator { emptyList() }

    /** Unused: [UsCaRegionsAnswerValidator] checks Name all's typed answers instead (D43). */
    override val validator = AnswerValidator { _, _, _ ->
        error("UsCaRegionsGameMode.validator is unused; see UsCaRegionsAnswerValidator")
    }

    override val scoring = ScoringRule { state -> HintCosts.applyTo(calculateScore(state), state.hintsUsed) }

    /** Unused: [UsCaRegionsChoiceGenerator] builds the Multiple choice variant's questions instead. */
    override val choiceGenerator: ChoiceQuestionGenerator = ChoiceQuestionGenerator { _, _, _, _, _ ->
        error("UsCaRegionsGameMode.choiceGenerator is unused; see UsCaRegionsChoiceGenerator")
    }

    /**
     * This mode has no classic category filter (task 6.2 review: "just US or Canada", a fixed
     * member set, not derived from [QuizCategory]): never offered in the generic category system,
     * unlike Border hop or Tap the map, which both filter the app's real countries.
     */
    override fun offers(category: QuizCategory): Boolean = false

    companion object {
        /** Never change: persisted in Room, DataStore, routes and this mode's own screens. */
        const val MODE_ID = "us_ca_regions"

        /** Variant ids ([com.geoquiz.app.domain.model.CompletedQuiz.variantId], `ModeRoutes`). */
        const val VARIANT_TAP = "tap"
        const val VARIANT_NAME_ALL = "name_all"
        const val VARIANT_CHOICE = "choice"

        /** Tiers each variant supports (task 6.2 review: Multiple choice is this mode's Easy-only
         * variant; Tap and Name all are Normal/Hard only, D40's precedent). */
        val TAP_DIFFICULTIES = listOf(Difficulty.NORMAL, Difficulty.HARD)
        val NAME_ALL_DIFFICULTIES = listOf(Difficulty.NORMAL, Difficulty.HARD)
        val CHOICE_DIFFICULTIES = listOf(Difficulty.EASY)

        fun difficultiesFor(variant: String): List<Difficulty> = when (variant) {
            VARIANT_NAME_ALL -> NAME_ALL_DIFFICULTIES
            VARIANT_CHOICE -> CHOICE_DIFFICULTIES
            else -> TAP_DIFFICULTIES
        }

        val SPEC = GameModeSpec(
            id = MODE_ID,
            labels = ModeLabels(
                name = R.string.mode_us_ca_regions_name,
                inputLabel = R.string.mode_us_ca_regions_input_label,
                itemsNoun = R.string.mode_us_ca_regions_items,
                pausedProgress = R.string.mode_us_ca_regions_paused_progress,
                giveUpMessage = R.string.mode_us_ca_regions_give_up,
                a11yProgress = R.plurals.a11y_progress_us_ca_regions
            ),
            icon = ModeIcon.MAP_PIN,
            answerType = AnswerType.MAP_TAP,
            supportedDifficulties = Difficulty.entries.toSet(),
            // No hints (task 6.2 review): none of the existing hint types fit cleanly - there is
            // no finer region/subregion to flash or show within a 50- or 13-member set
            // (SHOW_REGION/FLASH_REGION), no border graph between states (SHOW_NEIGHBOURS,
            // Border hop only), and Name all's free typing order has no single "current target"
            // for FIRST_LETTER to reveal, unlike Silhouettes' one-at-a-time silhouette.
            hintTypes = emptySet(),
            featureFlag = FeatureFlag.US_CA_REGIONS,
            sortOrder = 13,
            // Code review (task 6.2): this mode's codes are admin1 ISO 3166-2 ids, not cca3, so
            // AnswerReviewViewModel's CountryRepository lookup finds nothing and the screen comes
            // back blank. Hide the button rather than build a review path for synthetic codes.
            supportsAnswerReview = false
        )
    }
}
