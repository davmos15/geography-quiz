package com.geoquiz.app.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.ui.quiz.components.AnswerInput
import com.geoquiz.app.ui.quiz.components.CountryList
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.theme.CorrectGreen

@Composable
fun QuizScreen(
    onQuizComplete: (score: Double, correct: Int, total: Int, time: Int, perfectBonus: Boolean, categoryName: String) -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showTimer by viewModel.showTimer.collectAsStateWithLifecycle()
    var showGiveUpDialog by remember { mutableStateOf(false) }

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
                        result.category.displayName
                    )
                }
            }

            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    // Category name
                    Text(
                        text = quizState.quiz.category.displayName,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Header row with progress and optional timer
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
                        enabled = !quizState.isComplete
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Give up button
                    OutlinedButton(
                        onClick = { showGiveUpDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Give Up")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Country list
                    CountryList(
                        countries = quizState.quiz.countries,
                        answeredCodes = quizState.answeredCountries,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (showGiveUpDialog) {
                AlertDialog(
                    onDismissRequest = { showGiveUpDialog = false },
                    title = { Text("Give Up?") },
                    text = {
                        Text(
                            "You've named ${quizState.answeredCountries.size} of " +
                                    "${quizState.quiz.countries.size} countries. Are you sure?"
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
