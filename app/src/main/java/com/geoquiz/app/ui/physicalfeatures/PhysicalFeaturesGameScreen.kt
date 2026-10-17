package com.geoquiz.app.ui.physicalfeatures

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.mode.physicalfeatures.physicalFeatureFrame
import com.geoquiz.app.domain.mode.physicalfeatures.physicalFeatureLayerIds
import com.geoquiz.app.domain.mode.physicalfeatures.physicalFeatureOverlayLayer
import com.geoquiz.app.domain.mode.physicalfeatures.physicalFeatureProjection
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.components.AdaptiveTrailingRow
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.map.MapCanvas
import com.geoquiz.app.ui.map.rememberIsTouchExplorationEnabled
import com.geoquiz.app.ui.map.rememberMapScene
import com.geoquiz.app.ui.map.rememberMapViewState
import com.geoquiz.app.ui.quiz.components.MultipleChoicePanel
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer
import com.geoquiz.app.ui.theme.geoColors

/** A throwaway centre used only while there is no current feature to frame (round not loaded
 * yet, or just finished): [rememberMapScene] always needs a projection, but with no `coarse`
 * layer it returns a null scene regardless of which one. */
private val NO_TARGET_PROJECTION = LambertAzimuthalEqualAreaProjection(0.0, 0.0)

/** The map pane never shrinks below this (task 7.2 review fix C1), same floor as Tap the
 * map/US-CA's own `MAP_MIN_HEIGHT`. */
private val MAP_MIN_HEIGHT = 160.dp
private val SECTION_GAP = 8.dp

/**
 * Rivers, mountains and lakes' game screen (task 7.2, D51), registered at a destination the
 * lead's integration pass still needs to add (see `PhysicalFeaturesGameMode`'s class doc): one
 * feature highlighted at a time on the map, typed (Normal/Hard) or four choices (Easy,
 * [MultipleChoicePanel]). Finishing (every feature answered, Give up, or a Hard strike-out) ends
 * the round via [onFinished]. Backgrounding pauses the round, like every other new mode; a
 * paused overlay with Resume is the only way back.
 *
 * Simplification (task 7.2 report): unlike Country silhouettes/Tap the map this screen does not
 * yet have the two-pane (wide screen) layout those use (D28) - review fix C1 gave it their other
 * shape instead (map floor + weight(1f) in a non-scrolling column, scrollable details, pinned
 * input/controls), just always in the "one pane" arrangement. Revisit two-pane in a follow-up
 * polish pass if this mode ships; this should not block a first prototype (section 2's "prototype
 * first" principle).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalFeaturesGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PhysicalFeaturesGameViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    val timerVisible by viewModel.timerVisible.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
    val vibrationEnabled by rememberUpdatedState(vibration)
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    val haptics = rememberHapticFeedbackPlayer()

    LaunchedEffect(viewModel) {
        viewModel.feedbackEvents.collect { event -> if (vibrationEnabled) haptics.play(event) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    LaunchedEffect(completion) {
        val current = completion
        if (current != null && current.step == PhysicalFeaturesCompletion.Step.NAVIGATE) {
            onFinished(current.resultId)
            viewModel.onNavigatedToResults()
        }
    }

    var showGiveUpDialog by remember { mutableStateOf(false) }
    val roundInProgress = (uiState as? PhysicalFeaturesUiState.Active)?.state?.isComplete == false
    BackHandler(enabled = roundInProgress) { showGiveUpDialog = true }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_physical_features_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundInProgress) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is PhysicalFeaturesUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is PhysicalFeaturesUiState.Unavailable -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.physical_features_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) { Text(stringResource(R.string.action_back)) }
                }
            }
            is PhysicalFeaturesUiState.Active -> {
                val round = state.state
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    PhysicalFeaturesActiveContent(
                        round = round,
                        mapState = mapState,
                        timerVisible = timerVisible,
                        timerSeconds = timerSeconds,
                        onInputChange = viewModel::onInputChange,
                        onSubmit = viewModel::onSubmit,
                        onChoiceSelected = viewModel::onChoiceSelected,
                        onPass = viewModel::onPass,
                        onGiveUp = { showGiveUpDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (round.paused) {
                        PhysicalFeaturesPausedOverlay(
                            progressText = stringResource(
                                R.string.mode_physical_features_paused_progress, round.answeredIds.size, round.features.size
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
                                    R.string.mode_physical_features_give_up,
                                    round.answeredIds.size,
                                    round.features.size
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
    }
}

/**
 * [BoxWithConstraints] so the map pane can keep a floor ([MAP_MIN_HEIGHT]) while the scrollable
 * details above the pinned input/controls area shrink first on a short screen (task 7.2 review
 * fix C1): the map used to sit in a [androidx.compose.foundation.layout.heightIn] `Box` inside a
 * [androidx.compose.foundation.verticalScroll]ing column, which gives that `Box` an *unlimited*
 * maximum height - [MapCanvas]'s `Canvas` (a `Spacer` with draw logic) ended up 0 px tall and
 * nothing ever appeared. See [TapMapActiveContent][com.geoquiz.app.ui.tapmap.TapMapActiveContent]
 * for the pattern this follows.
 */
@Composable
private fun PhysicalFeaturesActiveContent(
    round: PhysicalFeaturesRoundState,
    mapState: PhysicalFeaturesMapState,
    timerVisible: Boolean,
    timerSeconds: Int,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onChoiceSelected: (String) -> Unit,
    onPass: () -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTyped = round.difficulty != Difficulty.EASY
    val target = round.current
    val layer = target?.let { mapState.layersById[it.layerId] }
    val highlightIds = if (target != null && layer != null) physicalFeatureLayerIds(target, layer) else emptySet()
    // Task 7.2 review fix C2: a synthetic layer holding only the target's own part(s), never the
    // whole (possibly hundreds-of-features) layer it came from, composited over admin0_50m's
    // real country context when that base layer loaded; falls back to the target alone (no base
    // context, still correctly drawn - never the pre-fix bug) if it didn't.
    // remember()'d (screenshot-wave review): GeoLayer/GeoFeature have no custom equals(), so a
    // plain val here would be a structurally-identical but reference-distinct object on every
    // recomposition (this composable recomposes every second while timerSeconds ticks) - both
    // MapPathCache's key and rememberMapLayerPaths' own remember() key it against would treat
    // that as a brand-new layer and rebuild its paths from scratch every second.
    val overlayLayer = remember(target, layer, highlightIds) {
        if (target != null && layer != null) physicalFeatureOverlayLayer(layer, highlightIds) else null
    }
    val baseLayer = mapState.baseLayer
    val projection = target?.let { physicalFeatureProjection(it) } ?: NO_TARGET_PROJECTION
    val frame = target?.let { physicalFeatureFrame(projection, it) }
    val scene = if (target != null && layer != null) {
        rememberMapScene(
            projection = projection,
            coarse = baseLayer ?: overlayLayer,
            overlay = if (baseLayer != null) overlayLayer else null,
            bounds = frame,
            // An empty (not null) member set: every admin0_50m feature draws as non-playable
            // context (`mapLandInactive`), never this mode's own "answerable" layer - there is no
            // country to answer here. Null (no exclusion) when there is no base layer to draw.
            members = if (baseLayer != null) emptySet() else null,
            panMarginFraction = 0.15f
        )
    } else {
        null
    }
    val mapViewState = rememberMapViewState()
    val featureStates = remember(highlightIds) { highlightIds.associateWith { MapFeatureState.Highlighted } }
    val controlsEnabled = !round.isComplete && !round.isAdvancing && !round.paused
    val giveUpEnabled = !round.paused
    // Task 7.2 review fix H2: with no map list/clue fallback for a one-off highlighted feature,
    // a TalkBack user typing Normal/Hard has no way to tell which feature is being asked about at
    // all; a continent clue (never the feature's own name or countries - D51's `countries` field
    // is cca3 codes with no repository lookup wired into this screen, deferred) is shown whenever
    // touch exploration is on. Not needed at Easy: the four named choices are already fully
    // accessible without seeing the map.
    val touchExplorationEnabled = rememberIsTouchExplorationEnabled()
    val density = LocalDensity.current
    var controlsHeight by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(modifier = modifier.imePadding().padding(16.dp)) {
        val detailsMax = (maxHeight - MAP_MIN_HEIGHT - controlsHeight - SECTION_GAP * 2).coerceAtLeast(0.dp)
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = MAP_MIN_HEIGHT)) {
                when {
                    scene != null -> MapCanvas(
                        scene = scene,
                        state = mapViewState,
                        modifier = Modifier.fillMaxSize(),
                        featureStates = featureStates,
                        onFeatureTap = null,
                        doubleTapToZoom = false,
                        // A plain, non-revealing description (D39's pattern: this map is decorative
                        // for TalkBack, never announcing "found"/"wrong" counts that don't apply to
                        // a single highlighted feature, and never naming it at Easy).
                        mapContentDescription = stringResource(R.string.physical_features_map_a11y)
                    )
                    // Task 7.2 review fix L1: per-layer, not round-wide, so a round mixing
                    // feature types shows this only for the questions whose own layer failed.
                    target != null && target.layerId in mapState.failedLayerIds -> Text(
                        text = stringResource(R.string.physical_features_map_error),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = detailsMax)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhysicalFeaturesHeader(
                    current = round.progress,
                    total = round.features.size,
                    difficulty = round.difficulty,
                    incorrectGuesses = round.incorrectGuesses,
                    timerVisible = timerVisible,
                    timerSeconds = timerSeconds,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = pluralStringResource(R.plurals.a11y_progress_physical_features, round.features.size, round.answeredIds.size, round.features.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                if (round.difficulty == Difficulty.EASY) {
                    Text(
                        text = stringResource(R.string.physical_features_choice_heading),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.physical_features_prompt_heading),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (touchExplorationEnabled && target != null) {
                        Text(
                            text = stringResource(R.string.physical_features_clue, target.continent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PhysicalFeaturesFeedbackBanner(feedback = round.feedback, feedbackToken = round.feedbackToken, modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.fillMaxWidth().onSizeChanged { controlsHeight = with(density) { it.height.toDp() } }) {
                PhysicalFeaturesControls(
                    round = round,
                    isTyped = isTyped,
                    controlsEnabled = controlsEnabled,
                    giveUpEnabled = giveUpEnabled,
                    onInputChange = onInputChange,
                    onSubmit = onSubmit,
                    onChoiceSelected = onChoiceSelected,
                    onPass = onPass,
                    onGiveUp = onGiveUp
                )
            }
        }
    }
}

/** The typed field (Normal/Hard) or Easy's four choices, then Pass (typed tiers only, task 7.2
 * review fix H3) and Give up. Pinned below the scrollable details, same shape as every other map
 * mode's controls row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhysicalFeaturesControls(
    round: PhysicalFeaturesRoundState,
    isTyped: Boolean,
    controlsEnabled: Boolean,
    giveUpEnabled: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onChoiceSelected: (String) -> Unit,
    onPass: () -> Unit,
    onGiveUp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isTyped) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = round.currentInput,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.mode_physical_features_input_label)) },
                    singleLine = true,
                    enabled = controlsEnabled,
                    readOnly = round.isAdvancing,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() })
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSubmit, enabled = controlsEnabled && round.currentInput.isNotBlank()) {
                    Text(stringResource(R.string.quiz_submit))
                }
            }
        } else {
            round.choice?.let { choice ->
                MultipleChoicePanel(
                    question = choice,
                    feedback = round.choiceFeedback,
                    enabled = !round.isAdvancing && !round.paused,
                    onSelect = onChoiceSelected,
                    modifier = Modifier.fillMaxWidth(),
                    showPrompt = false
                )
            }
        }

        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isTyped) {
                OutlinedButton(onClick = onPass, enabled = controlsEnabled) {
                    Text(stringResource(R.string.physical_features_pass))
                }
            }
            OutlinedButton(onClick = onGiveUp, enabled = giveUpEnabled) {
                Text(stringResource(R.string.quiz_give_up))
            }
        }
    }
}

@Composable
private fun PhysicalFeaturesHeader(
    current: Int,
    total: Int,
    difficulty: Difficulty,
    incorrectGuesses: Int,
    timerVisible: Boolean,
    timerSeconds: Int,
    modifier: Modifier = Modifier
) {
    // Task 7.2 review fix M2 (D29): AdaptiveTrailingRow wraps the difficulty/timer group onto
    // its own line instead of squeezing the progress text to zero width at 200% font scale, the
    // same component Tap the map/US-CA's own status rows use.
    AdaptiveTrailingRow(
        modifier = modifier,
        main = {
            Text(
                text = "${(current + 1).coerceAtMost(total.coerceAtLeast(1))} / ${total.coerceAtLeast(1)}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Task 9.2 review fix: Hard ends the round at a strike limit (same as every
                // other map mode) but, unlike Tap the map/US-CA, never showed the count - same
                // status item, so this never relies on colour alone either.
                if (difficulty == Difficulty.HARD) {
                    val limit = Difficulty.HARD.strikeLimit ?: HARD_STRIKE_LIMIT_FALLBACK
                    val shown = incorrectGuesses.coerceAtMost(limit)
                    val description = pluralStringResource(R.plurals.a11y_hard_mode_strikes, limit, shown, limit)
                    Text(
                        text = stringResource(R.string.mode_tap_map_strikes_short, shown, limit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.geoColors.wrong,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
                    )
                }
                DifficultyLabel(difficulty = difficulty)
                if (timerVisible) {
                    TimerDisplay(elapsedSeconds = timerSeconds, timerSeconds = null)
                }
            }
        }
    )
}

/** Fallback only if [Difficulty.HARD]'s own [Difficulty.strikeLimit] is ever cleared to null. */
private const val HARD_STRIKE_LIMIT_FALLBACK = 3

@Composable
private fun PhysicalFeaturesFeedbackBanner(
    feedback: PhysicalFeatureFeedback,
    feedbackToken: Int,
    modifier: Modifier = Modifier
) {
    val text = when (feedback) {
        PhysicalFeatureFeedback.None -> null
        is PhysicalFeatureFeedback.Correct -> stringResource(R.string.physical_features_correct_feedback, feedback.name)
        PhysicalFeatureFeedback.NearMiss -> stringResource(R.string.answer_near_miss)
        PhysicalFeatureFeedback.Incorrect -> stringResource(R.string.physical_features_incorrect_feedback)
        PhysicalFeatureFeedback.AlreadyAnswered -> stringResource(R.string.answer_already_answered)
        is PhysicalFeatureFeedback.Revealed -> stringResource(R.string.physical_features_revealed_feedback, feedback.name)
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun PhysicalFeaturesPausedOverlay(progressText: String, onResume: () -> Unit) {
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
            Text(progressText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onResume) { Text(stringResource(R.string.quiz_resume)) }
        }
    }
}
