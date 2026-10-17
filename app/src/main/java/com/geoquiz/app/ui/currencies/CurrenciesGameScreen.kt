package com.geoquiz.app.ui.currencies

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.components.ReadableWidthFrame
import com.geoquiz.app.ui.components.WrappingTopAppBar

/**
 * Currencies' game screen (task 7.1), mirroring `SilhouettesGameScreen.kt`'s shape: one item at a
 * time, typed (Normal/Hard, [CurrencyInputRow]) or four choices with flags
 * ([CurrencyChoiceOptions], Easy or any shared currency, D50). Registered in
 * `ui/navigation/AppNavigation.kt` as `Screen.Currencies`. Finishing (every item answered,
 * give-up, or a Hard strike-out) navigates to Results via [onFinished]. Backgrounding pauses the
 * round; a paused overlay with Resume is the only way back.
 *
 * Unlike Silhouettes/Tap the map, this screen is a single scrolling column at every window size
 * (no two-pane layout for wide screens): a deliberate scope reduction for this first version,
 * flagged for the lead/a later pass to match the other modes' adaptive layouts if wanted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrenciesGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CurrenciesGameViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerVisible by viewModel.timerVisible.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    LaunchedEffect(completion) {
        val current = completion
        if (current != null && current.step == CurrenciesCompletion.Step.NAVIGATE) {
            onFinished(current.resultId)
            viewModel.onNavigatedToResults()
        }
    }

    var showGiveUpDialog by remember { mutableStateOf(false) }
    val roundInProgress = (uiState as? CurrenciesUiState.Active)?.state?.isComplete == false
    BackHandler(enabled = roundInProgress) { showGiveUpDialog = true }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_currencies_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundInProgress) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is CurrenciesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is CurrenciesUiState.Unavailable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.currencies_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Button(onClick = onNavigateBack) { Text(stringResource(R.string.action_back)) }
                    }
                }
            }
            is CurrenciesUiState.Active -> {
                val round = state.state
                Box(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
                    CurrenciesActiveContent(
                        round = round,
                        timerVisible = timerVisible,
                        timerSeconds = timerSeconds,
                        viewModel = viewModel,
                        onGiveUp = { showGiveUpDialog = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (round.paused) Modifier.clearAndSetSemantics { } else Modifier)
                    )

                    if (round.paused) {
                        CurrenciesPausedOverlay(
                            progressText = stringResource(
                                R.string.mode_currencies_paused_progress, round.answeredCodes.size, round.items.size
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
                                stringResource(R.string.mode_currencies_give_up, round.answeredCodes.size, round.items.size)
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
private fun CurrenciesActiveContent(
    round: CurrencyRoundState,
    timerVisible: Boolean,
    timerSeconds: Int,
    viewModel: CurrenciesGameViewModel,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = round.current
    Column(modifier = modifier) {
        CurrenciesHeader(
            current = round.progress,
            total = round.items.size,
            difficulty = round.difficulty,
            incorrectGuesses = round.incorrectGuesses,
            timerVisible = timerVisible,
            timerSeconds = timerSeconds,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ReadableWidthFrame(modifier = Modifier.weight(1f).fillMaxWidth()) { sideInset ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp + sideInset),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (item != null) {
                    CurrencyClueCard(entry = item.entry, modifier = Modifier.fillMaxWidth())

                    if (round.isCurrentChoice) {
                        round.choice?.let { choice ->
                            CurrencyChoiceOptions(
                                question = choice,
                                feedback = round.choiceFeedback,
                                enabled = !round.isAdvancing && !round.paused,
                                onSelect = viewModel::onChoiceSelected,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    CurrencyFeedbackBanner(feedback = round.feedback, modifier = Modifier.fillMaxWidth())

                    if (round.isAdvancing) {
                        CurrencyNextButton(onNext = viewModel::onNext, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (item != null && !round.isCurrentChoice) {
                CurrencyInputRow(
                    input = round.currentInput,
                    onInputChange = viewModel::onInputChange,
                    onSubmit = viewModel::onSubmit,
                    enabled = !round.isComplete && !round.isAdvancing && !round.paused,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onGiveUp, enabled = !round.paused) {
                    Text(stringResource(R.string.quiz_give_up))
                }
            }
        }
    }
}

@Composable
private fun CurrenciesPausedOverlay(progressText: String, onResume: () -> Unit) {
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
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.quiz_paused),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                progressText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(24.dp))
            Button(onClick = onResume) {
                Text(stringResource(R.string.quiz_resume))
            }
        }
    }
}
