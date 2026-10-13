package com.geoquiz.app.domain.mode.tapmap

import com.geoquiz.app.R
import com.geoquiz.app.domain.map.RegionPreset
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
 * Tap the map (task 5.2 builds the real screen; this registers the mode so shared plumbing -
 * results, history, routes - works end to end). World or one continent; a country is named and
 * the player taps it on the map ([AnswerType.MAP_TAP]). Task 5.2 replaces [generator],
 * [validator] and [choiceGenerator] with map-tap behaviour; for now it reuses Countries' typed
 * behaviour so the [GameMode] contract is met and a quiz can complete end to end.
 *
 * Two variants ("find" the named country, or "name all" by typing): the variant is not part of
 * the mode's identity (it does not change [spec]), it is carried on the route and the completed
 * quiz ([com.geoquiz.app.domain.model.CompletedQuiz.variantId]) so "Play again" and "Practise the
 * ones you missed" replay the same one (`ui/navigation/ModeRoutes.kt`).
 */
class TapMapGameMode @Inject constructor(
    override val generator: CountriesQuestionGenerator,
    validateAnswer: ValidateAnswerUseCase,
    calculateScore: CalculateScoreUseCase
) : GameMode {
    override val spec: GameModeSpec = SPEC
    override val validator = AnswerValidator { input, state, allowFuzzy -> validateAnswer(input, state, allowFuzzy) }
    override val scoring = ScoringRule { state -> calculateScore(state) }
    override val choiceGenerator: ChoiceQuestionGenerator = CountriesChoiceGenerator

    /**
     * Tap the map offers the world or one continent ([QuizCategory.MapRegion], any of the seven
     * [RegionPreset]s) and practice sets. No letter, flag or word categories: there is nothing to
     * tap for those. [QuizCategory.ByRegion]/[QuizCategory.BySubregion] cannot express every
     * preset (North America spans three subregions), which is why [QuizCategory.MapRegion]
     * exists (task 5.0 review).
     */
    override fun offers(category: QuizCategory): Boolean = when (category) {
        is QuizCategory.MapRegion -> RegionPreset.fromId(category.presetId) != null
        is QuizCategory.Practice -> true
        else -> false
    }

    companion object {
        /** Never change: persisted in Room, DataStore, routes and this mode's own screens. */
        const val MODE_ID = "tap_map"

        /** Variant ids ([CompletedQuiz.variantId], `ModeRoutes`, `Screen.TapMap`). */
        const val VARIANT_FIND = "find"
        const val VARIANT_NAME_ALL = "name_all"

        val SPEC = GameModeSpec(
            id = MODE_ID,
            labels = ModeLabels(
                name = R.string.mode_tap_map_name,
                inputLabel = R.string.mode_tap_map_input_label,
                itemsNoun = R.string.mode_tap_map_items,
                pausedProgress = R.string.mode_tap_map_paused_progress,
                giveUpMessage = R.string.mode_tap_map_give_up,
                a11yProgress = R.plurals.a11y_progress_tap_map
            ),
            icon = ModeIcon.MAP_PIN,
            answerType = AnswerType.MAP_TAP,
            supportedDifficulties = Difficulty.entries.toSet(),
            hintTypes = setOf(HintType.FLASH_REGION),
            featureFlag = FeatureFlag.TAP_THE_MAP,
            sortOrder = 11
        )
    }
}
