@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.geoquiz.app.ui.borderhop

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.components.AdaptiveButtonRow
import com.geoquiz.app.ui.components.AdaptiveTrailingRow
import com.geoquiz.app.ui.components.AutocompleteSuggestion
import com.geoquiz.app.ui.components.CountryAutocompleteField
import com.geoquiz.app.ui.components.DEFAULT_MAX_SUGGESTIONS_HEIGHT
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.WindowLayout
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.rememberReducedMotion
import com.geoquiz.app.ui.map.MapCanvas
import com.geoquiz.app.ui.map.MapViewState
import com.geoquiz.app.ui.map.rememberMapScene
import com.geoquiz.app.ui.map.rememberMapViewState
import com.geoquiz.app.ui.quiz.components.FeedbackLine
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.quiz.feedback.FeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.FeedbackSignal
import com.geoquiz.app.ui.quiz.feedback.HapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.feedback.pulseOnCorrect
import com.geoquiz.app.ui.quiz.feedback.rememberFeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.feedback.shakeOnWrong
import com.geoquiz.app.ui.tapmap.MapLoadError
import com.geoquiz.app.ui.theme.geoColors

/**
 * Border hop's game screen (task P10-C, replacing the neighbour list of D45): a blank world map
 * (no labels; pan and zoom only, taps never answer) showing the start, the target and the route
 * so far, with the instructions, hops against par and the route as text above it, and the input
 * pinned at the bottom: a country name field with suggestions at Normal, a plain field at Hard,
 * four options at Easy. Reaching the target, Give up or Hard's strike limit navigates to Results
 * via [onFinished]. Backgrounding pauses the round like the other new modes; a paused overlay
 * with Resume is the only way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorderHopGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: BorderHopGameViewModel = hiltViewModel(),
    haptics: HapticFeedbackPlayer = rememberHapticFeedbackPlayer()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    val routeFrame by viewModel.routeFrame.collectAsStateWithLifecycle()
    val timerVisible by viewModel.timerVisible.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
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

    val vibrationEnabled by rememberUpdatedState(vibration)
    var feedbackSignal by remember { mutableStateOf(FeedbackSignal()) }
    LaunchedEffect(viewModel) {
        viewModel.feedbackEvents.collect { event ->
            if (vibrationEnabled) haptics.play(event)
            feedbackSignal = feedbackSignal.next(event)
        }
    }
    val feedbackMotion = rememberFeedbackMotion(feedbackSignal, rememberReducedMotion())

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
        // Same structure as Tap the map's Name all: the layout is read from the space before the
        // keyboard takes its share, so opening it never flips between one and two panes.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            val windowLayout = WindowLayout.of(maxWidth, maxHeight)
            Box(modifier = Modifier.fillMaxSize().imePadding()) {
                when (val state = uiState) {
                    is BorderHopUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is BorderHopUiState.Unavailable -> Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
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
                    is BorderHopUiState.Active -> {
                        val round = state.state
                        Box(modifier = Modifier.fillMaxSize()) {
                            BorderHopActiveContent(
                                state = round,
                                mapState = mapState,
                                routeFrame = routeFrame,
                                countryName = { code -> viewModel.countryFor(code)?.name ?: code },
                                timerVisible = timerVisible,
                                timerSeconds = timerSeconds,
                                actions = BorderHopActions(
                                    onInputChange = viewModel::onInputChange,
                                    onSubmit = viewModel::onSubmit,
                                    onSuggestionSelected = viewModel::onSuggestionSelected,
                                    onOptionSelected = viewModel::onOptionSelected,
                                    onUndo = viewModel::onUndo,
                                    onHint = viewModel::onHint,
                                    onGiveUp = { showGiveUpDialog = true }
                                ),
                                motion = feedbackMotion,
                                windowLayout = windowLayout,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (round.paused) Modifier.clearAndSetSemantics { } else Modifier)
                            )
                            if (round.paused) {
                                PausedOverlay(
                                    progressText = pluralStringResource(
                                        R.plurals.mode_border_hop_paused_progress, round.hopsUsed, round.hopsUsed
                                    ),
                                    onResume = viewModel::onResume
                                )
                            }
                        }
                    }
                }
            }
        }

        val active = (uiState as? BorderHopUiState.Active)?.state
        if (showGiveUpDialog && active != null) {
            val target = viewModel.countryFor(active.targetCode)?.name.orEmpty()
            AlertDialog(
                onDismissRequest = { showGiveUpDialog = false },
                title = { Text(stringResource(R.string.quiz_give_up_title)) },
                text = {
                    Text(
                        pluralStringResource(
                            R.plurals.mode_border_hop_give_up, active.hopsUsed, active.hopsUsed, active.parHops, target
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

/** Everything the active round's controls can do; grouped so [BorderHopActiveContent] stays readable. */
internal data class BorderHopActions(
    val onInputChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onSuggestionSelected: (String) -> Unit = {},
    val onOptionSelected: (String) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onHint: () -> Unit = {},
    val onGiveUp: () -> Unit = {}
)

/**
 * Stateless content of an active round (internal so screenshot tests can render it directly).
 * One pane (phone portrait): instructions on top (they scroll if the space is short, normally
 * capped so the map keeps [MAP_MIN_HEIGHT], but the map gives way first if even that doesn't fit
 * with the field and Submit, and the autocomplete list is capped to whatever room is actually
 * left, P10-R2 M2), the map filling the middle, the input or options pinned at the bottom. Two
 * panes (landscape, wide): the map at the start, instructions and input at the end.
 * [windowLayout], when given, decides one pane vs two (the screen measures it before the
 * keyboard opens); null measures here.
 */
@Composable
internal fun BorderHopActiveContent(
    state: BorderHopState,
    mapState: BorderHopMapState,
    routeFrame: BorderHopRouteFrame?,
    countryName: (String) -> String,
    timerVisible: Boolean,
    timerSeconds: Int,
    actions: BorderHopActions,
    modifier: Modifier = Modifier,
    motion: FeedbackMotion? = null,
    windowLayout: WindowLayout? = null
) {
    val featureStates = remember(state.path, state.targetCode, state.revealedHopCode, state.flashWrongCode) {
        borderHopFeatureStates(state)
    }
    val routeNames = state.path.map(countryName)
    val routeA11y = stringResource(
        R.string.border_hop_route_a11y,
        routeNames.joinToString(stringResource(R.string.border_hop_route_a11y_separator))
    )
    val mapDescription = stringResource(
        R.string.border_hop_map_description,
        countryName(state.startCode),
        countryName(state.targetCode),
        routeA11y
    )

    val map: @Composable (Modifier) -> Unit = { mapModifier ->
        BorderHopMap(
            mapState = mapState,
            routeFrame = routeFrame,
            featureStates = featureStates,
            description = mapDescription,
            modifier = mapModifier
        )
    }
    val details: @Composable (Modifier) -> Unit = { detailsModifier ->
        BorderHopDetails(
            state = state,
            countryName = countryName,
            routeNames = routeNames,
            routeA11y = routeA11y,
            timerVisible = timerVisible,
            timerSeconds = timerSeconds,
            actions = actions,
            modifier = detailsModifier
        )
    }
    val footer: @Composable (Dp, (Dp) -> Unit) -> Unit = { maxSuggestionsHeight, onSuggestionsHeightChanged ->
        BorderHopFooter(
            state = state,
            countryName = countryName,
            actions = actions,
            motion = motion,
            maxSuggestionsHeight = maxSuggestionsHeight,
            onSuggestionsHeightChanged = onSuggestionsHeightChanged
        )
    }

    BoxWithConstraints(modifier = modifier.padding(16.dp)) {
        val layout = windowLayout ?: WindowLayout.of(maxWidth, maxHeight)
        if (layout.useTwoPanes) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                map(Modifier.weight(1f).fillMaxHeight())
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    details(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()))
                    footer(DEFAULT_MAX_SUGGESTIONS_HEIGHT) {}
                }
            }
        } else {
            val density = LocalDensity.current
            var footerHeight by remember { mutableStateOf(0.dp) }
            var suggestionsHeight by remember { mutableStateOf(0.dp) }
            // The footer's height without its suggestion list (the field, Submit and feedback
            // line), inferred from the last frame's measurements, same one-frame lag as
            // footerHeight itself. The map gives way (down to no minimum at all) before this ever
            // loses room, and the list is then capped to whatever is left after it, so a long
            // suggestion list at a large font scale with the keyboard open can never push the
            // field or Submit off-screen (P10-R2 M2; never give BoxWithConstraints an intrinsic
            // measurement, so this stays plain arithmetic on measured sizes instead).
            val chromeHeight = (footerHeight - suggestionsHeight).coerceAtLeast(0.dp)
            val mapMinHeight = MAP_MIN_HEIGHT.coerceAtMost((maxHeight - chromeHeight - SECTION_GAP * 2).coerceAtLeast(0.dp))
            val detailsMax = (maxHeight - mapMinHeight - footerHeight - SECTION_GAP * 2).coerceAtLeast(0.dp)
            val maxSuggestionsHeight = (maxHeight - mapMinHeight - chromeHeight - SECTION_GAP * 2)
                .coerceAtLeast(0.dp)
                .coerceAtMost(DEFAULT_MAX_SUGGESTIONS_HEIGHT)
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                details(Modifier.fillMaxWidth().heightIn(max = detailsMax).verticalScroll(rememberScrollState()))
                map(Modifier.weight(1f).fillMaxWidth().heightIn(min = mapMinHeight))
                Box(modifier = Modifier.fillMaxWidth().onSizeChanged { footerHeight = with(density) { it.height.toDp() } }) {
                    footer(maxSuggestionsHeight) { suggestionsHeight = it }
                }
            }
        }
    }
}

/**
 * The world map: no labels, pan and zoom only (taps never answer, so Tap the map's dashed tap
 * zones are not drawn). The camera fits [routeFrame] whenever it changes (animated unless reduced
 * motion), except when the only change is a wrong guess's flash ending after the player has moved
 * the camera themselves: they may be looking at it, so it isn't yanked away. A viewport size
 * change (the keyboard opening or closing) always refits, without animation, so the route stays
 * framed rather than the camera falling back to the whole world.
 */
@Composable
private fun BorderHopMap(
    mapState: BorderHopMapState,
    routeFrame: BorderHopRouteFrame?,
    featureStates: Map<String, MapFeatureState>,
    description: String,
    modifier: Modifier = Modifier
) {
    val preset = RegionPreset.WORLD
    val scene = rememberMapScene(
        projection = preset.projection,
        coarse = mapState.coarse,
        detail = mapState.detail,
        tapZones = mapState.tapZones,
        panMarginFraction = preset.panMarginFraction
    )
    val viewState = rememberMapViewState()
    val reducedMotion = rememberReducedMotion()
    val paddingPx = with(LocalDensity.current) { FRAME_PADDING.toPx() }
    val tracker = remember { RouteFitTracker() }
    LaunchedEffect(routeFrame, viewState.isReady, scene?.contentBounds, viewState.viewportWidth, viewState.viewportHeight) {
        val frame = routeFrame ?: return@LaunchedEffect
        if (!viewState.isReady) return@LaunchedEffect
        val resized = tracker.viewportChanged(viewState)
        if (!resized && !tracker.shouldFit(frame, viewState)) return@LaunchedEffect
        viewState.fitTo(frame.rect, paddingPx, animate = !reducedMotion && !resized)
        tracker.fitted(frame, viewState)
    }

    Box(modifier = modifier) {
        when {
            scene != null -> MapCanvas(
                scene = scene,
                state = viewState,
                modifier = Modifier.fillMaxSize(),
                featureStates = featureStates,
                showTapZones = false,
                onFeatureTap = null,
                mapContentDescription = description
            )
            mapState.loadFailed -> MapLoadError()
            else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Remembers the last auto-fit, so a fit never undoes the player's own pan for a passing flash. */
private class RouteFitTracker {
    private var lastFrame: BorderHopRouteFrame? = null
    private var lastCamera: MapViewState.Camera? = null
    private var lastViewportWidth = 0f
    private var lastViewportHeight = 0f

    /** True when the map has been resized (e.g. by the keyboard) since the last fit. */
    fun viewportChanged(viewState: MapViewState): Boolean =
        lastFrame != null && (viewState.viewportWidth != lastViewportWidth || viewState.viewportHeight != lastViewportHeight)

    fun shouldFit(frame: BorderHopRouteFrame, viewState: MapViewState): Boolean {
        val previous = lastFrame ?: return true
        val camera = lastCamera ?: return true
        // Only the player's own pan/zoom counts: the map resets its camera itself when the detail
        // scene replaces the coarse one, and that reset must be refitted, not respected.
        val cameraChanged = camera.zoom != viewState.zoom || camera.centreX != viewState.centreX || camera.centreY != viewState.centreY
        val playerMoved = cameraChanged && viewState.userMoved
        val onlyFlashEnded = frame.routeCodes == previous.routeCodes && previous.codes.containsAll(frame.codes)
        return !(playerMoved && onlyFlashEnded)
    }

    fun fitted(frame: BorderHopRouteFrame, viewState: MapViewState) {
        lastFrame = frame
        lastCamera = MapViewState.Camera(viewState.zoom, viewState.centreX, viewState.centreY)
        lastViewportWidth = viewState.viewportWidth
        lastViewportHeight = viewState.viewportHeight
    }
}

/** Instructions, hops against par, the route as text, the hint, and Undo/Hint/Give up. */
@Composable
private fun BorderHopDetails(
    state: BorderHopState,
    countryName: (String) -> String,
    routeNames: List<String>,
    routeA11y: String,
    timerVisible: Boolean,
    timerSeconds: Int,
    actions: BorderHopActions,
    modifier: Modifier = Modifier
) {
    val current = countryName(state.currentCode)
    val target = countryName(state.targetCode)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.border_hop_instruction_goal, countryName(state.startCode), target),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        if (state.isComplete) {
            OutcomeLine(state = state, targetName = target)
        } else {
            val next = if (state.difficulty == Difficulty.EASY) R.string.border_hop_instruction_next_easy else R.string.border_hop_instruction_next
            Text(text = stringResource(next, current), style = MaterialTheme.typography.bodyLarge)
        }
        AdaptiveTrailingRow(
            main = {
                Text(
                    text = pluralStringResource(R.plurals.border_hop_progress, state.hopsUsed, state.hopsUsed, state.parHops),
                    style = MaterialTheme.typography.titleSmall
                )
            },
            trailing = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DifficultyLabel(difficulty = state.difficulty)
                    state.strikesLeft?.let { left ->
                        Text(
                            text = pluralStringResource(R.plurals.border_hop_wrong_guesses_left, left, left),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (timerVisible) TimerDisplay(elapsedSeconds = timerSeconds, timerSeconds = null)
                }
            }
        )
        Text(
            text = stringResource(
                R.string.border_hop_route_label,
                routeNames.joinToString(stringResource(R.string.border_hop_route_separator))
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { contentDescription = routeA11y }
        )
        state.revealedHopCode?.let { hop ->
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(inlineIconSize(18.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.border_hop_hint_revealed, countryName(hop), current),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (!state.isComplete) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = actions.onUndo, enabled = state.canUndo, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(inlineIconSize(18.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.border_hop_undo))
                }
                // Task 8.4: disabled for the rest of the round once the tier's hint cap is
                // reached; hidden at Easy, whose cap is 0 (D62).
                val maxHints = HintCosts.maxHintsPerRound(state.difficulty)
                if (maxHints > 0) {
                    OutlinedButton(
                        onClick = actions.onHint,
                        enabled = state.acceptsInput && state.revealedHopCode == null &&
                            state.hintsUsed.size < maxHints && state.hintAvailable,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(inlineIconSize(18.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.border_hop_hint, HintCosts.cost(HintType.SHOW_VALID_HOP)))
                    }
                }
                OutlinedButton(onClick = actions.onGiveUp, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(inlineIconSize(18.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.quiz_give_up))
                }
            }
        }
    }
}

@Composable
private fun OutcomeLine(state: BorderHopState, targetName: String) {
    val reached = state.reachedTarget
    val text = when {
        reached -> stringResource(R.string.border_hop_reached, targetName, state.hopsUsed, state.parHops)
        state.outOfGuesses -> stringResource(R.string.border_hop_out_of_guesses_outcome, targetName)
        else -> stringResource(R.string.border_hop_gave_up_outcome, targetName)
    }
    FeedbackLine(
        icon = if (reached) Icons.Filled.Check else Icons.Filled.Close,
        text = text,
        color = if (reached) MaterialTheme.geoColors.correct else MaterialTheme.geoColors.wrong,
        style = MaterialTheme.typography.titleSmall,
        iconSize = 20.dp,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    )
}

/**
 * Pinned at the bottom: the feedback line, then the field and Submit (Normal/Hard) or the options
 * (Easy). [maxSuggestionsHeight] and [onSuggestionsHeightChanged] let the one-pane layout cap the
 * autocomplete list to the space actually available and learn how much it took (P10-R2 M2).
 */
@Composable
private fun BorderHopFooter(
    state: BorderHopState,
    countryName: (String) -> String,
    actions: BorderHopActions,
    motion: FeedbackMotion?,
    maxSuggestionsHeight: Dp = DEFAULT_MAX_SUGGESTIONS_HEIGHT,
    onSuggestionsHeightChanged: (Dp) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.difficulty == Difficulty.EASY) {
            if (!state.isComplete) {
                state.options.forEach { code ->
                    BorderHopOption(
                        label = countryName(code),
                        look = when {
                            code == state.correctPick -> OptionLook.CORRECT
                            code in state.wrongPicks -> OptionLook.WRONG
                            state.correctPick != null -> OptionLook.DIMMED
                            else -> OptionLook.NEUTRAL
                        },
                        enabled = state.acceptsInput && code !in state.wrongPicks,
                        onClick = { actions.onOptionSelected(code) }
                    )
                }
            }
            FeedbackRow(feedback = state.feedback, countryName = countryName, motion = motion)
        } else {
            FeedbackRow(feedback = state.feedback, countryName = countryName, motion = motion)
            if (!state.isComplete) {
                CountryAutocompleteField(
                    value = state.input,
                    onValueChange = actions.onInputChange,
                    suggestions = state.suggestions.map { AutocompleteSuggestion(it, countryName(it)) },
                    onSuggestionSelected = actions.onSuggestionSelected,
                    onSubmit = actions.onSubmit,
                    label = stringResource(R.string.mode_border_hop_input_label),
                    enabled = state.acceptsInput,
                    isError = state.feedback is BorderHopFeedback.NotNeighbour,
                    fieldModifier = Modifier.shakeOnWrong(motion),
                    maxSuggestionsHeight = maxSuggestionsHeight,
                    onSuggestionsHeightChanged = onSuggestionsHeightChanged
                )
                AdaptiveButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = actions.onSubmit,
                        enabled = state.acceptsInput && state.input.isNotBlank(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.quiz_submit)) }
                }
            }
        }
    }
}

/** The latest guess's result, in a polite live region; every state has an icon, never colour alone. */
@Composable
private fun FeedbackRow(feedback: BorderHopFeedback?, countryName: (String) -> String, motion: FeedbackMotion?) {
    val geo = MaterialTheme.geoColors
    val style = MaterialTheme.typography.bodyMedium
    Box(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        when (feedback) {
            null -> Unit
            is BorderHopFeedback.Hopped -> FeedbackLine(
                icon = Icons.Filled.Check,
                text = stringResource(R.string.border_hop_feedback_hopped, countryName(feedback.code)),
                color = geo.correct,
                style = style,
                iconSize = FEEDBACK_ICON_SIZE,
                modifier = Modifier.pulseOnCorrect(motion)
            )
            is BorderHopFeedback.NotNeighbour -> FeedbackLine(
                icon = Icons.Filled.Close,
                text = stringResource(R.string.border_hop_feedback_not_neighbour, countryName(feedback.code), countryName(feedback.fromCode)),
                color = geo.wrong,
                style = style,
                iconSize = FEEDBACK_ICON_SIZE
            )
            is BorderHopFeedback.AlreadyOnRoute -> FeedbackLine(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.border_hop_feedback_already_on_route, countryName(feedback.code)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = style,
                iconSize = FEEDBACK_ICON_SIZE
            )
            is BorderHopFeedback.NotRecognised -> FeedbackLine(
                icon = Icons.Outlined.Spellcheck,
                text = stringResource(R.string.border_hop_feedback_not_recognised),
                color = geo.nearMiss,
                style = style,
                iconSize = FEEDBACK_ICON_SIZE
            )
        }
    }
}

private enum class OptionLook { NEUTRAL, CORRECT, WRONG, DIMMED }

/** One Easy option: right = blue + tick, wrong = orange + cross (disabled), never colour alone. */
@Composable
private fun BorderHopOption(label: String, look: OptionLook, enabled: Boolean, onClick: () -> Unit) {
    val geo = MaterialTheme.geoColors
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    val border: BorderStroke
    when (look) {
        OptionLook.NEUTRAL -> {
            container = Color.Transparent
            content = scheme.onSurface
            border = BorderStroke(1.dp, scheme.outline)
        }
        OptionLook.CORRECT -> {
            container = geo.correctContainer
            content = geo.onCorrectContainer
            border = BorderStroke(2.dp, geo.correct)
        }
        OptionLook.WRONG -> {
            container = geo.wrongContainer
            content = geo.onWrongContainer
            border = BorderStroke(2.dp, geo.wrong)
        }
        OptionLook.DIMMED -> {
            container = Color.Transparent
            content = scheme.onSurface.copy(alpha = 0.38f)
            border = BorderStroke(1.dp, scheme.outlineVariant)
        }
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        border = border,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            when (look) {
                OptionLook.CORRECT -> ResultIcon(Icons.Filled.Check, stringResource(R.string.choice_option_correct), geo.correct)
                OptionLook.WRONG -> ResultIcon(Icons.Filled.Close, stringResource(R.string.choice_option_wrong), geo.wrong)
                OptionLook.NEUTRAL, OptionLook.DIMMED -> Unit
            }
        }
    }
}

@Composable
private fun ResultIcon(icon: ImageVector, description: String, tint: Color) {
    Spacer(modifier = Modifier.width(8.dp))
    Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(inlineIconSize(24.dp)))
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
                Icons.Filled.PauseCircle,
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

/**
 * A floor on the map's height in one pane, so it never disappears behind long text or the
 * keyboard - except when even that much space isn't actually available, in which case the map
 * gives way first (down to no minimum at all) rather than push the field or Submit off-screen
 * (P10-R2 M2; see the one-pane branch of [BorderHopActiveContent]).
 */
private val MAP_MIN_HEIGHT = 140.dp
private val SECTION_GAP = 8.dp
private val FRAME_PADDING = 24.dp
private val FEEDBACK_ICON_SIZE = 16.dp
