package com.geoquiz.app.domain.mode

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizResult
import com.geoquiz.app.domain.model.QuizState

/** How the player gives an answer. */
enum class AnswerType {
    /** Type the name into a text field (every classic mode at Normal and Hard). */
    TYPED_NAME,

    /** Pick one of several options (Easy tier, task 3.2). */
    MULTIPLE_CHOICE,

    /** Tap a place on a map (Phase 5). */
    MAP_TAP
}

/** Hints a mode can offer. Not implemented until Phase 8. */
enum class HintType {
    FIRST_LETTER,
    SHOW_REGION,
    SHOW_NEIGHBOURS,
    FLASH_REGION
}

/**
 * The mode's icon as a domain key; the UI maps it to an image (`ui/mode/ModeIcons.kt`), so the
 * domain layer stays free of Compose.
 */
enum class ModeIcon {
    GLOBE,
    LANDMARK,
    FLAG
}

/** User-facing text for a mode, as resource ids. */
data class ModeLabels(
    /** "Countries": tab and results label. */
    @StringRes val name: Int,
    /** Answer field label, e.g. "Enter a country name". */
    @StringRes val inputLabel: Int,
    /** Plural noun for the items, e.g. "countries" (share text). */
    @StringRes val itemsNoun: Int,
    /** "%1$d / %2$d countries named" on the pause overlay. */
    @StringRes val pausedProgress: Int,
    /** "You've named %1$d of %2$d countries. Are you sure?" in the give-up dialog. */
    @StringRes val giveUpMessage: Int,
    /** Spoken progress, quantity = total: "12 of 197 countries named". */
    @PluralsRes val a11yProgress: Int
)

/**
 * Static description of a game mode: everything the UI needs to show it, with no behaviour.
 *
 * @param id persisted in Room, DataStore, challenge links, routes and leaderboard mapping.
 *   Never change it. The classic modes use the [com.geoquiz.app.domain.model.QuizMode] ids.
 * @param answerType how the player answers at Normal and Hard; Easy is always multiple choice.
 * @param featureFlag the flag that hides an unreleased mode, or null for released modes.
 * @param sortOrder position in mode lists, lowest first.
 */
data class GameModeSpec(
    val id: String,
    val labels: ModeLabels,
    val icon: ModeIcon,
    val answerType: AnswerType,
    val supportedDifficulties: Set<Difficulty>,
    val hintTypes: Set<HintType>,
    val featureFlag: FeatureFlag?,
    val sortOrder: Int
)

/** Builds the set of items a quiz asks about. Only called for categories offered in the mode. */
fun interface QuestionGenerator {
    suspend fun items(category: QuizCategory): List<Country>
}

/** Judges one answer against the quiz state. */
fun interface AnswerValidator {
    /** @param allowFuzzy accept answers one typo away (off in hard mode). */
    suspend fun validate(input: String, state: QuizState, allowFuzzy: Boolean): AnswerResult
}

/** Scores a finished quiz. [QuizState.timeElapsedSeconds] is already set. */
fun interface ScoringRule {
    fun score(state: QuizState): QuizResult
}

/**
 * A playable mode: its [spec] plus the behaviour behind it.
 *
 * To add a mode: write its [QuestionGenerator] (and validator, if the answers are not country or
 * capital names) and its [ChoiceQuestionGenerator] for Easy, implement this interface with an
 * `@Inject` constructor next to its spec, and add one `@Binds @IntoSet` line to
 * `di/GameModeModule.kt`. Nothing else needs a `when (mode)`.
 */
interface GameMode {
    val spec: GameModeSpec
    val generator: QuestionGenerator
    val validator: AnswerValidator
    val scoring: ScoringRule

    /** Builds the multiple-choice questions for the Easy tier ([Difficulty.EASY]). */
    val choiceGenerator: ChoiceQuestionGenerator

    val id: String get() = spec.id
}
