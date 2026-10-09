package com.geoquiz.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.usecase.MasteryStars
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.theme.geoColors

/** Short name of the tier: "Easy", "Normal", "Hard". */
@get:StringRes
val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.NORMAL -> R.string.difficulty_normal
        Difficulty.HARD -> R.string.difficulty_hard
    }

/** One line explaining the tier's rules. */
@get:StringRes
val Difficulty.summaryRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy_summary
        Difficulty.NORMAL -> R.string.difficulty_normal_summary
        Difficulty.HARD -> R.string.difficulty_hard_summary
    }

/**
 * Single-choice segmented buttons (Easy / Normal / Hard) with a supporting line below that
 * explains the selected tier. Used on the category list (per quiz, remembered as the default)
 * and in Settings ("Default difficulty").
 *
 * The selected segment shows a tick as well as its fill, so the choice never relies on colour
 * alone. When a label would not fit its segment on one line (large text or a narrow screen) the
 * tiers become a column of radio buttons instead, so no label is cut off (3.6). Both are read
 * by TalkBack as radio buttons with their selected state. The explanation is a polite live
 * region, so changing the tier is announced.
 *
 * The fit check uses BoxWithConstraints, which can't be measured intrinsically: don't put this
 * selector inside a layout that asks for intrinsic sizes (e.g. `Modifier.height(IntrinsicSize.Min)`
 * or an [AdaptiveButtonRow]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifficultySelector(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Difficulty> = Difficulty.entries
) {
    val labels = options.map { stringResource(it.labelRes) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val labelRoom = maxWidth / options.size.coerceAtLeast(1) - SEGMENT_LABEL_CHROME
            val labelsFit = labels.all { label ->
                val width = measurer.measure(label, labelStyle, maxLines = 1, softWrap = false).size.width
                with(density) { width.toDp() } <= labelRoom
            }
            if (labelsFit) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, difficulty ->
                        SegmentedButton(
                            selected = difficulty == selected,
                            onClick = { onSelect(difficulty) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            label = { Text(text = labels[index], textAlign = TextAlign.Center) }
                        )
                    }
                }
            } else {
                DifficultyRadioColumn(
                    options = options,
                    labels = labels,
                    selected = selected,
                    onSelect = onSelect
                )
            }
        }
        Text(
            text = stringResource(selected.summaryRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** The large-text form of [DifficultySelector]: one full-width radio row (at least 48 dp) per tier. */
@Composable
private fun DifficultyRadioColumn(
    options: List<Difficulty>,
    labels: List<String>,
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        options.forEachIndexed { index, difficulty ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = difficulty == selected,
                        onClick = { onSelect(difficulty) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The row handles the tap and the semantics.
                RadioButton(selected = difficulty == selected, onClick = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Small read-only label naming the tier of a running quiz, e.g. in the quiz header. */
@Composable
fun DifficultyLabel(
    difficulty: Difficulty,
    modifier: Modifier = Modifier
) {
    val name = stringResource(difficulty.labelRes)
    val description = stringResource(R.string.a11y_quiz_difficulty, name)
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * Mastery stars for a category: [stars] filled stars then outlined ones, up to
 * [MasteryStars.MAX]. Read as one item, e.g. "2 of 3 stars".
 */
@Composable
fun MasteryStarsRow(
    stars: Int,
    modifier: Modifier = Modifier
) {
    val max = MasteryStars.MAX
    val earned = stars.coerceIn(0, max)
    val description = pluralStringResource(R.plurals.a11y_mastery_stars, max, earned, max)
    Row(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        repeat(max) { index ->
            Icon(
                imageVector = if (index < earned) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.geoColors.star,
                modifier = Modifier.size(inlineIconSize(18.dp))
            )
        }
    }
}
