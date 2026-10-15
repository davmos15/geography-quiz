package com.geoquiz.app.ui.flagspeed

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeSet
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.FlagImage
import com.geoquiz.app.ui.components.ReadableWidthFrame
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.rememberReducedMotion
import com.geoquiz.app.ui.quiz.components.MultipleChoicePanel
import com.geoquiz.app.ui.quiz.feedback.FeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.FeedbackSignal
import com.geoquiz.app.ui.quiz.feedback.rememberFeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer

/**
 * Flag speed's game screen (task 7.3, D52): a countdown, the live combo, and the current
 * question ([MultipleChoicePanel], reusing the Easy-tier panel exactly - D52 - with its own
 * prompt card for [com.geoquiz.app.domain.mode.ChoicePrompt.FlagOf]). The round ends itself the
 * instant the countdown reaches zero, or earlier via Give up; either way it shows a review of the
 * lookalike sets encountered ([FlagSpeedCompletion.Step.REVIEW]) before leaving for the shared
 * Results screen. Backgrounding pauses the round like the other new modes; a paused overlay with
 * Resume is the only way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagSpeedGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: FlagSpeedGameViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
    val vibrationEnabled by rememberUpdatedState(vibration)
    val haptics = rememberHapticFeedbackPlayer()

    var feedbackSignal by remember { mutableStateOf(FeedbackSignal()) }
    LaunchedEffect(viewModel) {
        viewModel.feedbackEvents.collect { event ->
            if (vibrationEnabled) haptics.play(event)
            feedbackSignal = feedbackSignal.next(event)
        }
    }
    val reducedMotion = rememberReducedMotion()
    val feedbackMotion = rememberFeedbackMotion(feedbackSignal, reducedMotion)

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    LaunchedEffect(completion) {
        val current = completion
        if (current != null && current.step == FlagSpeedCompletion.Step.NAVIGATE) {
            onFinished(current.resultId)
            viewModel.onNavigatedToResults()
        }
    }

    var showGiveUpDialog by remember { mutableStateOf(false) }
    val roundInProgress = (uiState as? FlagSpeedUiState.Active)?.state?.isComplete == false
    // Back during a round asks first (D39 precedent): there is no resume save (D37), so leaving
    // would throw the round away unrecorded.
    BackHandler(enabled = roundInProgress) { showGiveUpDialog = true }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_flag_speed_round_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundInProgress) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is FlagSpeedUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is FlagSpeedUiState.Unavailable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.flag_speed_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text(stringResource(R.string.action_back)) }
                    }
                }
            }
            is FlagSpeedUiState.Active -> {
                val round = state.state
                val reviewStep = completion?.step == FlagSpeedCompletion.Step.REVIEW
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when {
                        round.isComplete && reviewStep -> FlagSpeedReviewContent(
                            round = round,
                            reviewSets = viewModel.reviewSets(),
                            didYouKnow = viewModel.nearIdenticalDidYouKnow(),
                            onContinue = viewModel::onContinueFromReview
                        )
                        round.isComplete -> {
                            // Still recording the result: a brief, unobservable gap in practice.
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        else -> {
                            FlagSpeedActiveContent(
                                round = round,
                                remainingSeconds = remainingSeconds,
                                onSelect = viewModel::onChoiceSelected,
                                onGiveUp = { showGiveUpDialog = true },
                                feedbackMotion = feedbackMotion,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (round.paused) Modifier.clearAndSetSemantics { } else Modifier)
                            )
                            if (round.paused) {
                                FlagSpeedPausedOverlay(
                                    progressText = stringResource(R.string.mode_flag_speed_round_paused_progress, round.correctCount, round.askedCount),
                                    onResume = viewModel::onResume
                                )
                            }
                        }
                    }
                }

                if (showGiveUpDialog) {
                    AlertDialog(
                        onDismissRequest = { showGiveUpDialog = false },
                        title = { Text(stringResource(R.string.quiz_give_up_title)) },
                        text = { Text(stringResource(R.string.mode_flag_speed_round_give_up, round.correctCount, round.askedCount)) },
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
private fun FlagSpeedActiveContent(
    round: FlagSpeedRoundState,
    remainingSeconds: Int,
    onSelect: (code: String) -> Unit,
    onGiveUp: () -> Unit,
    feedbackMotion: FeedbackMotion? = null,
    modifier: Modifier = Modifier
) {
    ReadableWidthFrame(modifier = modifier) { sideInset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp + sideInset, vertical = 8.dp)
        ) {
            FlagSpeedHeader(remainingSeconds = remainingSeconds, liveCombo = round.liveCombo, difficulty = round.difficulty)
            Spacer(modifier = Modifier.height(16.dp))
            val question = round.question
            if (question != null) {
                MultipleChoicePanel(
                    question = question,
                    feedback = round.feedback,
                    enabled = !round.paused,
                    onSelect = onSelect,
                    motion = feedbackMotion
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onGiveUp, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.quiz_give_up))
            }
        }
    }
}

@Composable
private fun FlagSpeedHeader(remainingSeconds: Int, liveCombo: Int, difficulty: Difficulty) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlagSpeedCountdown(remainingSeconds = remainingSeconds)
            DifficultyLabel(difficulty = difficulty)
        }
        if (liveCombo >= 2) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.flag_speed_combo, liveCombo, liveCombo),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FlagSpeedCountdown(remainingSeconds: Int) {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formatted = "%d:%02d".format(minutes, seconds)
    val description = stringResource(R.string.flag_speed_time_remaining_a11y, remainingSeconds)
    Text(
        text = formatted,
        style = MaterialTheme.typography.headlineMedium,
        color = if (remainingSeconds in 1..10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        // Not a live region (same reasoning as `TimerDisplay`): it changes every second, so
        // TalkBack reads it only when focus moves there.
        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
    )
}

@Composable
private fun FlagSpeedReviewContent(
    round: FlagSpeedRoundState,
    reviewSets: List<FlagLookalikeSet>,
    didYouKnow: FlagLookalikeSet?,
    onContinue: () -> Unit
) {
    ReadableWidthFrame(modifier = Modifier.fillMaxSize()) { sideInset ->
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp + sideInset, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "summary") {
                    Column {
                        Text(
                            text = stringResource(R.string.flag_speed_round_over_title),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.flag_speed_round_summary,
                                round.correctCount,
                                round.askedCount,
                                round.longestCombo
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                if (reviewSets.isNotEmpty()) {
                    item(key = "explanations-header") {
                        Text(
                            text = stringResource(R.string.flag_speed_review_header),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp).semantics { heading() }
                        )
                    }
                }
                items(reviewSets, key = { it.id }) { set ->
                    FlagSpeedReviewCard(set = set)
                }
                if (didYouKnow != null) {
                    item(key = "did-you-know") {
                        FlagSpeedDidYouKnowCard(set = didYouKnow)
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp + sideInset, vertical = 12.dp)) {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.flag_speed_continue_action))
                }
            }
        }
    }
}

@Composable
private fun FlagSpeedReviewCard(set: FlagLookalikeSet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                set.countries.forEach { code -> FlagImage(countryCode = code, contentDescription = null, height = 20.dp) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = set.explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FlagSpeedDidYouKnowCard(set: FlagLookalikeSet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.flag_speed_did_you_know_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                set.countries.forEach { code -> FlagImage(countryCode = code, contentDescription = null, height = 20.dp) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = set.explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FlagSpeedPausedOverlay(progressText: String, onResume: () -> Unit) {
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
                Icons.Filled.PauseCircle,
                contentDescription = null,
                modifier = Modifier.height(64.dp).width(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.quiz_paused),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                progressText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onResume) {
                Text(stringResource(R.string.quiz_resume))
            }
        }
    }
}
