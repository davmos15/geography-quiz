package com.geoquiz.app.ui.quiz.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.theme.geoColors

/**
 * Typed tiers (3.3): the latest correct answers, newest first, as small chips above the answer
 * field (so above the keyboard while it is open). Capitals show the capital, plus the country
 * when the "Country hint" setting is on, as the quiz list does. The chips wrap onto more lines
 * at large font sizes, and TalkBack reads them as one item: "Recent answers: France, Germany".
 * Shows nothing when [recent] is empty.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentAnswers(
    recent: List<Country>,
    quizMode: QuizMode,
    showCountryHint: Boolean,
    modifier: Modifier = Modifier
) {
    if (recent.isEmpty()) return
    val labels = recent.map { recentAnswerLabel(it, quizMode, showCountryHint) }
    val description = stringResource(
        R.string.recent_answers_a11y,
        labels.joinToString(stringResource(R.string.recent_answers_separator))
    )
    val geo = MaterialTheme.geoColors
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEach { label ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = geo.correctContainer,
                contentColor = geo.onCorrectContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = geo.correct,
                        modifier = Modifier.size(inlineIconSize(16.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun recentAnswerLabel(country: Country, quizMode: QuizMode, showCountryHint: Boolean): String =
    when {
        quizMode != QuizMode.CAPITALS -> country.name
        showCountryHint -> stringResource(R.string.recent_answer_capital_with_country, country.capital, country.name)
        else -> country.capital
    }
