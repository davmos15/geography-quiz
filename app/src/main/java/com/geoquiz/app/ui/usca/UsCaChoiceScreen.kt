package com.geoquiz.app.ui.usca

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.map.MapCanvas
import com.geoquiz.app.ui.map.mapContentDescription
import com.geoquiz.app.ui.map.rememberMapScene
import com.geoquiz.app.ui.map.rememberMapViewState
import com.geoquiz.app.ui.quiz.components.MultipleChoicePanel
import com.geoquiz.app.ui.quiz.feedback.HapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer

/**
 * Multiple choice's game screen (task 6.2): the target highlighted on the map (Easy accessibility
 * follows D39's pattern: no map/list toggle - a list would mark the highlighted answer for
 * TalkBack - and no Hint/Skip, since the four names are the help) with four name options below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsCaChoiceScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: UsCaChoiceViewModel = hiltViewModel(),
    hapticFeedbackPlayer: HapticFeedbackPlayer = rememberHapticFeedbackPlayer()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    var showGiveUpDialog by remember { mutableStateOf(false) }

    val activeState = (uiState as? UsCaChoiceUiState.Active)?.state
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
            is UsCaChoiceUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UsCaChoiceUiState.Unavailable -> UsCaUnavailable(padding, onNavigateBack)
            is UsCaChoiceUiState.Active -> Box(Modifier.fillMaxSize().padding(padding)) {
                UsCaChoiceContent(
                    state = state.state,
                    mapState = mapState,
                    onChoiceSelected = viewModel::onChoiceSelected,
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
            val active = uiState as? UsCaChoiceUiState.Active
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
internal fun UsCaChoiceContent(
    state: QuizState,
    mapState: UsCaRegionsMapState,
    onChoiceSelected: (String) -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val found = state.answeredCountries.size
    val total = state.quiz.countries.size
    val featureStates = remember(state) {
        val map = HashMap<String, MapFeatureState>(state.answeredCountries.size + state.missedCountries.size)
        state.answeredCountries.forEach { map[it] = MapFeatureState.Found }
        state.missedCountries.forEach { map[it] = MapFeatureState.Wrong }
        state.choice?.targetCode?.let { map[it] = MapFeatureState.Highlighted }
        map
    }
    val scene = rememberMapScene(
        projection = US_CA_PROJECTION,
        coarse = mapState.layer,
        bounds = mapState.frame,
        members = state.quiz.countries.mapTo(HashSet()) { it.code },
        panMarginFraction = 0.2f
    )
    val mapViewState = rememberMapViewState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = pluralStringResource(R.plurals.a11y_progress_us_ca_regions, total, found, total),
            style = MaterialTheme.typography.bodyMedium,
            // Code review (task 6.2): the heading below is static text ("Which one is
            // highlighted?") that never changes between questions, so a liveRegion on it alone
            // never re-announces anything after the first question. This progress count does
            // change every question (found/total), so the live region belongs here instead -
            // matching how the Tap variant's prompt text (which also changes per round) carries
            // its own liveRegion.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        // D39's pattern: the map is decorative for TalkBack (no toggle, no list - a list would
        // mark the highlighted answer); this heading plus the four names below are the only
        // TalkBack path, matching D41 (no hints at this tier; the names are the help).
        Text(
            text = stringResource(R.string.us_ca_regions_choice_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 160.dp)) {
            when {
                scene != null -> MapCanvas(
                    scene = scene,
                    state = mapViewState,
                    modifier = Modifier.fillMaxSize(),
                    featureStates = featureStates,
                    onFeatureTap = null,
                    doubleTapToZoom = false,
                    mapContentDescription = mapContentDescription(
                        res = com.geoquiz.app.ui.components.a11yResources(),
                        regionLabel = state.quiz.category.displayName,
                        featureStates = featureStates,
                        total = total
                    )
                )
                mapState.loadFailed -> UsCaMapLoadError()
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }

        state.choice?.let { choice ->
            MultipleChoicePanel(
                question = choice,
                feedback = state.choiceFeedback,
                enabled = true,
                onSelect = onChoiceSelected,
                showPrompt = false
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onGiveUp, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.quiz_give_up))
            }
        }
    }
}
