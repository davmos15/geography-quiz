package com.geoquiz.app.ui.quiz.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.FlagImage
import com.geoquiz.app.ui.components.MainWithCappedTrailing
import com.geoquiz.app.ui.components.a11yResources

@Composable
fun CountryList(
    countries: List<Country>,
    answeredCodes: Set<String>,
    quizMode: QuizMode = QuizMode.COUNTRIES,
    showFlags: Boolean = false,
    showCountryHint: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sorted = if (quizMode == QuizMode.CAPITALS) {
        countries.sortedBy { it.capital }
    } else {
        countries.sortedBy { it.name }
    }

    LazyColumn(modifier = modifier) {
        itemsIndexed(sorted, key = { _, country -> country.code }) { index, country ->
            val isAnswered = country.code in answeredCodes
            // TalkBack reads each row as one item, e.g. "France, answered" or
            // "Row 12, not yet answered". The visible "???" placeholder is never read out.
            val rowDescription = A11yText.quizRow(
                res = a11yResources(),
                mode = quizMode,
                country = country,
                rowNumber = index + 1,
                isAnswered = isAnswered,
                showCountryHint = showCountryHint
            )
            val rowModifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .clearAndSetSemantics { contentDescription = rowDescription }
            // Never reveal an unanswered country through the flag's description (the row's
            // merged description replaces it for TalkBack, but keep it safe on its own too).
            val flagDescription = if (isAnswered) {
                stringResource(R.string.flag_content_description, country.name)
            } else {
                stringResource(R.string.flag_content_description_hidden)
            }

            when (quizMode) {
                QuizMode.COUNTRIES -> {
                    Row(
                        modifier = rowModifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showFlags) {
                            FlagImage(
                                countryCode = country.code,
                                contentDescription = flagDescription
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = if (isAnswered) country.name else "???",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isAnswered) FontWeight.Medium else FontWeight.Normal,
                            color = if (isAnswered) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }
                }

                QuizMode.CAPITALS -> {
                    Row(
                        modifier = rowModifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showFlags) {
                            FlagImage(
                                countryCode = country.code,
                                contentDescription = flagDescription
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        if (showCountryHint) {
                            // The capital takes what it needs, up to half the row; the
                            // country gets the rest. Either wraps only when it must (3.6).
                            MainWithCappedTrailing(
                                modifier = Modifier.weight(1f),
                                main = {
                                    Text(
                                        text = country.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailing = {
                                    Text(
                                        text = if (isAnswered) country.capital else "???",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isAnswered) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isAnswered) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        },
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            )
                        } else {
                            Text(
                                text = if (isAnswered) country.capital else "???",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isAnswered) FontWeight.Medium else FontWeight.Normal,
                                color = if (isAnswered) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }
                }

                QuizMode.FLAGS -> {
                    Row(
                        modifier = rowModifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showFlags) {
                            FlagImage(
                                countryCode = country.code,
                                contentDescription = flagDescription
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = if (isAnswered) country.name else "???",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isAnswered) FontWeight.Medium else FontWeight.Normal,
                            color = if (isAnswered) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}
