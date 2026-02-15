package com.geoquiz.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.ui.share.ShareUtils
import com.geoquiz.app.ui.theme.CorrectGreen
import java.util.UUID

@Composable
fun ResultsScreen(
    score: Double,
    correctAnswers: Int,
    totalCountries: Int,
    timeElapsedSeconds: Int,
    perfectBonus: Boolean,
    categoryName: String,
    categoryType: String,
    categoryValue: String,
    quizMode: String = "countries",
    incorrectGuesses: Int = 0,
    onPlayAgain: () -> Unit,
    onGoHome: () -> Unit,
    onViewAnswers: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playerName by viewModel.playerName.collectAsStateWithLifecycle(initialValue = "A friend")

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
                textAlign = TextAlign.Center
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
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format("%.1f", score),
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
                    val resultLabel = when (quizMode) {
                        "capitals" -> "Capitals"
                        "flags" -> "Flags"
                        else -> "Countries"
                    }
                    ResultRow(resultLabel, "$correctAnswers / $totalCountries")
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultRow("Percentage", String.format("%.1f%%", percentage))
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultRow("Time", timeFormatted)
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultRow("Incorrect Guesses", incorrectGuesses.toString())
                    if (perfectBonus) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Perfect! +20% bonus!",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = CorrectGreen,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Share buttons
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val deepLink = ChallengeDeepLink(
                            challengeId = UUID.randomUUID().toString(),
                            categoryType = categoryType,
                            categoryValue = categoryValue,
                            challengerName = playerName,
                            challengerScore = correctAnswers,
                            challengerTotal = totalCountries,
                            challengerTime = timeElapsedSeconds,
                            quizMode = quizMode
                        )
                        ShareUtils.shareResults(
                            context = context,
                            categoryName = categoryName,
                            score = correctAnswers,
                            total = totalCountries,
                            time = timeElapsedSeconds,
                            deepLink = deepLink.toUri()
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = {
                        val deepLink = ChallengeDeepLink(
                            challengeId = UUID.randomUUID().toString(),
                            categoryType = categoryType,
                            categoryValue = categoryValue,
                            challengerName = playerName,
                            challengerScore = null,
                            challengerTotal = null,
                            challengerTime = null,
                            quizMode = quizMode
                        )
                        ShareUtils.shareChallenge(
                            context = context,
                            categoryName = categoryName,
                            deepLink = deepLink.toUri()
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Challenge")
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
                Text("View Answers")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPlayAgain,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Play Again")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Home")
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
