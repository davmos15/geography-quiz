@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.geoquiz.app.ui.borderhop

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.FlagImage
import com.geoquiz.app.ui.components.ReadableWidthFrame
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.buttonSemantics
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.theme.geoColors

/**
 * Border hop's game screen (task 6.1): the current country ([BorderHopHeader]), the chain hopped
 * so far and the target ([BorderHopPathRow]), then every valid next hop as a tappable card
 * ([BorderHopNeighbourCard]). No typed input and no wrong answer: a tap is always a valid hop
 * (D44). Reaching the target or Give up navigates to Results via [onFinished]. Backgrounding
 * pauses the round like the other new modes; a paused overlay with Resume is the only way back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BorderHopGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: BorderHopGameViewModel = hiltViewModel()
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
        if (current != null && current.step == BorderHopCompletion.Step.NAVIGATE) {
            onFinished(current.resultId)
            viewModel.onNavigatedToResults()
        }
    }

    var showGiveUpDialog by remember { mutableStateOf(false) }
    val roundInProgress = (uiState as? BorderHopUiState.Active)?.state?.isComplete == false
    // Back during a round asks first (D39 precedent): there is no resume save (D37), so leaving
    // would throw the round away unrecorded.
    BackHandler(enabled = roundInProgress) { showGiveUpDialog = true }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_border_hop_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundInProgress) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is BorderHopUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is BorderHopUiState.Unavailable -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.border_hop_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text(stringResource(R.string.action_back)) }
                    }
                }
            }
            is BorderHopUiState.Active -> {
                val round = state.state
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    BorderHopActiveContent(
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
                        PausedOverlay(
                            progressText = stringResource(R.string.mode_border_hop_paused_progress, round.hopsUsed),
                            onResume = viewModel::onResume
                        )
                    }
                }

                if (showGiveUpDialog) {
                    val target = viewModel.countryFor(round.targetCode)?.name.orEmpty()
                    AlertDialog(
                        onDismissRequest = { showGiveUpDialog = false },
                        title = { Text(stringResource(R.string.quiz_give_up_title)) },
                        text = { Text(stringResource(R.string.mode_border_hop_give_up, round.hopsUsed, round.parHops, target)) },
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
private fun BorderHopActiveContent(
    round: BorderHopState,
    timerVisible: Boolean,
    timerSeconds: Int,
    viewModel: BorderHopGameViewModel,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val start = viewModel.countryFor(round.startCode)
    val current = viewModel.countryFor(round.currentCode)
    val target = viewModel.countryFor(round.targetCode)
    val neighbours = viewModel.neighbourOptions()
    val hintedCode = round.revealedHopCode

    ReadableWidthFrame(modifier = modifier) { sideInset ->
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp + sideInset, vertical = 8.dp)) {
                BorderHopHeader(
                    hopsUsed = round.hopsUsed,
                    parHops = round.parHops,
                    difficulty = round.difficulty,
                    timerVisible = timerVisible,
                    timerSeconds = timerSeconds
                )
                Spacer(modifier = Modifier.height(8.dp))
                BorderHopPathRow(
                    path = round.path.mapNotNull { viewModel.countryFor(it) },
                    target = target,
                    reachedTarget = round.reachedTarget && round.isComplete
                )
            }

            if (round.isComplete) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BorderHopOutcomeBanner(round = round, targetName = target?.name.orEmpty())
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp + sideInset, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "current") {
                        Text(
                            text = stringResource(R.string.border_hop_current_location, current?.name ?: round.currentCode),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }
                        )
                    }
                    items(neighbours, key = { it.code }) { neighbour ->
                        BorderHopNeighbourCard(
                            country = neighbour,
                            isHinted = neighbour.code == hintedCode,
                            onClick = { viewModel.onHop(neighbour.code) }
                        )
                    }
                    if (neighbours.isEmpty()) {
                        item(key = "no-moves") {
                            Text(
                                text = stringResource(R.string.border_hop_no_neighbours),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp + sideInset, vertical = 8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::onUndo, enabled = round.canUndo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.border_hop_undo))
                        }
                        // Task 8.4: disabled for the rest of the round once its difficulty tier's
                        // hint cap (HintCosts.maxHintsPerRound) is reached, alongside the existing
                        // "already revealed at this position" disable. A tier with no hints at all
                        // (Easy) hides the button, like Silhouettes and Tap the map (D62).
                        val maxHints = com.geoquiz.app.domain.mode.HintCosts.maxHintsPerRound(round.difficulty)
                        if (maxHints > 0) {
                            OutlinedButton(
                                onClick = viewModel::onHint,
                                enabled = hintedCode == null && round.hintsUsed.size < maxHints
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.border_hop_hint, HintCostsForDisplay))
                            }
                        }
                        OutlinedButton(onClick = onGiveUp) {
                            Text(stringResource(R.string.quiz_give_up))
                        }
                    }
                }
            }
        }
    }
}

/** [com.geoquiz.app.domain.mode.HintCosts.cost] for [com.geoquiz.app.domain.mode.HintType.SHOW_VALID_HOP]. */
private val HintCostsForDisplay: Double
    get() = com.geoquiz.app.domain.mode.HintCosts.cost(com.geoquiz.app.domain.mode.HintType.SHOW_VALID_HOP)

@Composable
private fun BorderHopHeader(
    hopsUsed: Int,
    parHops: Int,
    difficulty: com.geoquiz.app.domain.model.Difficulty,
    timerVisible: Boolean,
    timerSeconds: Int
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.border_hop_progress, hopsUsed, parHops),
            style = MaterialTheme.typography.titleMedium
        )
        DifficultyLabel(difficulty = difficulty)
        if (timerVisible) {
            TimerDisplay(elapsedSeconds = timerSeconds, timerSeconds = null)
        }
    }
}

/**
 * The chain hopped so far, ending with the target chip (D44: "shows route chips"). One row that
 * scrolls sideways and follows the latest hop, so a long path never pushes the neighbour list off
 * a small screen at large text (Phase 9 review).
 */
@Composable
private fun BorderHopPathRow(path: List<Country>, target: Country?, reachedTarget: Boolean) {
    val scrollState = rememberScrollState()
    // maxValue only grows once the new chip is laid out, so follow it rather than reading it once.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue }.collect { scrollState.animateScrollTo(it) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        path.forEachIndexed { index, country ->
            val isLast = index == path.lastIndex
            BorderHopChip(country = country, isTarget = reachedTarget && isLast, isCurrent = isLast && !reachedTarget)
        }
        if (target != null && (path.isEmpty() || path.last().code != target.code)) {
            BorderHopChip(country = target, isTarget = true, isCurrent = false)
        }
    }
}

@Composable
private fun BorderHopChip(country: Country, isTarget: Boolean = false, isCurrent: Boolean = false) {
    val label = if (isTarget) stringResource(R.string.border_hop_target_chip, country.name) else country.name
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent || isTarget) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FlagImage(countryCode = country.code, contentDescription = null, height = 16.dp)
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BorderHopNeighbourCard(country: Country, isHinted: Boolean, onClick: () -> Unit) {
    val actionLabel = stringResource(R.string.border_hop_hop_to_action, country.name)
    val hintedLabel = stringResource(R.string.border_hop_hint_suggested_hop, country.name)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .buttonSemantics(actionLabel, onClick)
            // The hint is otherwise only a colour and a decorative bulb: say it, and announce it
            // once when it appears (Phase 9 review).
            .then(
                if (isHinted) {
                    Modifier.semantics {
                        contentDescription = hintedLabel
                        liveRegion = LiveRegionMode.Polite
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHinted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlagImage(countryCode = country.code, contentDescription = null, height = 24.dp)
            Text(text = country.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (isHinted) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BorderHopOutcomeBanner(round: BorderHopState, targetName: String) {
    val reached = round.reachedTarget
    val icon = if (reached) Icons.Default.Check else Icons.Default.Close
    val colour = if (reached) MaterialTheme.geoColors.correct else MaterialTheme.geoColors.wrong
    val text = if (reached) {
        stringResource(R.string.border_hop_reached, targetName, round.hopsUsed, round.parHops)
    } else {
        stringResource(R.string.border_hop_gave_up_outcome, targetName)
    }
    Row(
        modifier = Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colour)
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = colour)
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
