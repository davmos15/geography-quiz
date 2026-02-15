package com.geoquiz.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onNavigateToChallenges: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToAchievements,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Achievements")
                }
                OutlinedButton(
                    onClick = onNavigateToChallenges,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Leaderboard, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Challenges")
                }
            }

            StatsCard(title = "Overview") {
                StatRow("Quizzes Completed", state.totalQuizzesCompleted.toString())
                StatRow("Unique Quizzes Played", state.uniqueQuizzesCompleted.toString())
                StatRow("Perfect Scores (100%)", state.totalPerfectQuizzes.toString())
                StatRow("Highest Score", String.format("%.1f", state.highestScore))
                StatRow("Average Accuracy", String.format("%.1f%%", state.averageAccuracy * 100))
            }

            StatsCard(title = "Answers") {
                StatRow("Total Correct Answers", state.totalCorrectAnswers.toString())
                StatRow("Total Incorrect Guesses", state.totalIncorrectGuesses.toString())
                StatRow("Total Questions Faced", state.totalQuestionsAnswered.toString())
            }

            StatsCard(title = "Time") {
                val totalMinutes = state.totalTimeSpentSeconds / 60
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                StatRow("Total Time Playing", timeStr)
            }

            StatsCard(title = "By Mode") {
                StatRow("Countries Quizzes", state.countriesQuizCount.toString())
                StatRow("Capitals Quizzes", state.capitalsQuizCount.toString())
                StatRow("Flags Quizzes", state.flagsQuizCount.toString())
            }

            StatsCard(title = "Achievements") {
                StatRow(
                    "Unlocked",
                    "${state.unlockedAchievementCount} / ${state.totalAchievementCount}"
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
