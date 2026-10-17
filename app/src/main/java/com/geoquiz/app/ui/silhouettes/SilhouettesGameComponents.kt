package com.geoquiz.app.ui.silhouettes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.ui.map.CountryOutline
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.theme.geoColors

/**
 * The outline on screen: a spinner while it decodes, a fallback message if it failed to load or
 * does not exist (task 5.1 review nit: never an endless spinner), or the outline itself. Never
 * leaks the answer.
 */
@Composable
fun SilhouetteOutlineBox(
    outline: OutlineModel?,
    isLoading: Boolean,
    isUnavailable: Boolean,
    showNeighbours: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth(0.85f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        when {
            outline != null -> CountryOutline(
                outline = outline,
                showNeighbours = showNeighbours,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            isUnavailable -> Text(
                text = stringResource(R.string.silhouettes_outline_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            isLoading -> CircularProgressIndicator()
            else -> Unit
        }
    }
}

/** 3 heart icons (filled = a life left); never colour alone, TalkBack gets "X lives left". */
@Composable
fun LivesRow(lives: Int, maxLives: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.silhouettes_lives_left, lives, lives)
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        repeat(maxLives) { index ->
            Icon(
                imageVector = if (index < lives) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                // Material's own error token (task 5.1 review nit): lives running out is a danger
                // state, not the app's "wrong answer" feedback colour. Filled vs outline shape is
                // still the real (non-colour) cue.
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(inlineIconSize(20.dp))
            )
        }
    }
}

/** "Guessed so far" chips (task 5.1): wrong real countries named for the current silhouette. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuessedSoFarChips(names: List<String>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val geo = MaterialTheme.geoColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.silhouettes_guessed_so_far_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { name ->
                Surface(shape = MaterialTheme.shapes.small, color = geo.wrongContainer, contentColor = geo.onWrongContainer) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = geo.wrong,
                            modifier = Modifier.size(inlineIconSize(16.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * One feedback line (or two, for [SilhouetteFeedback.LivesExhausted]) under the silhouette: icon
 * + text, never colour alone, a polite live region. [feedbackToken] (task 5.1 review nit) is
 * folded into the announced text as an invisible suffix, so TalkBack re-announces even when the
 * same feedback (e.g. the same wrong country) repeats and the visible words do not change.
 */
@Composable
fun SilhouetteFeedbackBanner(feedback: SilhouetteFeedback, feedbackToken: Int, modifier: Modifier = Modifier) {
    val geo = MaterialTheme.geoColors
    val announceSuffix = ZERO_WIDTH_SPACE.repeat(feedbackToken % 2)
    Box(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (feedback) {
            is SilhouetteFeedback.Correct -> FeedbackRow(
                Icons.Filled.Check, stringResource(R.string.silhouettes_feedback_correct, feedback.name) + announceSuffix, geo.correct
            )
            is SilhouetteFeedback.WrongCountry -> FeedbackRow(
                Icons.Filled.Close,
                pluralStringResource(R.plurals.silhouettes_feedback_wrong_country, feedback.livesLeft, feedback.name, feedback.livesLeft) + announceSuffix,
                geo.wrong
            )
            SilhouetteFeedback.NearMiss -> FeedbackRow(
                Icons.Outlined.Spellcheck, stringResource(R.string.silhouettes_feedback_near_miss) + announceSuffix, geo.nearMiss
            )
            SilhouetteFeedback.NotRecognised -> FeedbackRow(
                Icons.Outlined.Info,
                stringResource(R.string.silhouettes_feedback_not_recognised) + announceSuffix,
                MaterialTheme.colorScheme.onSurfaceVariant
            )
            is SilhouetteFeedback.Revealed -> FeedbackRow(
                Icons.Outlined.Info, stringResource(R.string.silhouettes_feedback_revealed, feedback.name) + announceSuffix, MaterialTheme.colorScheme.onSurfaceVariant
            )
            is SilhouetteFeedback.LivesExhausted -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FeedbackRow(Icons.Filled.Close, stringResource(R.string.silhouettes_feedback_wrong_country_last, feedback.wrongName), geo.wrong)
                FeedbackRow(Icons.Outlined.Info, stringResource(R.string.silhouettes_feedback_revealed, feedback.revealedName) + announceSuffix, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SilhouetteFeedback.None -> Unit
        }
    }
}

/** An invisible character that changes a string's identity without changing what is read aloud. */
private const val ZERO_WIDTH_SPACE = "​"

@Composable
private fun FeedbackRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(inlineIconSize(18.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Hint buttons (Show region, First letter, Show neighbours): disabled only while [enabled] is
 * false (an advance in progress) and not yet used this silhouette. Once used, a hint shows its
 * revealed value as plain readable text rather than a disabled button (task 5.1 review, S6):
 * [revealedSubregion]/[revealedFirstLetter] for the first two; [neighbourNames] (D38) for Show
 * neighbours, sorted and spoken for everyone, with "No land neighbours" if the country has none.
 *
 * [isOutlineLoading] additionally disables Show neighbours on its own (task 9.2 review fix):
 * until the current silhouette's outline has loaded, its neighbour list isn't known yet, so the
 * button would otherwise charge the hint and falsely reveal "No land neighbours" on a cold
 * silhouette. The other two hints are unaffected, since they don't depend on the outline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SilhouetteHintsRow(
    available: Set<HintType>,
    used: Set<HintType>,
    revealedSubregion: String?,
    revealedFirstLetter: Char?,
    neighbourNames: List<String>,
    enabled: Boolean,
    onHint: (HintType) -> Unit,
    modifier: Modifier = Modifier,
    isOutlineLoading: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.silhouettes_hints_cost_points),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (HintType.SHOW_REGION in available) {
                if (HintType.SHOW_REGION in used) {
                    RevealedHintText(stringResource(R.string.silhouettes_hint_show_region_revealed, revealedSubregion.orEmpty()))
                } else {
                    HintButton(
                        label = stringResource(R.string.silhouettes_hint_show_region, HintCosts.cost(HintType.SHOW_REGION)),
                        enabled = enabled,
                        onClick = { onHint(HintType.SHOW_REGION) }
                    )
                }
            }
            if (HintType.FIRST_LETTER in available) {
                if (HintType.FIRST_LETTER in used) {
                    RevealedHintText(stringResource(R.string.silhouettes_hint_first_letter_revealed, revealedFirstLetter?.toString().orEmpty()))
                } else {
                    HintButton(
                        label = stringResource(R.string.silhouettes_hint_first_letter, HintCosts.cost(HintType.FIRST_LETTER)),
                        enabled = enabled,
                        onClick = { onHint(HintType.FIRST_LETTER) }
                    )
                }
            }
            if (HintType.SHOW_NEIGHBOURS in available) {
                if (HintType.SHOW_NEIGHBOURS in used) {
                    val neighboursText = if (neighbourNames.isEmpty()) {
                        stringResource(R.string.silhouettes_hint_no_neighbours)
                    } else {
                        stringResource(R.string.silhouettes_hint_show_neighbours_revealed, neighbourNames.joinToString(", "))
                    }
                    RevealedHintText(neighboursText)
                } else {
                    HintButton(
                        label = stringResource(R.string.silhouettes_hint_show_neighbours, HintCosts.cost(HintType.SHOW_NEIGHBOURS)),
                        enabled = enabled && !isOutlineLoading,
                        onClick = { onHint(HintType.SHOW_NEIGHBOURS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HintButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text = label)
    }
}

/** A used hint's revealed value: plain readable text, not a disabled button (task 5.1 review, S6). */
@Composable
private fun RevealedHintText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(inlineIconSize(18.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}
