package com.geoquiz.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.usecase.MasteryStars
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
 * Labels are short and may wrap to two lines at large font sizes; the selected segment shows
 * a tick as well as its fill, so the choice never relies on colour alone. The explanation is a
 * polite live region, so changing the tier is announced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifficultySelector(
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Difficulty> = Difficulty.entries
) {
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, difficulty ->
                SegmentedButton(
                    selected = difficulty == selected,
                    onClick = { onSelect(difficulty) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = {
                        Text(
                            text = stringResource(difficulty.labelRes),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
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
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
