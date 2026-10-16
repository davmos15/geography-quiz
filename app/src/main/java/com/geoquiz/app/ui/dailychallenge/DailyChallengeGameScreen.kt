package com.geoquiz.app.ui.dailychallenge

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeQuestion
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeQuestionKind
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.components.FlagImage
import com.geoquiz.app.ui.components.ReadableWidthFrame
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.quiz.components.AnswerInput
import com.geoquiz.app.ui.quiz.components.MultipleChoicePanel

/**
 * Daily challenge's game screen (task 8.1): one question at a time, progress "Question X of 10",
 * [com.geoquiz.app.ui.components.AnswerInput] for the two typed kinds and
 * [MultipleChoicePanel] for the two choice kinds - the exact widgets classic typed quizzes and
 * Easy-tier quizzes already use. Finishing the 10th question or "End challenge" navigates to
 * Results via [onFinished]. Backgrounding pauses the round like every other new mode (D37); a
 * paused overlay with Resume is the only way back in. A fresh start for a date already recorded
 * (task 8.2's hard guard, D54) shows [DailyChallengeUiState.AlreadyPlayed] instead - a short
 * message with a Back button, no round.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: DailyChallengeGameViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    LaunchedEffect(completion) {
        val current = completion
        if (current != null && current.step == DailyChallengeCompletion.Step.NAVIGATE) {
            onFinished(current.resultId)
            viewModel.onNavigatedToResults()
        }
    }

    var showGiveUpDialog by remember { mutableStateOf(false) }
    val roundInProgress = (uiState as? DailyChallengeUiState.Active)?.state?.isComplete == false
    // Back during a round asks first (D39/D45 precedent): there is no resume save (D37), so
    // leaving would throw the round away unrecorded.
    BackHandler(enabled = roundInProgress) { showGiveUpDialog = true }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_daily_challenge_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundInProgress) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is DailyChallengeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DailyChallengeUiState.Unavailable -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.daily_challenge_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                        Button(onClick = onNavigateBack, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            is DailyChallengeUiState.AlreadyPlayed -> {
                // The hard one-attempt-per-day guard (task 8.2, D54): reuses the Setup screen's
                // own "already played" wording rather than new, near-duplicate strings.
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.daily_challenge_completed_title),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = stringResource(R.string.daily_challenge_completed_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                }
            }
            is DailyChallengeUiState.Active -> {
                val round = state.state
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    DailyChallengeActiveContent(
                        round = round,
                        viewModel = viewModel,
                        onGiveUp = { showGiveUpDialog = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (round.paused) Modifier.clearAndSetSemantics { } else Modifier)
                    )

                    if (round.paused) {
                        PausedOverlay(
                            progressText = stringResource(
                                R.string.mode_daily_challenge_paused_progress,
                                round.correctCodes.size,
                                round.totalCount
                            ),
                            onResume = viewModel::onResume
                        )
                    }
                }

                if (showGiveUpDialog) {
                    AlertDialog(
                        onDismissRequest = { showGiveUpDialog = false },
                        title = { Text(stringResource(R.string.quiz_give_up_title)) },
                        text = {
                            Text(
                                stringResource(
                                    R.string.mode_daily_challenge_give_up,
                                    round.correctCodes.size,
                                    round.totalCount
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showGiveUpDialog = false
                                viewModel.onGiveUp()
                            }) { Text(stringResource(R.string.quiz_give_up_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showGiveUpDialog = false }) {
                                Text(stringResource(R.string.quiz_give_up_dismiss))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyChallengeActiveContent(
    round: DailyChallengeRoundState,
    viewModel: DailyChallengeGameViewModel,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val question = round.currentQuestion

    ReadableWidthFrame(modifier = modifier) { sideInset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp + sideInset, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.daily_challenge_progress,
                    (round.displayIndex + 1).coerceAtMost(round.totalCount),
                    round.totalCount
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )

            if (question != null) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QuestionContent(question = question, round = round, viewModel = viewModel)
                }

                OutlinedButton(onClick = onGiveUp) {
                    Text(stringResource(R.string.daily_challenge_give_up))
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(
    question: DailyChallengeQuestion,
    round: DailyChallengeRoundState,
    viewModel: DailyChallengeGameViewModel
) {
    val enabled = !round.pendingAdvance
    when (question.kind) {
        DailyChallengeQuestionKind.TYPED_COUNTRY -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FlagImage(
                    countryCode = question.target.code,
                    contentDescription = stringResource(R.string.choice_flag_description),
                    height = 120.dp
                )
            }
            Text(
                text = stringResource(R.string.daily_challenge_prompt_typed_country),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            AnswerInput(
                value = round.currentInput,
                onValueChange = viewModel::onInputChange,
                onSubmit = viewModel::onSubmitTyped,
                lastResult = round.lastAnswerResult,
                enabled = enabled,
                quizMode = QuizMode.COUNTRIES
            )
        }
        DailyChallengeQuestionKind.TYPED_CAPITAL -> {
            Text(
                text = stringResource(R.string.choice_prompt_capital_of, question.target.name),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            AnswerInput(
                value = round.currentInput,
                onValueChange = viewModel::onInputChange,
                onSubmit = viewModel::onSubmitTyped,
                lastResult = round.lastAnswerResult,
                enabled = enabled,
                quizMode = QuizMode.CAPITALS
            )
        }
        DailyChallengeQuestionKind.CHOICE_FLAG, DailyChallengeQuestionKind.CHOICE_CAPITAL -> {
            val choice = question.choice
            if (choice != null) {
                MultipleChoicePanel(
                    question = choice,
                    feedback = round.choiceFeedback,
                    enabled = enabled,
                    onSelect = viewModel::onChoiceSelected
                )
            }
        }
    }
}

@Composable
private fun PausedOverlay(progressText: String, onResume: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PauseCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.quiz_paused),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }.padding(top = 16.dp)
            )
            Text(
                progressText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(onClick = onResume, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.quiz_resume))
            }
        }
    }
}
