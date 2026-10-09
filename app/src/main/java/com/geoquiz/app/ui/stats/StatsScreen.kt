package com.geoquiz.app.ui.stats

import androidx.compose.ui.res.stringResource
import com.geoquiz.app.R
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.ui.components.AdaptiveButtonRow
import com.geoquiz.app.ui.components.WrappingTopAppBar
import androidx.compose.ui.text.style.TextAlign
import java.util.Locale
import com.geoquiz.app.ui.components.readableWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToAchievements: () -> Unit,
    onNavigateToChallenges: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.nav_stats),
                // No back arrow when shown as a bottom navigation tab
                navigationIcon = onNavigateBack?.let { back ->
                    @Composable {
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
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
                .readableWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Side by side when both labels fit on one line, otherwise stacked (3.6).
            AdaptiveButtonRow(
                modifier = Modifier.fillMaxWidth(),
                spacing = 12.dp
            ) {
                OutlinedButton(
                    onClick = onNavigateToAchievements
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Achievements")
                }
                OutlinedButton(
                    onClick = onNavigateToChallenges
                ) {
                    Icon(Icons.Default.Leaderboard, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Challenges")
                }
            }
            OutlinedButton(
                onClick = { viewModel.showLeaderboards() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Leaderboard, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Leaderboards")
            }

            StatsCard(title = "Overview") {
                StatRow("Quizzes Completed", state.totalQuizzesCompleted.toString())
                StatRow("Unique Quizzes Played", state.uniqueQuizzesCompleted.toString())
                StatRow("Perfect Scores (100%)", state.totalPerfectQuizzes.toString())
                StatRow("Highest Score", String.format(Locale.US, "%.1f", state.highestScore))
                StatRow("Average Accuracy", String.format(Locale.US, "%.1f%%", state.averageAccuracy * 100))
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

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset Statistics")
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Statistics?") },
            text = { Text("This will permanently delete all quiz history and statistics. Achievements will not be affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetStatistics()
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
