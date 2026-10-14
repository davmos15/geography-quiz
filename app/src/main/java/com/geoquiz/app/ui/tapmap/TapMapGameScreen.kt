package com.geoquiz.app.ui.tapmap

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.ui.components.AdaptiveButtonRow
import com.geoquiz.app.ui.components.AdaptiveTrailingRow
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.WindowLayout
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
import com.geoquiz.app.ui.quiz.components.MultipleChoicePanel
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.quiz.feedback.HapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer
import com.geoquiz.app.ui.theme.geoColors

/**
 * Tap the map's game screen (task 5.2, review fixes 5.2a). Normal and Hard answer by tapping the
 * map (or, with TalkBack on, selecting a row in [MapListAlternative]); Easy shows the target
 * highlighted and four name options ([MultipleChoicePanel], `showPrompt = false`), with no
 * map/list toggle and no list (D39: a list would mark the highlighted answer for TalkBack).
 * Finishing navigates to Results exactly like the classic quiz, popping this screen off the back
 * stack. Backgrounding pauses, exactly like the classic quiz ([LifecycleEventEffect]); back
 * (system or the top bar's) during an active round asks before giving up, the same dialog as the
 * Give up button (D37/D39).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapMapGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TapMapGameViewModel = hiltViewModel(),
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

    val activeState = (uiState as? TapMapUiState.Active)?.state
    val roundActive = activeState != null && !activeState.quizState.isComplete

    LaunchedEffect(completion) {
        val current = completion
        if (current != null && current.step == TapMapCompletion.Step.NAVIGATE) {
            onFinished(current.resultId)
            viewModel.onNavigatedToResults()
        }
    }
    LaunchedEffect(vibration) {
        if (vibration) viewModel.feedbackEvents.collect { hapticFeedbackPlayer.play(it) }
    }

    // D37/D39: back (system or the top bar's) during an active round asks before giving up,
    // instead of leaving straight away.
    BackHandler(enabled = roundActive) { showGiveUpDialog = true }

    // Pauses on backgrounding, exactly like the classic quiz (QuizScreen): rotation does not call
    // this (isChangingConfigurations is skipped).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    val touchExplorationEnabled = rememberIsTouchExplorationEnabled()
    var viewMode by rememberSaveable {
        mutableStateOf(if (touchExplorationEnabled) MapViewMode.List else MapViewMode.Map)
    }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_tap_map_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundActive) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    // D39: no toggle at Easy (there is no list to switch to).
                    if (uiState is TapMapUiState.Active && difficulty != Difficulty.EASY) {
                        MapOrListToggle(mode = viewMode, onModeChange = { viewMode = it })
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is TapMapUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is TapMapUiState.Active -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                TapMapActiveContent(
                    state = state.state,
                    mapState = mapState,
                    difficulty = difficulty,
                    timerVisible = timerVisible,
                    timerSeconds = timerSeconds,
                    viewMode = viewMode,
                    onFeatureTap = viewModel::onFeatureTap,
                    onListSelect = { code ->
                        viewModel.onFeatureTap(MapHit(featureId = code, isPlayable = true, viaTapZone = false))
                    },
                    onChoiceSelected = viewModel::onChoiceSelected,
                    onSkip = viewModel::onSkip,
                    onHint = viewModel::onHint,
                    onGiveUp = { showGiveUpDialog = true },
                    modifier = Modifier
                        .fillMaxSize()
                        // While paused, only the pause overlay is reachable by TalkBack.
                        .then(if (state.state.quizState.isPaused) Modifier.clearAndSetSemantics { } else Modifier)
                )

                if (state.state.quizState.isPaused) {
                    TapMapPausedOverlay(
                        answeredCount = state.state.quizState.answeredCountries.size,
                        totalCount = state.state.quizState.quiz.countries.size,
                        pausedProgressRes = viewModel.modeSpec.labels.pausedProgress,
                        onResume = viewModel::togglePause
                    )
                }
            }
        }

        if (showGiveUpDialog) {
            val active = uiState as? TapMapUiState.Active
            AlertDialog(
                onDismissRequest = { showGiveUpDialog = false },
                title = { Text(stringResource(R.string.quiz_give_up_title)) },
                text = {
                    Text(
                        stringResource(
                            viewModel.modeSpec.labels.giveUpMessage,
                            active?.state?.quizState?.answeredCountries?.size ?: 0,
                            active?.state?.quizState?.quiz?.countries?.size ?: 0
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

/**
 * Matches the classic quiz's pause overlay ([com.geoquiz.app.ui.quiz.QuizScreen]): the only way
 * back in is [onResume] (S6).
 */
@Composable
private fun TapMapPausedOverlay(
    answeredCount: Int,
    totalCount: Int,
    pausedProgressRes: Int,
    onResume: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        // Scrolls if large text makes it taller than the screen.
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
            Text(
                stringResource(pausedProgressRes, answeredCount, totalCount),
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

/**
 * Internal (not private) so [TapMapActiveContentUiTest] can render it directly. [WindowLayout]
 * (S5): two panes (map at the start, a scrollable controls column at the end) on tablets,
 * landscape tablets and landscape phones; one pane (map above, controls below) otherwise, where
 * the map keeps a floor on its height ([MAP_MIN_HEIGHT]) and the controls column scrolls instead
 * of squeezing it on a short screen.
 */
@Composable
internal fun TapMapActiveContent(
    state: TapMapGameState,
    mapState: TapMapMapState,
    difficulty: Difficulty,
    timerVisible: Boolean,
    timerSeconds: Int,
    viewMode: MapViewMode,
    onFeatureTap: (MapHit?) -> Unit,
    onListSelect: (String) -> Unit,
    onChoiceSelected: (String) -> Unit,
    onSkip: () -> Unit,
    onHint: () -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qs = state.quizState
    val isEasy = difficulty == Difficulty.EASY
    val countriesByCode = remember(qs.quiz.countries) { qs.quiz.countries.associateBy { it.code } }
    val target = state.targetCode?.let { countriesByCode[it] }
    val featureStates = remember(state, isEasy) { tapMapFeatureStates(state, isEasy) }

    val controlsRow: @Composable () -> Unit = {
        // D39: Hint and Skip are hidden at Easy (nothing to skip to answer by picking a name;
        // the subregion clue already stands in for a hint).
        ControlsRow(isEasy = isEasy, hintUsed = state.hintUsedThisPrompt, onSkip = onSkip, onHint = onHint, onGiveUp = onGiveUp)
    }
    val details: @Composable (Modifier) -> Unit = { detailsModifier ->
        ControlsBlock(
            state = state,
            difficulty = difficulty,
            timerVisible = timerVisible,
            timerSeconds = timerSeconds,
            target = target,
            onChoiceSelected = onChoiceSelected,
            modifier = detailsModifier
        )
    }

    BoxWithConstraints(modifier = modifier.padding(16.dp)) {
        val windowLayout = WindowLayout.of(maxWidth, maxHeight)
        if (windowLayout.useTwoPanes) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MapArea(
                    mapState = mapState,
                    isEasy = isEasy,
                    viewMode = viewMode,
                    qs = qs,
                    featureStates = featureStates,
                    targetCode = state.targetCode,
                    onFeatureTap = onFeatureTap,
                    onListSelect = onListSelect,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                // Controls pane: details scroll, Skip/Hint/Give up stay pinned at its bottom.
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    details(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()))
                    controlsRow()
                }
            }
        } else {
            // One pane: map on top (never below MAP_MIN_HEIGHT), then the details (scrolling,
            // capped so they can't push the map below its floor), then the controls row pinned
            // at the bottom (D27-style), measured first so the cap can leave room for it.
            val density = LocalDensity.current
            var controlsHeight by remember { mutableStateOf(0.dp) }
            val detailsMax = (maxHeight - MAP_MIN_HEIGHT - controlsHeight - SECTION_GAP * 2).coerceAtLeast(0.dp)
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                MapArea(
                    mapState = mapState,
                    isEasy = isEasy,
                    viewMode = viewMode,
                    qs = qs,
                    featureStates = featureStates,
                    targetCode = state.targetCode,
                    onFeatureTap = onFeatureTap,
                    onListSelect = onListSelect,
                    modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = MAP_MIN_HEIGHT)
                )
                details(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = detailsMax)
                        .verticalScroll(rememberScrollState())
                )
                Box(modifier = Modifier.fillMaxWidth().onSizeChanged { controlsHeight = with(density) { it.height.toDp() } }) {
                    controlsRow()
                }
            }
        }
    }
}

/**
 * The map pane: [MapCanvas], [MapListAlternative] (never at Easy, D39), a loading spinner, or
 * [MapLoadError]. [onListSelect]'s [MapListAlternative] is re-keyed on [targetCode] (S8): its
 * search query is `rememberSaveable` with no key of its own, so without this a leftover query
 * from the previous prompt would carry over and could hide the new target's row.
 */
@Composable
private fun MapArea(
    mapState: TapMapMapState,
    isEasy: Boolean,
    viewMode: MapViewMode,
    qs: QuizState,
    featureStates: Map<String, MapFeatureState>,
    targetCode: String?,
    onFeatureTap: (MapHit?) -> Unit,
    onListSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scene = rememberMapScene(
        projection = mapState.preset.projection,
        coarse = mapState.coarse,
        detail = mapState.detail,
        tapZones = mapState.tapZones,
        bounds = mapState.frame,
        members = mapState.members,
        panMarginFraction = mapState.preset.panMarginFraction
    )
    val mapViewState = rememberMapViewState()

    Box(modifier = modifier) {
        when {
            // D39: the map, never the list, at Easy - and never naming the target (mapContentDescription
            // below only ever reports found/wrong counts and the region, not any one country).
            isEasy || viewMode == MapViewMode.Map -> when {
                scene != null -> MapCanvas(
                    scene = scene,
                    state = mapViewState,
                    modifier = Modifier.fillMaxSize(),
                    featureStates = featureStates,
                    onFeatureTap = if (isEasy) null else onFeatureTap,
                    doubleTapToZoom = false,
                    mapContentDescription = mapContentDescription(
                        res = a11yResources(),
                        regionLabel = stringResource(mapState.preset.label),
                        featureStates = featureStates,
                        total = qs.quiz.countries.size
                    )
                )
                mapState.loadFailed -> MapLoadError()
                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            viewMode == MapViewMode.List -> key(targetCode) {
                MapListAlternative(
                    countries = qs.quiz.countries,
                    featureStates = featureStates,
                    onSelect = onListSelect,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun MapLoadError() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.mode_tap_map_map_error),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

/** Status, prompt (a polite live region, S8), Easy's options and feedback; the controls row is pinned separately. */
@Composable
private fun ControlsBlock(
    state: TapMapGameState,
    difficulty: Difficulty,
    timerVisible: Boolean,
    timerSeconds: Int,
    target: Country?,
    onChoiceSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val qs = state.quizState
    val isEasy = difficulty == Difficulty.EASY

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusRow(qs = qs, difficulty = difficulty, timerVisible = timerVisible, timerSeconds = timerSeconds)

        if (!isEasy && target != null) {
            // S8: a polite live region, so a TalkBack user hears the new prompt without having
            // to find it again after each find, skip or reveal.
            Text(
                text = stringResource(R.string.mode_tap_map_prompt, target.name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
            )
        } else if (isEasy) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.mode_tap_map_easy_heading),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    }
                )
                // D39: a text clue for everyone (also TalkBack's only clue, since there is no
                // list at Easy); never the target's name.
                if (target != null) {
                    Text(
                        text = stringResource(R.string.mode_tap_map_easy_subregion_clue, target.subregion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isEasy) {
            qs.choice?.let { choice ->
                // showPrompt = false: the map (the target highlighted) and the subregion clue
                // above are the prompt, not the generic "Which of these fits ...?" text.
                MultipleChoicePanel(
                    question = choice,
                    feedback = qs.choiceFeedback,
                    enabled = true,
                    onSelect = onChoiceSelected,
                    showPrompt = false
                )
            }
        }

        FeedbackArea(state = state)
    }
}

@Composable
private fun StatusRow(qs: QuizState, difficulty: Difficulty, timerVisible: Boolean, timerSeconds: Int) {
    val found = qs.answeredCountries.size
    val total = qs.quiz.countries.size

    // S4: the progress text as the "main" slot, so it never gets squeezed to zero width by the
    // strikes/timer/tier group; that group wraps to its own line first instead.
    AdaptiveTrailingRow(
        modifier = Modifier.fillMaxWidth(),
        main = {
            Text(
                text = pluralStringResource(R.plurals.a11y_progress_tap_map, total, found, total),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (difficulty == Difficulty.HARD) {
                    val limit = Difficulty.HARD.strikeLimit ?: HARD_STRIKE_LIMIT
                    // S1: the VM never lets this exceed the limit, but coerce defensively too, so
                    // the label never reads past it (e.g. "4 of 3").
                    val shown = qs.incorrectGuesses.coerceAtMost(limit)
                    val description = pluralStringResource(R.plurals.a11y_hard_mode_strikes, limit, shown, limit)
                    Text(
                        text = stringResource(R.string.mode_tap_map_strikes_short, shown, limit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.geoColors.wrong,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
                    )
                }
                if (timerVisible) {
                    TimerDisplay(elapsedSeconds = timerSeconds, timerSeconds = null)
                }
                DifficultyLabel(difficulty = difficulty)
            }
        }
    )
}

/**
 * Polite live region for Normal/Hard's tap feedback (D36); Easy's pick feedback is shown by
 * [MultipleChoicePanel] itself, directly under its options.
 */
@Composable
private fun FeedbackArea(state: TapMapGameState) {
    val qs = state.quizState
    Box(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        when {
            state.revealedCode != null -> {
                val name = qs.quiz.countries.find { it.code == state.revealedCode }?.name.orEmpty()
                FeedbackLine(
                    icon = Icons.Filled.Close,
                    text = stringResource(R.string.mode_tap_map_missed_feedback, name),
                    color = MaterialTheme.geoColors.wrong,
                    style = MaterialTheme.typography.bodyMedium,
                    iconSize = FEEDBACK_ICON_SIZE
                )
            }
            state.wrongFlashName != null -> FeedbackLine(
                icon = Icons.Filled.Close,
                text = stringResource(R.string.mode_tap_map_wrong_tap_feedback, state.wrongFlashName),
                color = MaterialTheme.geoColors.wrong,
                style = MaterialTheme.typography.bodyMedium,
                iconSize = FEEDBACK_ICON_SIZE
            )
        }
    }
}

/** Skip, Hint (hidden at Easy, D39) and Give up: stacked instead of clipped when they don't fit (S3). */
@Composable
private fun ControlsRow(isEasy: Boolean, hintUsed: Boolean, onSkip: () -> Unit, onHint: () -> Unit, onGiveUp: () -> Unit) {
    AdaptiveButtonRow(modifier = Modifier.fillMaxWidth()) {
        if (!isEasy) {
            OutlinedButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.mode_tap_map_skip))
            }
            val hintDescription = stringResource(
                if (hintUsed) R.string.a11y_tap_map_hint_used else R.string.a11y_tap_map_hint
            )
            OutlinedButton(
                onClick = onHint,
                enabled = !hintUsed,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clearAndSetSemantics { contentDescription = hintDescription }
            ) {
                Text(stringResource(R.string.mode_tap_map_hint))
            }
        }
        OutlinedButton(onClick = onGiveUp, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(inlineIconSize(FEEDBACK_ICON_SIZE))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.quiz_give_up))
        }
    }
}

private val FEEDBACK_ICON_SIZE = 18.dp
private const val HARD_STRIKE_LIMIT = 3

/** A floor on the map pane's height in the one-pane layout (S5), so a tall controls column never squeezes it away. */
private val MAP_MIN_HEIGHT = 160.dp

/** Gap between the map, the details and the controls row in the one-pane layout. */
private val SECTION_GAP = 8.dp

/**
 * Found = blue tick, revealed-missed = orange cross (D36: [MapFeatureState.Wrong] doubles as "a
 * wrong tap or a missed feature"). The current target (Normal/Hard being revealed, or Easy's
 * highlighted target) and a FLASH_REGION hint's subregion mates (which now include the target
 * itself, S9) are highlighted (no marker); a just-tapped wrong country flashes orange on top.
 */
internal fun tapMapFeatureStates(state: TapMapGameState, isEasy: Boolean): Map<String, MapFeatureState> {
    val qs = state.quizState
    val map = HashMap<String, MapFeatureState>(qs.answeredCountries.size + qs.missedCountries.size)
    qs.answeredCountries.forEach { map[it] = MapFeatureState.Found }
    qs.missedCountries.forEach { map[it] = MapFeatureState.Wrong }
    if (isEasy) {
        qs.choice?.targetCode?.let { map[it] = MapFeatureState.Highlighted }
    } else {
        state.revealedCode?.let { map[it] = MapFeatureState.Highlighted }
    }
    state.hintFlashCodes.forEach { code -> map.putIfAbsent(code, MapFeatureState.Highlighted) }
    state.wrongFlashCode?.let { map[it] = MapFeatureState.Wrong }
    return map
}
