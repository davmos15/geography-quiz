package com.geoquiz.app.ui.usca

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.quiz.components.FeedbackLine
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.quiz.feedback.HapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer
import com.geoquiz.app.ui.theme.geoColors

/**
 * Name all's game screen (task 6.2, D40's precedent): type every state/province's name; each
 * correct one fills in. Always-visible timer, 3 strikes at Hard. Back during a round asks before
 * giving up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsCaNameAllScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: UsCaNameAllViewModel = hiltViewModel(),
    hapticFeedbackPlayer: HapticFeedbackPlayer = rememberHapticFeedbackPlayer()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    var showGiveUpDialog by remember { mutableStateOf(false) }

    val activeState = (uiState as? UsCaNameAllUiState.Active)?.state
    val roundActive = activeState != null && !activeState.isComplete

    LaunchedEffect(completion) {
        val current = completion
        if (current != null && current.step == UsCaRegionsCompletion.Step.NAVIGATE) {
            onFinished(current.resultId)
            viewModel.onNavigatedToResults()
        }
    }
    LaunchedEffect(vibration) {
        if (vibration) viewModel.feedbackEvents.collect { hapticFeedbackPlayer.play(it) }
    }

    BackHandler(enabled = roundActive) { showGiveUpDialog = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_us_ca_regions_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundActive) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is UsCaNameAllUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UsCaNameAllUiState.Active -> Box(Modifier.fillMaxSize().padding(padding)) {
                UsCaNameAllContent(
                    state = state.state,
                    difficulty = difficulty,
                    timerSeconds = timerSeconds,
                    onInputChange = viewModel::onInputChange,
                    onSubmit = viewModel::onSubmitAnswer,
                    onGiveUp = { showGiveUpDialog = true }
                )
                if (state.state.isPaused) {
                    UsCaPausedOverlay(
                        answeredCount = state.state.answeredCountries.size,
                        totalCount = state.state.quiz.countries.size,
                        onResume = viewModel::togglePause
                    )
                }
            }
        }

        if (showGiveUpDialog) {
            val active = uiState as? UsCaNameAllUiState.Active
            AlertDialog(
                onDismissRequest = { showGiveUpDialog = false },
                title = { Text(stringResource(R.string.quiz_give_up_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.mode_us_ca_regions_give_up,
                            active?.state?.answeredCountries?.size ?: 0,
                            active?.state?.quiz?.countries?.size ?: 0
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showGiveUpDialog = false; viewModel.onGiveUp() }) {
                        Text(stringResource(R.string.quiz_give_up_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGiveUpDialog = false }) { Text(stringResource(R.string.quiz_give_up_dismiss)) }
                }
            )
        }
    }
}

@Composable
internal fun UsCaNameAllContent(
    state: QuizState,
    difficulty: com.geoquiz.app.domain.model.Difficulty,
    timerSeconds: Int,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val found = state.answeredCountries.size
    val total = state.quiz.countries.size

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.a11y_progress_us_ca_regions, total, found, total),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (difficulty == com.geoquiz.app.domain.model.Difficulty.HARD) {
                    val limit = com.geoquiz.app.domain.model.Difficulty.HARD.strikeLimit ?: 3
                    val shown = state.incorrectGuesses.coerceAtMost(limit)
                    Text(text = "$shown/$limit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.geoColors.wrong)
                }
                TimerDisplay(elapsedSeconds = timerSeconds, timerSeconds = null)
                DifficultyLabel(difficulty = difficulty)
            }
        }

        val remaining = remember(state.quiz.countries, state.answeredCountries) {
            state.quiz.countries.filter { it.code !in state.answeredCountries }.sortedBy { it.name }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(remaining, key = { it.code }) { country ->
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(vertical = 2.dp)) {
                    Text(text = country.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            when (val result = state.lastAnswerResult) {
                is AnswerResult.Correct -> FeedbackLine(
                    icon = Icons.Filled.Check,
                    text = stringResource(R.string.choice_feedback_correct),
                    color = MaterialTheme.geoColors.correct,
                    style = MaterialTheme.typography.bodyMedium,
                    iconSize = 18.dp
                )
                AnswerResult.Incorrect -> FeedbackLine(
                    icon = Icons.Filled.Close,
                    text = stringResource(R.string.us_ca_regions_name_all_incorrect),
                    color = MaterialTheme.geoColors.wrong,
                    style = MaterialTheme.typography.bodyMedium,
                    iconSize = 18.dp
                )
                AnswerResult.NearMiss -> Text(
                    text = stringResource(R.string.answer_near_miss),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Unit
            }
        }

        OutlinedTextField(
            value = state.currentInput,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.mode_us_ca_regions_input_label)) },
            singleLine = true,
            enabled = !state.isComplete && !state.isPaused,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSubmit() })
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onGiveUp, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.quiz_give_up))
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onSubmit,
                enabled = !state.isComplete && !state.isPaused && state.currentInput.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.quiz_submit))
            }
        }
    }
}
