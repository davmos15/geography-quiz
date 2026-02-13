package com.geoquiz.app.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.quiz.components.AnswerInput
import com.geoquiz.app.ui.quiz.components.CountryList
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.theme.CorrectGreen
import com.geoquiz.app.ui.theme.IncorrectRed

@Composable
fun QuizScreen(
    onQuizComplete: (score: Double, correct: Int, total: Int, time: Int, perfectBonus: Boolean, categoryName: String, categoryType: String, categoryValue: String, incorrectGuesses: Int) -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showTimer by viewModel.showTimer.collectAsStateWithLifecycle()
    val showFlags by viewModel.showFlags.collectAsStateWithLifecycle()
    val showCountryHint by viewModel.showCountryHint.collectAsStateWithLifecycle()
    val hardMode by viewModel.hardMode.collectAsStateWithLifecycle()
    var showGiveUpDialog by remember { mutableStateOf(false) }

    // Auto-pause and save when app goes to background
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onBackgrounded()
    }

    when (val state = uiState) {
        is QuizUiState.Loading -> {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading quiz...")
                }
            }
        }

        is QuizUiState.Active -> {
            val quizState = state.state

            LaunchedEffect(quizState.isComplete) {
                if (quizState.isComplete) {
                    val result = viewModel.getResult() ?: return@LaunchedEffect
                    onQuizComplete(
                        result.score,
                        result.correctAnswers,
                        result.totalCountries,
                        result.timeElapsedSeconds,
                        result.perfectBonus,
                        result.category.displayName,
                        viewModel.category.typeKey,
                        viewModel.category.valueKey,
                        result.incorrectGuesses
                    )
                }
            }

            Scaffold { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Category name
                        Text(
                            text = quizState.quiz.category.displayName,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        // Pattern explainer banner
                        val patternDescription = quizState.quiz.category.description
                        if (patternDescription != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = patternDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Header row with progress, optional timer, incorrect count, and pause button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${quizState.answeredCountries.size} / ${quizState.quiz.countries.size}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (showTimer) {
                                Spacer(modifier = Modifier.width(12.dp))
                                TimerDisplay(
                                    elapsedSeconds = quizState.timeElapsedSeconds,
                                    timerSeconds = null
                                )
                            }
                            if (hardMode) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${quizState.incorrectGuesses} / 3",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (quizState.incorrectGuesses >= 2) IncorrectRed
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (quizState.incorrectGuesses > 0) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${quizState.incorrectGuesses}x",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IncorrectRed
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.togglePause() }) {
                                Icon(
                                    imageVector = if (quizState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (quizState.isPaused) "Resume" else "Pause"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { quizState.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = CorrectGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Answer input
                        AnswerInput(
                            value = quizState.currentInput,
                            onValueChange = viewModel::onInputChange,
                            onSubmit = viewModel::onSubmitAnswer,
                            lastResult = quizState.lastAnswerResult,
                            enabled = !quizState.isComplete && !quizState.isPaused
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Give up button
                        OutlinedButton(
                            onClick = { showGiveUpDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !quizState.isPaused
                        ) {
                            Text("Give Up")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Country list
                        CountryList(
                            countries = quizState.quiz.countries,
                            answeredCodes = quizState.answeredCountries,
                            quizMode = viewModel.quizMode,
                            showFlags = showFlags,
                            showCountryHint = showCountryHint,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Pause overlay
                    if (quizState.isPaused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PauseCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Paused",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val itemLabel = when (viewModel.quizMode) {
                                    QuizMode.CAPITALS -> "capitals named"
                                    QuizMode.FLAGS -> "flags identified"
                                    QuizMode.COUNTRIES -> "countries named"
                                }
                                Text(
                                    "${quizState.answeredCountries.size} / ${quizState.quiz.countries.size} $itemLabel",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { viewModel.togglePause() }) {
                                    Text("Resume")
                                }
                            }
                        }
                    }
                }
            }

            if (showGiveUpDialog) {
                AlertDialog(
                    onDismissRequest = { showGiveUpDialog = false },
                    title = { Text("Give Up?") },
                    text = {
                        val giveUpLabel = when (viewModel.quizMode) {
                            QuizMode.CAPITALS -> "capitals"
                            QuizMode.FLAGS -> "flags"
                            QuizMode.COUNTRIES -> "countries"
                        }
                        Text(
                            "You've named ${quizState.answeredCountries.size} of " +
                                    "${quizState.quiz.countries.size} $giveUpLabel. Are you sure?"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showGiveUpDialog = false
                            viewModel.onGiveUp()
                        }) {
                            Text("Yes, Give Up")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGiveUpDialog = false }) {
                            Text("Keep Going")
                        }
                    }
                )
            }
        }
    }
}
