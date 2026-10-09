package com.geoquiz.app.ui.results

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.AdaptiveButtonRow
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.share.ShareUtils
import com.geoquiz.app.ui.theme.geoColors
import java.util.Locale

@Composable
fun ResultsScreen(
    /** [difficultyId]: the finished quiz's tier, so "Play again" replays at the same tier. */
    onPlayAgain: (quizMode: String, categoryType: String, categoryValue: String, difficultyId: String) -> Unit,
    onGoHome: (quizMode: String) -> Unit,
    onViewAnswers: (resultId: String) -> Unit,
    /**
     * "Practise the ones you missed" (3.5c): a quiz of the same mode and tier with exactly the
     * missed items, as a [com.geoquiz.app.domain.model.QuizCategory.Practice] route.
     */
    onPractiseMissed: (quizMode: String, categoryType: String, categoryValue: String, difficultyId: String) -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
        ResultsUiState.Loading -> ResultLoading()
        ResultsUiState.Missing -> ResultMissing(onGoHome = { onGoHome(QuizMode.COUNTRIES.id) })
        is ResultsUiState.Loaded -> {
            val result = state.result
            val practice = result.practiceCategoryOrNull()
            InterstitialAfterFirstFrame(viewModel)
            ResultsContent(
                score = result.score,
                correctAnswers = result.correct,
                totalCountries = result.total,
                timeElapsedSeconds = result.timeSeconds,
                perfectBonus = result.perfectBonus,
                // A practice quiz shows its title from strings.xml (D21: it is not shared either).
                categoryName = if (result.isPractice) {
                    stringResource(R.string.practice_quiz_title)
                } else {
                    result.categoryName
                },
                categoryType = result.categoryType,
                categoryValue = result.categoryValue,
                quizMode = result.quizModeId,
                incorrectGuesses = result.incorrectGuesses,
                newAchievements = result.newAchievements,
                isPractice = result.isPractice,
                missedCount = practice?.codes?.size ?: 0,
                onPractiseMissed = {
                    if (practice != null) {
                        onPractiseMissed(
                            result.quizModeId, practice.typeKey, practice.valueKey, result.difficulty.id
                        )
                    }
                },
                onPlayAgain = {
                    onPlayAgain(result.quizModeId, result.categoryType, result.categoryValue, result.difficulty.id)
                },
                onGoHome = { onGoHome(result.quizModeId) },
                onViewAnswers = { onViewAnswers(result.id) },
                viewModel = viewModel
            )
        }
    }
}

/**
 * Shows a due interstitial only after the Results content has been composed and drawn: the
 * effect starts once this composition is applied, and the next frame callback comes after that
 * frame was drawn.
 */
@Composable
private fun InterstitialAfterFirstFrame(viewModel: ResultsViewModel) {
    val due by viewModel.interstitialDue.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    if (due && activity != null) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            viewModel.showDueInterstitial(activity)
        }
    }
}

@Composable
private fun ResultsContent(
    score: Double,
    correctAnswers: Int,
    totalCountries: Int,
    timeElapsedSeconds: Int,
    perfectBonus: Boolean,
    categoryName: String,
    categoryType: String,
    categoryValue: String,
    quizMode: String,
    incorrectGuesses: Int,
    newAchievements: List<Achievement>,
    isPractice: Boolean,
    missedCount: Int,
    onPractiseMissed: () -> Unit,
    onPlayAgain: () -> Unit,
    onGoHome: () -> Unit,
    onViewAnswers: () -> Unit,
    viewModel: ResultsViewModel
) {
    val context = LocalContext.current
    val challengeResult by viewModel.challengeResult.collectAsStateWithLifecycle()

    val percentage = if (totalCountries > 0) {
        (correctAnswers.toDouble() / totalCountries * 100)
    } else 0.0

    val minutes = timeElapsedSeconds / 60
    val seconds = timeElapsedSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Quiz Complete!",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )

            if (categoryName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .semantics(mergeDescendants = true) { },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", score),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Score",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    val resultLabel = stringResource(QuizMode.fromId(quizMode).spec.labels.name)
                    val res = a11yResources()
                    ResultRow(
                        resultLabel,
                        "$correctAnswers / $totalCountries",
                        valueDescription = A11yText.progress(
                            res, QuizMode.fromId(quizMode), correctAnswers, totalCountries
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultRow("Percentage", String.format(Locale.US, "%.1f%%", percentage))
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultRow("Time", timeFormatted, valueDescription = A11yText.duration(res, timeElapsedSeconds))
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultRow("Incorrect Guesses", incorrectGuesses.toString())
                    if (perfectBonus) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutcomeLine(
                            text = "Perfect! +20% bonus!",
                            icon = Icons.Default.Check,
                            color = MaterialTheme.geoColors.correct,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (newAchievements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                NewAchievementsCard(newAchievements)
            }

            // Challenge result comparison card
            val challenge = challengeResult
            if (challenge != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ChallengeResultCard(
                    challengerName = challenge.challengerName,
                    challengerScore = challenge.challengerScore,
                    challengerTotal = challenge.challengerTotal,
                    challengerTime = challenge.challengerTime,
                    myScore = correctAnswers,
                    myTotal = totalCountries,
                    myTime = timeElapsedSeconds
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // D21: a practice quiz is not recorded, so it is not shared or sent as a challenge.
            if (isPractice) {
                Text(
                    text = stringResource(R.string.results_practice_not_recorded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            } else AdaptiveButtonRow(
                // Side by side when both labels fit on one line, otherwise stacked (3.6).
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                OutlinedButton(
                    onClick = {
                        ShareUtils.shareResults(
                            context = context,
                            categoryName = categoryName,
                            quizMode = quizMode,
                            score = correctAnswers,
                            total = totalCountries,
                            time = timeElapsedSeconds,
                            deepLink = viewModel.createChallengeShareUrl(
                                categoryType, categoryValue, quizMode,
                                score = correctAnswers, total = totalCountries, time = timeElapsedSeconds
                            )
                        )
                    },
                    // Material's padding for a button with an icon: a little more room for the label.
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Share", textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = {
                        ShareUtils.shareChallenge(
                            context = context,
                            categoryName = categoryName,
                            deepLink = viewModel.createChallengeShareUrl(
                                categoryType, categoryValue, quizMode,
                                score = null, total = null, time = null
                            )
                        )
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Challenge", textAlign = TextAlign.Center)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // View Answers button
            Button(
                onClick = onViewAnswers,
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("View Answers", textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPlayAgain,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Play Again", textAlign = TextAlign.Center)
            }

            if (missedCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                PractiseMissedButton(missedCount = missedCount, onClick = onPractiseMissed)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Home", textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * "Practise the ones you missed (3)": starts a quiz of exactly the missed items. No fixed height,
 * so the label wraps at large font sizes; Material buttons keep the 48 dp touch target.
 */
@Composable
private fun PractiseMissedButton(missedCount: Int, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.8f)
    ) {
        Icon(
            imageVector = Icons.Default.Replay,
            // Decorative: the label says it.
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = pluralStringResource(R.plurals.results_practise_missed, missedCount, missedCount),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The achievements this quiz unlocked. A polite live region, so TalkBack reads it once when it
 * appears; each achievement is one TalkBack item ("First Steps, Complete any quiz").
 */
@Composable
internal fun NewAchievementsCard(achievements: List<Achievement>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(
                text = pluralStringResource(R.plurals.results_new_achievements_heading, achievements.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.semantics { heading() }
            )
            achievements.forEach { achievement ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        // Decorative: the title says it.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(inlineIconSize(32.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = achievement.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeResultCard(
    challengerName: String,
    challengerScore: Int?,
    challengerTotal: Int?,
    challengerTime: Int?,
    myScore: Int,
    myTotal: Int,
    myTime: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Challenge Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (challengerScore != null && challengerTotal != null) {
                val myPct = if (myTotal > 0) myScore.toDouble() / myTotal * 100 else 0.0
                val theirPct = if (challengerTotal > 0) challengerScore.toDouble() / challengerTotal * 100 else 0.0

                // Comparison header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = challengerName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                        // Wraps rather than cutting a long name short.
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Score row
                ComparisonRow("$myScore / $myTotal", "Score", "$challengerScore / $challengerTotal")

                Spacer(modifier = Modifier.height(4.dp))

                // Percentage row
                ComparisonRow(
                    String.format(Locale.US, "%.1f%%", myPct),
                    "Percentage",
                    String.format(Locale.US, "%.1f%%", theirPct)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Time row
                val myMin = myTime / 60
                val mySec = myTime % 60
                val theirMin = (challengerTime ?: 0) / 60
                val theirSec = (challengerTime ?: 0) % 60
                ComparisonRow(
                    String.format("%02d:%02d", myMin, mySec),
                    "Time",
                    String.format("%02d:%02d", theirMin, theirSec)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Win/loss/tie
                val resultText: String
                val resultColor: androidx.compose.ui.graphics.Color
                val resultIcon: ImageVector?
                when {
                    myPct > theirPct -> {
                        resultText = "You won!"
                        resultColor = MaterialTheme.geoColors.correct
                        resultIcon = Icons.Default.Check
                    }
                    myPct < theirPct -> {
                        resultText = "You lost - challenge them back!"
                        resultColor = MaterialTheme.geoColors.wrong
                        resultIcon = Icons.Default.Close
                    }
                    else -> {
                        resultText = "It's a tie!"
                        resultColor = MaterialTheme.colorScheme.onTertiaryContainer
                        resultIcon = null
                    }
                }

                OutcomeLine(
                    text = resultText,
                    icon = resultIcon,
                    color = resultColor,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                // Challenger didn't share their score
                Text(
                    text = "Challenge from $challengerName completed!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Share your result so they can compare.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(leftValue: String, label: String, rightValue: String) {
    // One item for TalkBack: "<you>, <label>, <them>".
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leftValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = rightValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueDescription: String? = null) {
    // One item for TalkBack: "Time, 3 minutes 12 seconds".
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // The label wraps at large text sizes; the value keeps its own space at the end.
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = 12.dp)
                .then(
                    if (valueDescription != null) {
                        Modifier.clearAndSetSemantics { contentDescription = valueDescription }
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/** Centred bold outcome text with an optional leading icon (decorative: the text says it). */
@Composable
private fun OutcomeLine(
    text: String,
    icon: ImageVector?,
    color: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(inlineIconSize(20.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = style,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
