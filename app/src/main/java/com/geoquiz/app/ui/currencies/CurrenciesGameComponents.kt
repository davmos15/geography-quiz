package com.geoquiz.app.ui.currencies

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.currencies.CurrencyEntry
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.components.AdaptiveTrailingRow
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.FlagImage
import com.geoquiz.app.ui.quiz.components.FeedbackLine
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.theme.geoColors

/** Header: "3 of 143", Hard's strike counter, difficulty chip and the timer. */
@Composable
fun CurrenciesHeader(
    current: Int,
    total: Int,
    difficulty: Difficulty,
    incorrectGuesses: Int,
    timerVisible: Boolean,
    timerSeconds: Int,
    modifier: Modifier = Modifier
) {
    val progressText = stringResource(R.string.currencies_progress, (current + 1).coerceAtMost(total.coerceAtLeast(1)), total)
    // Task 9.2 review fix: AdaptiveTrailingRow wraps the strikes/timer/tier group onto its own
    // line at 200% font scale (D29), the same component Tap the map/Rivers mountains and
    // lakes/US-CA's own status rows use, instead of squeezing the progress text to zero width.
    AdaptiveTrailingRow(
        modifier = modifier,
        main = {
            Text(text = progressText, style = MaterialTheme.typography.titleMedium)
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (difficulty == Difficulty.HARD) {
                    val limit = Difficulty.HARD.strikeLimit ?: HARD_STRIKE_LIMIT_FALLBACK
                    // The VM never lets this exceed the limit, but coerce defensively too (same
                    // reasoning as `TapMapGameScreen.StatusRow`).
                    val shown = incorrectGuesses.coerceAtMost(limit)
                    val description = pluralStringResource(R.plurals.a11y_hard_mode_strikes, limit, shown, limit)
                    Text(
                        text = stringResource(R.string.mode_tap_map_strikes_short, shown, limit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.geoColors.wrong,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
                    )
                }
                if (timerVisible) {
                    TimerDisplay(elapsedSeconds = timerSeconds, timerSeconds = null)
                }
                DifficultyLabel(difficulty = difficulty)
            }
        }
    )
}

private const val HARD_STRIKE_LIMIT_FALLBACK = 3

/**
 * The currency's clue: a big currency symbol, nothing else (task P10-B, Dav 2026-10-20). Shown for
 * both typed and choice items. Never the currency's name or ISO code before the answer is
 * resolved - both used to be shown here unconditionally, which gave the country away (e.g. "Afghan
 * afghani (؋)" names Afghanistan outright); the full name is only safe to show once an answer
 * locks the item, in [CurrencyFeedbackBanner]'s reveal/fact text.
 *
 * The heading text depends on [CurrencyEntry.isChoiceOnly] (task 7.1 review's D50, extended by
 * task P10-B to ambiguous-symbol single-owner currencies too): a choice-only currency's question
 * is genuinely "which of these" (several valid-looking countries to weigh up), so it gets
 * [R.string.currencies_choice_prompt_symbol] with the symbol filled in - the same string
 * `MultipleChoicePanel`'s `CurrencyOf` branch uses - rather than the generic "which country" text.
 *
 * [R.string.currencies_symbol_description]'s "Currency symbol: <symbol>" content description
 * (task P10-B) replaces TalkBack reading the raw glyph (which reads poorly for marks like "$" or
 * "₨"), as [clearAndSetSemantics] on the big [Text] below.
 */
@Composable
fun CurrencyClueCard(entry: CurrencyEntry, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val prompt = if (entry.isChoiceOnly) {
                stringResource(R.string.currencies_choice_prompt_symbol, entry.symbol)
            } else {
                stringResource(R.string.currencies_prompt_which_country)
            }
            Text(
                text = prompt,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            val symbolDescription = stringResource(R.string.currencies_symbol_description, entry.symbol)
            Text(
                text = entry.symbol,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { contentDescription = symbolDescription }
            )
        }
    }
}

/** The answer field + Submit for a typed item (Normal/Hard, non-shared currencies). */
@Composable
fun CurrencyInputRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            label = { Text(stringResource(R.string.mode_currencies_input_label)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onSubmit, enabled = enabled && input.isNotBlank()) {
            Text(stringResource(R.string.quiz_submit))
        }
    }
}

/** How an option looks: before a pick, or while a pick's result is shown. */
private enum class CurrencyOptionLook { NEUTRAL, CORRECT, WRONG, DIMMED }

/**
 * The options for a choice item (Easy, or any shared currency), with a flag next to every
 * option's name (task 7.1 spec): the one piece `MultipleChoicePanel` cannot show, since its
 * options are plain country/capital/flag-target names with no per-option flag of their own, so
 * this mode builds its own options list rather than reusing that composable, while matching its
 * chrome (same colours, tick/cross icons and polite live region).
 */
@Composable
fun CurrencyChoiceOptions(
    question: ChoiceQuestion,
    feedback: ChoiceFeedback?,
    enabled: Boolean,
    onSelect: (code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.options.forEach { option ->
            val look = when {
                feedback == null -> CurrencyOptionLook.NEUTRAL
                option.code == question.targetCode -> CurrencyOptionLook.CORRECT
                option.code == feedback.selectedCode -> CurrencyOptionLook.WRONG
                else -> CurrencyOptionLook.DIMMED
            }
            CurrencyOptionButton(
                option = option,
                look = look,
                enabled = enabled && feedback == null,
                onClick = { onSelect(option.code) }
            )
        }
        Box(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            when {
                feedback == null -> Unit
                feedback.isCorrect -> FeedbackLine(
                    icon = Icons.Filled.Check,
                    text = stringResource(R.string.choice_feedback_correct),
                    color = MaterialTheme.geoColors.correct,
                    style = MaterialTheme.typography.bodyMedium,
                    iconSize = 18.dp
                )
                else -> FeedbackLine(
                    icon = Icons.Filled.Close,
                    text = stringResource(R.string.choice_feedback_incorrect, question.correctOption.label),
                    color = MaterialTheme.geoColors.wrong,
                    style = MaterialTheme.typography.bodyMedium,
                    iconSize = 18.dp
                )
            }
        }
    }
}

@Composable
private fun CurrencyOptionButton(
    option: ChoiceOption,
    look: CurrencyOptionLook,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val geo = MaterialTheme.geoColors
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    val border: BorderStroke
    when (look) {
        CurrencyOptionLook.NEUTRAL -> {
            container = Color.Transparent
            content = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f)
            border = BorderStroke(1.dp, scheme.outline)
        }
        CurrencyOptionLook.CORRECT -> {
            container = geo.correctContainer
            content = geo.onCorrectContainer
            border = BorderStroke(2.dp, geo.correct)
        }
        CurrencyOptionLook.WRONG -> {
            container = geo.wrongContainer
            content = geo.onWrongContainer
            border = BorderStroke(2.dp, geo.wrong)
        }
        CurrencyOptionLook.DIMMED -> {
            container = Color.Transparent
            content = scheme.onSurface.copy(alpha = 0.38f)
            border = BorderStroke(1.dp, scheme.outlineVariant)
        }
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        border = border,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Decorative only: the option's name is already read by the button text right next
            // to it, so a separate "Flag" description would just be noise for TalkBack (task 7.1
            // review item 9).
            FlagImage(
                countryCode = option.code,
                contentDescription = null,
                height = 20.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            when (look) {
                CurrencyOptionLook.CORRECT -> CurrencyResultIcon(Icons.Filled.Check, stringResource(R.string.choice_option_correct), geo.correct)
                CurrencyOptionLook.WRONG -> CurrencyResultIcon(Icons.Filled.Close, stringResource(R.string.choice_option_wrong), geo.wrong)
                CurrencyOptionLook.NEUTRAL, CurrencyOptionLook.DIMMED -> Unit
            }
        }
    }
}

@Composable
private fun CurrencyResultIcon(icon: ImageVector, description: String, tint: Color) {
    Spacer(modifier = Modifier.width(8.dp))
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(inlineIconSize(24.dp))
    )
}

/** The last answer's feedback (right or wrong) and the currency's fact, if it has one. */
@Composable
fun CurrencyFeedbackBanner(feedback: CurrencyFeedback, modifier: Modifier = Modifier) {
    if (feedback == CurrencyFeedback.None) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val geo = MaterialTheme.geoColors
        val style = MaterialTheme.typography.bodyMedium
        val fact: String? = when (feedback) {
            is CurrencyFeedback.Correct -> {
                FeedbackLine(
                    icon = Icons.Filled.Check,
                    text = stringResource(R.string.currencies_feedback_correct, feedback.countryName),
                    color = geo.correct,
                    style = style,
                    iconSize = 18.dp
                )
                feedback.fact
            }
            is CurrencyFeedback.Revealed -> {
                FeedbackLine(
                    icon = Icons.Filled.Close,
                    text = stringResource(R.string.currencies_feedback_incorrect, feedback.countryName),
                    color = geo.wrong,
                    style = style,
                    iconSize = 18.dp
                )
                feedback.fact
            }
            CurrencyFeedback.NearMiss -> {
                FeedbackLine(
                    icon = Icons.Outlined.Spellcheck,
                    text = stringResource(R.string.currencies_feedback_near_miss),
                    color = geo.nearMiss,
                    style = style,
                    iconSize = 18.dp
                )
                null
            }
            CurrencyFeedback.Incorrect -> {
                FeedbackLine(
                    icon = Icons.Outlined.Info,
                    text = stringResource(R.string.currencies_feedback_not_recognised),
                    color = geo.wrong,
                    style = style,
                    iconSize = 18.dp
                )
                null
            }
            CurrencyFeedback.None -> null
        }
        if (fact != null) {
            Text(text = fact, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.currencies_fact_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shown once an answer locks the item, to move on at the player's own pace (no auto-advance). */
@Composable
fun CurrencyNextButton(onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onClick = onNext) {
            Text(stringResource(R.string.currencies_next_action))
        }
    }
}
