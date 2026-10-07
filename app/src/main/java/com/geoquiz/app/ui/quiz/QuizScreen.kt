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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.R
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.quiz.components.AnswerInput
import com.geoquiz.app.ui.quiz.components.CountryList
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.theme.geoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    onQuizComplete: (resultId: String) -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val showTimer by viewModel.showTimer.collectAsStateWithLifecycle()
    val showFlags by viewModel.showFlags.collectAsStateWithLifecycle()
    val showCountryHint by viewModel.showCountryHint.collectAsStateWithLifecycle()
    val timerVisible by viewModel.timerVisible.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    var showGiveUpDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val completion by viewModel.completion.collectAsStateWithLifecycle()

    // Auto-pause and save when the app goes to the background. Rotation also stops the
    // activity, but the ViewModel survives it, so the quiz keeps running.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    // Once the quiz is recorded, go to Results. Any interstitial is shown there, after Results
    // has rendered (see ResultsScreen), never on the way.
    LaunchedEffect(completion) {
        val done = completion ?: return@LaunchedEffect
        when (done.step) {
            QuizCompletion.Step.DONE -> Unit
            QuizCompletion.Step.NAVIGATE -> {
                viewModel.onNavigatedToResults()
                onQuizComplete(done.resultId)
            }
        }
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
            val res = a11yResources()
            val answeredCount = quizState.answeredCountries.size
            val totalCount = quizState.quiz.countries.size
            val progressDescription = A11yText.progress(res, viewModel.quizMode, answeredCount, totalCount)

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
                            // While paused, only the pause overlay is reachable by TalkBack.
                            .then(if (quizState.isPaused) Modifier.clearAndSetSemantics { } else Modifier)
                    ) {
                        // Category name
                        Text(
                            text = quizState.quiz.category.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        // The tier is fixed for the whole quiz, so it is a label, not a control.
                        DifficultyLabel(
                            difficulty = difficulty,
                            modifier = Modifier.padding(top = 4.dp)
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
                            // Polite live region: announced when the count changes (a correct
                            // answer), e.g. "12 of 197 countries named". Not tied to the timer.
                            Text(
                                text = "$answeredCount / $totalCount",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = progressDescription
                                    liveRegion = LiveRegionMode.Polite
                                }
                            )
                            if (timerVisible) {
                                Spacer(modifier = Modifier.width(12.dp))
                                TimerDisplay(
                                    elapsedSeconds = timerSeconds,
                                    timerSeconds = null
                                )
                            }
                            val strikeLimit = difficulty.strikeLimit
                            if (strikeLimit != null) {
                                Spacer(modifier = Modifier.width(12.dp))
                                val strikesDescription = A11yText.strikes(res, quizState.incorrectGuesses, strikeLimit)
                                val strikesColor = if (quizState.incorrectGuesses >= strikeLimit - 1) MaterialTheme.geoColors.wrong
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                // Cross icon + count, so strikes never rely on colour alone.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = strikesDescription
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = strikesColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${quizState.incorrectGuesses} / $strikeLimit",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = strikesColor
                                    )
                                }
                            } else if (quizState.incorrectGuesses > 0) {
                                Spacer(modifier = Modifier.width(12.dp))
                                val incorrectDescription = A11yText.incorrectGuesses(res, quizState.incorrectGuesses)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = incorrectDescription
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.geoColors.wrong,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${quizState.incorrectGuesses}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.geoColors.wrong
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { showSettingsSheet = true }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                            IconButton(onClick = { viewModel.togglePause() }) {
                                Icon(
                                    imageVector = if (quizState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (quizState.isPaused) "Resume" else "Pause"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Same information as the count above, so hidden from TalkBack.
                        LinearProgressIndicator(
                            progress = { quizState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics { },
                            color = MaterialTheme.geoColors.correct,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Answer input
                        AnswerInput(
                            value = quizState.currentInput,
                            onValueChange = viewModel::onInputChange,
                            onSubmit = viewModel::onSubmitAnswer,
                            lastResult = quizState.lastAnswerResult,
                            enabled = !quizState.isComplete && !quizState.isPaused,
                            quizMode = viewModel.quizMode
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Give up + Submit row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showGiveUpDialog = true },
                                enabled = !quizState.isPaused
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Give Up",
                                    tint = if (!quizState.isPaused) MaterialTheme.geoColors.wrong
                                        else MaterialTheme.geoColors.wrong.copy(alpha = 0.38f)
                                )
                            }
                            Button(
                                onClick = viewModel::onSubmitAnswer,
                                modifier = Modifier.weight(1f),
                                enabled = !quizState.isComplete && !quizState.isPaused
                            ) {
                                Text("Submit")
                            }
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
                                // Live region so pausing is announced when the overlay appears.
                                Text(
                                    "Paused",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.semantics {
                                        heading()
                                        liveRegion = LiveRegionMode.Polite
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(
                                        viewModel.modeSpec.labels.pausedProgress, answeredCount, totalCount
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = progressDescription
                                    }
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

            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .semantics { heading() }
                        )
                        if (difficulty.timerAlwaysShown) {
                            // Hard always shows the timer (D15), so there is nothing to switch.
                            SettingsToggleRow(
                                label = "Show Timer",
                                description = stringResource(R.string.quiz_timer_always_shown_hard),
                                checked = true,
                                enabled = false,
                                onToggle = {}
                            )
                        } else {
                            SettingsToggleRow("Show Timer", "Display count-up timer during quizzes", showTimer) {
                                viewModel.toggleShowTimer()
                            }
                        }
                        SettingsToggleRow("Show Flags", "Show flags next to countries", showFlags) {
                            viewModel.toggleShowFlags()
                        }
                        if (viewModel.quizMode == QuizMode.CAPITALS) {
                            SettingsToggleRow("Country Hint", "Show the country name as a clue", showCountryHint) {
                                viewModel.toggleShowCountryHint()
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            if (showGiveUpDialog) {
                AlertDialog(
                    onDismissRequest = { showGiveUpDialog = false },
                    title = { Text("Give Up?") },
                    text = {
                        Text(
                            stringResource(
                                viewModel.modeSpec.labels.giveUpMessage,
                                quizState.answeredCountries.size,
                                quizState.quiz.countries.size
                            )
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

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}
