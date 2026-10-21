package com.geoquiz.app.ui.usca

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.ui.components.AdaptiveButtonRow
import com.geoquiz.app.ui.components.AdaptiveTrailingRow
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.map.MapCanvas
import com.geoquiz.app.ui.map.MapListAlternative
import com.geoquiz.app.ui.map.MapOrListToggle
import com.geoquiz.app.ui.map.MapViewMode
import com.geoquiz.app.ui.map.mapContentDescription
import com.geoquiz.app.ui.map.rememberIsTouchExplorationEnabled
import com.geoquiz.app.ui.map.rememberMapScene
import com.geoquiz.app.ui.map.rememberMapViewState
import com.geoquiz.app.ui.quiz.components.FeedbackLine
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.quiz.feedback.HapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer
import com.geoquiz.app.ui.theme.geoColors

/**
 * Tap's game screen (task 6.2): the state/province's name as a prompt, tap it on the map (or,
 * with TalkBack on, pick its row in [MapListAlternative]). Finishing navigates to Results exactly
 * like the classic quiz, popping this screen off the back stack. Backgrounding pauses; back
 * during a round asks before giving up (D37/D39's pattern).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsCaTapScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: UsCaTapViewModel = hiltViewModel(),
    hapticFeedbackPlayer: HapticFeedbackPlayer = rememberHapticFeedbackPlayer()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val timerVisible by viewModel.timerVisible.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    var showGiveUpDialog by remember { mutableStateOf(false) }

    val activeState = (uiState as? UsCaTapUiState.Active)?.state
    val roundActive = activeState != null && !activeState.quizState.isComplete

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

    val touchExplorationEnabled = rememberIsTouchExplorationEnabled()
    var viewMode by rememberSaveable { mutableStateOf(if (touchExplorationEnabled) MapViewMode.List else MapViewMode.Map) }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_us_ca_regions_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundActive) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState is UsCaTapUiState.Active) MapOrListToggle(mode = viewMode, onModeChange = { viewMode = it })
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is UsCaTapUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UsCaTapUiState.Unavailable -> UsCaUnavailable(padding, onNavigateBack)
            is UsCaTapUiState.Active -> Box(Modifier.fillMaxSize().padding(padding)) {
                UsCaTapContent(
                    state = state.state,
                    mapState = mapState,
                    difficulty = difficulty,
                    timerVisible = timerVisible,
                    timerSeconds = timerSeconds,
                    viewMode = viewMode,
                    onFeatureTap = viewModel::onFeatureTap,
                    onListSelect = { code -> viewModel.onFeatureTap(MapHit(featureId = code, isPlayable = true, viaTapZone = false)) },
                    onGiveUp = { showGiveUpDialog = true }
                )
                if (state.state.quizState.isPaused) {
                    UsCaPausedOverlay(
                        answeredCount = state.state.quizState.answeredCountries.size,
                        totalCount = state.state.quizState.quiz.countries.size,
                        onResume = viewModel::togglePause
                    )
                }
            }
        }

        if (showGiveUpDialog) {
            val active = uiState as? UsCaTapUiState.Active
            AlertDialog(
                onDismissRequest = { showGiveUpDialog = false },
                title = { Text(stringResource(R.string.quiz_give_up_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.mode_us_ca_regions_give_up,
                            active?.state?.quizState?.answeredCountries?.size ?: 0,
                            active?.state?.quizState?.quiz?.countries?.size ?: 0
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
internal fun UsCaPausedOverlay(answeredCount: Int, totalCount: Int, onResume: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().zIndex(1f).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.quiz_paused),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.mode_us_ca_regions_paused_progress, answeredCount, totalCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onResume) { Text(stringResource(R.string.quiz_resume)) }
        }
    }
}

@Composable
internal fun UsCaTapContent(
    state: UsCaTapGameState,
    mapState: UsCaRegionsMapState,
    difficulty: Difficulty,
    timerVisible: Boolean,
    timerSeconds: Int,
    viewMode: MapViewMode,
    onFeatureTap: (MapHit?) -> Unit,
    onListSelect: (String) -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qs = state.quizState
    val countriesByCode = androidx.compose.runtime.remember(qs.quiz.countries) { qs.quiz.countries.associateBy { it.code } }
    val target = state.currentTargetCode?.let { countriesByCode[it] }
    val featureStates = androidx.compose.runtime.remember(state) { usCaTapFeatureStates(state) }
    val scene = rememberMapScene(
        projection = mapState.projection,
        coarse = mapState.layer,
        bounds = mapState.frame,
        members = qs.quiz.countries.mapTo(HashSet()) { it.code },
        panMarginFraction = 0.2f
    )
    val mapViewState = rememberMapViewState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UsCaStatusRow(qs = qs, difficulty = difficulty, timerVisible = timerVisible, timerSeconds = timerSeconds)

        if (target != null) {
            Text(
                text = stringResource(R.string.us_ca_regions_tap_prompt, target.name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 160.dp)) {
            when {
                viewMode == MapViewMode.Map -> when {
                    scene != null -> MapCanvas(
                        scene = scene,
                        state = mapViewState,
                        modifier = Modifier.fillMaxSize(),
                        featureStates = featureStates,
                        onFeatureTap = onFeatureTap,
                        doubleTapToZoom = false,
                        mapContentDescription = mapContentDescription(
                            res = a11yResources(),
                            regionLabel = qs.quiz.category.displayName,
                            featureStates = featureStates,
                            total = qs.quiz.countries.size
                        )
                    )
                    mapState.loadFailed -> UsCaMapLoadError()
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                else -> MapListAlternative(
                    countries = qs.quiz.countries,
                    featureStates = featureStates,
                    onSelect = onListSelect,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        UsCaTapFeedbackArea(state = state)

        AdaptiveButtonRow(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onGiveUp, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.height(0.dp))
                Text(stringResource(R.string.quiz_give_up))
            }
        }
    }
}

@Composable
internal fun UsCaMapLoadError() {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.us_ca_regions_map_error), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun UsCaStatusRow(qs: QuizState, difficulty: Difficulty, timerVisible: Boolean, timerSeconds: Int) {
    val found = qs.answeredCountries.size
    val total = qs.quiz.countries.size
    // Code review (task 6.2): a plain Row(SpaceBetween) let the strikes/timer/tier group overlap
    // the progress text at 200% font scale (D29). AdaptiveTrailingRow is Tap the map's own fix
    // for the same row shape (TapMapGameScreen.StatusRow): the trailing group wraps to its own
    // line first, rather than squeezing or clipping.
    AdaptiveTrailingRow(
        modifier = Modifier.fillMaxWidth(),
        main = {
            Text(
                text = androidx.compose.ui.res.pluralStringResource(R.plurals.a11y_progress_us_ca_regions, total, found, total),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        trailing = {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Task 9.2 review fix: a bare "1/3" relied on colour alone for what it meant -
                // the same labelled text + content description Tap the map's own status row uses.
                if (difficulty == Difficulty.HARD) {
                    val limit = Difficulty.HARD.strikeLimit ?: 3
                    val shown = qs.incorrectGuesses.coerceAtMost(limit)
                    val description = pluralStringResource(R.plurals.a11y_hard_mode_strikes, limit, shown, limit)
                    Text(
                        text = stringResource(R.string.mode_tap_map_strikes_short, shown, limit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.geoColors.wrong,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
                    )
                }
                if (timerVisible) TimerDisplay(elapsedSeconds = timerSeconds, timerSeconds = null)
                DifficultyLabel(difficulty = difficulty)
            }
        }
    )
}

@Composable
private fun UsCaTapFeedbackArea(state: UsCaTapGameState) {
    Box(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        when {
            state.revealedCode != null -> {
                val name = state.quizState.quiz.countries.find { it.code == state.revealedCode }?.name.orEmpty()
                FeedbackLine(
                    icon = Icons.Filled.Close,
                    text = stringResource(R.string.us_ca_regions_missed_feedback, name),
                    color = MaterialTheme.geoColors.wrong,
                    style = MaterialTheme.typography.bodyMedium,
                    iconSize = 18.dp
                )
            }
            state.wrongFlashName != null -> FeedbackLine(
                icon = Icons.Filled.Close,
                text = stringResource(R.string.us_ca_regions_wrong_tap_feedback, state.wrongFlashName),
                color = MaterialTheme.geoColors.wrong,
                style = MaterialTheme.typography.bodyMedium,
                iconSize = 18.dp
            )
        }
    }
}

internal fun usCaTapFeatureStates(state: UsCaTapGameState): Map<String, MapFeatureState> {
    val qs = state.quizState
    val map = HashMap<String, MapFeatureState>(qs.answeredCountries.size + qs.missedCountries.size)
    qs.answeredCountries.forEach { map[it] = MapFeatureState.Found }
    qs.missedCountries.forEach { map[it] = MapFeatureState.Wrong }
    state.revealedCode?.let { map[it] = MapFeatureState.Highlighted }
    state.wrongFlashCode?.let { map[it] = MapFeatureState.Wrong }
    return map
}
