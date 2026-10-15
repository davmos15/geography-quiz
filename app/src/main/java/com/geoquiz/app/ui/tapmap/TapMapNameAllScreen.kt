package com.geoquiz.app.ui.tapmap

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.ui.components.AdaptiveButtonRow
import com.geoquiz.app.ui.components.WindowLayout
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.components.rememberReducedMotion
import com.geoquiz.app.ui.map.MapCanvas
import com.geoquiz.app.ui.map.MapOrListToggle
import com.geoquiz.app.ui.map.MapViewMode
import com.geoquiz.app.ui.map.mapContentDescription
import com.geoquiz.app.ui.map.rememberIsTouchExplorationEnabled
import com.geoquiz.app.ui.map.rememberMapScene
import com.geoquiz.app.ui.map.rememberMapViewState
import com.geoquiz.app.ui.map.sortCountriesForList
import com.geoquiz.app.ui.quiz.components.AnswerInput
import com.geoquiz.app.ui.quiz.components.RecentAnswers
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.quiz.feedback.FeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.FeedbackSignal
import com.geoquiz.app.ui.quiz.feedback.HapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.feedback.rememberFeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer
import com.geoquiz.app.ui.theme.geoColors

/**
 * Tap the map's "Name all" game screen (task 5.2b, D40): type every member of the chosen
 * region's name; each correct one fills in on the map. Shares the Give up dialog, the paused
 * overlay and the map-loading error text with Find ([TapMapPausedOverlay], [MapLoadError]), and
 * the status row ([StatusRow]). Finishing navigates to Results exactly like Find and the classic
 * quiz, popping this screen off the back stack. Backgrounding pauses, exactly like Find
 * ([LifecycleEventEffect]); back (system or the top bar's) during an active round asks before
 * giving up, the same dialog as the Give up button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapMapNameAllScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TapMapNameAllViewModel = hiltViewModel(),
    haptics: HapticFeedbackPlayer = rememberHapticFeedbackPlayer()
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

    val activeState = (uiState as? TapMapNameAllUiState.Active)?.state
    val roundActive = activeState != null && !activeState.isComplete

    LaunchedEffect(completion) {
        val current = completion
        if (current != null && current.step == TapMapCompletion.Step.NAVIGATE) {
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
    val reducedMotion = rememberReducedMotion()
    val feedbackMotion = rememberFeedbackMotion(feedbackSignal, reducedMotion)

    // D40/D37: back (system or the top bar's) during an active round asks before giving up,
    // instead of leaving straight away (same as Find).
    BackHandler(enabled = roundActive) { showGiveUpDialog = true }

    // Pauses on backgrounding, exactly like Find and the classic quiz: rotation does not call
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
                    if (uiState is TapMapNameAllUiState.Active) {
                        MapOrListToggle(mode = viewMode, onModeChange = { viewMode = it })
                    }
                }
            )
        }
    ) { padding ->
        // The Scaffold pads for the system bars; consuming that padding lets imePadding() add
        // only the part of the keyboard above the navigation bar (adjustResize, 3.7). WindowLayout
        // is read from the space this screen has before the keyboard takes its share, so opening
        // it never flips between one and two panes (same structure as the classic quiz, QuizScreen).
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            val windowLayout = WindowLayout.of(maxWidth, maxHeight)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                when (val state = uiState) {
                    is TapMapNameAllUiState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    is TapMapNameAllUiState.Active -> Box(modifier = Modifier.fillMaxSize()) {
                        TapMapNameAllActiveContent(
                            state = state.state,
                            mapState = mapState,
                            difficulty = difficulty,
                            timerVisible = timerVisible,
                            timerSeconds = timerSeconds,
                            viewMode = viewMode,
                            onInputChange = viewModel::onInputChange,
                            onSubmit = viewModel::onSubmitAnswer,
                            onGiveUp = { showGiveUpDialog = true },
                            motion = feedbackMotion,
                            windowLayout = windowLayout,
                            modifier = Modifier
                                .fillMaxSize()
                                // While paused, only the pause overlay is reachable by TalkBack.
                                .then(if (state.state.isPaused) Modifier.clearAndSetSemantics { } else Modifier)
                        )

                        if (state.state.isPaused) {
                            TapMapPausedOverlay(
                                answeredCount = state.state.answeredCountries.size,
                                totalCount = state.state.quiz.countries.size,
                                pausedProgressRes = viewModel.modeSpec.labels.pausedProgress,
                                onResume = viewModel::togglePause
                            )
                        }
                    }
                }
            }
        }

        if (showGiveUpDialog) {
            val active = uiState as? TapMapNameAllUiState.Active
            AlertDialog(
                onDismissRequest = { showGiveUpDialog = false },
                title = { Text(stringResource(R.string.quiz_give_up_title)) },
                text = {
                    Text(
                        stringResource(
                            viewModel.modeSpec.labels.giveUpMessage,
                            active?.state?.answeredCountries?.size ?: 0,
                            active?.state?.quiz?.countries?.size ?: 0
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
 * Internal (not private) so a UI test can render it directly, mirroring [TapMapActiveContent].
 * [windowLayout], when given, decides one pane vs two instead of this composable's own
 * [BoxWithConstraints] (the screen computes it once, before the keyboard takes any space, so
 * opening it never flips between one and two panes, D27-style); null (a UI test rendering this
 * directly, with no keyboard to worry about) falls back to measuring here. Two panes: the map at
 * the start, a scrollable status/recent-answers column at the end with the answer field and
 * Submit/Give up pinned below it. One pane: the map above (a floor on its height,
 * [MAP_MIN_HEIGHT]), then the same scrolling status/recent answers (capped so the map keeps that
 * floor on a short screen), then the field and buttons pinned at the bottom. Only the status row
 * and recent answers ever scroll - the field and its feedback line stay pinned, exactly like the
 * classic quiz ([com.geoquiz.app.ui.quiz.QuizScreen], D27).
 */
@Composable
internal fun TapMapNameAllActiveContent(
    state: QuizState,
    mapState: TapMapMapState,
    difficulty: Difficulty,
    timerVisible: Boolean,
    timerSeconds: Int,
    viewMode: MapViewMode,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGiveUp: () -> Unit,
    motion: FeedbackMotion? = null,
    modifier: Modifier = Modifier,
    windowLayout: WindowLayout? = null
) {
    val featureStates = remember(state.answeredCountries) { tapMapNameAllFeatureStates(state) }
    val enabled = !state.isComplete && !state.isPaused

    val footer: @Composable () -> Unit = {
        TapMapNameAllFooter(
            state = state,
            enabled = enabled,
            onInputChange = onInputChange,
            onSubmit = onSubmit,
            onGiveUp = onGiveUp,
            motion = motion
        )
    }
    val details: @Composable (Modifier) -> Unit = { detailsModifier ->
        TapMapNameAllDetails(
            state = state,
            difficulty = difficulty,
            timerVisible = timerVisible,
            timerSeconds = timerSeconds,
            modifier = detailsModifier
        )
    }

    BoxWithConstraints(modifier = modifier.padding(16.dp)) {
        val layout = windowLayout ?: WindowLayout.of(maxWidth, maxHeight)
        if (layout.useTwoPanes) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TapMapNameAllMapArea(
                    mapState = mapState,
                    viewMode = viewMode,
                    quiz = state.quiz,
                    featureStates = featureStates,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    details(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()))
                    footer()
                }
            }
        } else {
            val density = LocalDensity.current
            var footerHeight by remember { mutableStateOf(0.dp) }
            val detailsMax = (maxHeight - MAP_MIN_HEIGHT - footerHeight - SECTION_GAP * 2).coerceAtLeast(0.dp)
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
                TapMapNameAllMapArea(
                    mapState = mapState,
                    viewMode = viewMode,
                    quiz = state.quiz,
                    featureStates = featureStates,
                    modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = MAP_MIN_HEIGHT)
                )
                details(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = detailsMax)
                        .verticalScroll(rememberScrollState())
                )
                Box(modifier = Modifier.fillMaxWidth().onSizeChanged { footerHeight = with(density) { it.height.toDp() } }) {
                    footer()
                }
            }
        }
    }
}

/**
 * The map pane: [MapCanvas] (taps do nothing except pan/zoom - [MapCanvas]'s own gestures, not an
 * answer path, D40) or [TapMapNameAllFoundList] (never clickable), a loading spinner, or
 * [MapLoadError].
 */
@Composable
private fun TapMapNameAllMapArea(
    mapState: TapMapMapState,
    viewMode: MapViewMode,
    quiz: Quiz,
    featureStates: Map<String, MapFeatureState>,
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
            viewMode == MapViewMode.Map -> when {
                scene != null -> MapCanvas(
                    scene = scene,
                    state = mapViewState,
                    modifier = Modifier.fillMaxSize(),
                    featureStates = featureStates,
                    // D40: taps do nothing but pan/zoom; naming is typed, never tapped.
                    onFeatureTap = null,
                    mapContentDescription = mapContentDescription(
                        res = a11yResources(),
                        regionLabel = stringResource(mapState.preset.label),
                        featureStates = featureStates,
                        total = quiz.countries.size
                    )
                )
                mapState.loadFailed -> MapLoadError()
                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> TapMapNameAllFoundList(
                countries = quiz.countries,
                foundCodes = featureStates.filterValues { it == MapFeatureState.Found }.keys,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * The TalkBack-friendly alternative to the map (D40): only the countries already named, each with
 * a tick, plus a count of how many are left - a full list (as [com.geoquiz.app.ui.map.MapListAlternative]
 * shows for Find) would read out the answers. Rows are not clickable: naming is typed, never
 * tapped or picked from a list.
 */
@Composable
internal fun TapMapNameAllFoundList(
    countries: List<Country>,
    foundCodes: Set<String>,
    modifier: Modifier = Modifier
) {
    val found = remember(countries, foundCodes) {
        sortCountriesForList(countries.filter { it.code in foundCodes })
    }
    val remaining = countries.size - found.size

    Column(modifier = modifier) {
        if (found.isEmpty()) {
            Text(
                text = stringResource(R.string.mode_tap_map_name_all_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        tapMapNameAllFoundListBody(found)
        if (remaining > 0) {
            Text(
                text = pluralStringResource(R.plurals.mode_tap_map_name_all_remaining, remaining, remaining),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/** Separate so the [LazyColumn] always gets the remaining [ColumnScope] weight, even when empty. */
@Composable
private fun ColumnScope.tapMapNameAllFoundListBody(found: List<Country>) {
    LazyColumn(modifier = Modifier.weight(1f).fillMaxSize()) {
        items(found, key = { it.code }) { country ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    // Not clickable (selecting a row does nothing, D40): merged anyway, so
                    // TalkBack reads the name and "Found" as one row, not two separate texts.
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.geoColors.correct)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = country.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.map_list_state_found), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

/** Status row and recent answers (D27): the only part of the controls that scrolls. */
@Composable
private fun TapMapNameAllDetails(
    state: QuizState,
    difficulty: Difficulty,
    timerVisible: Boolean,
    timerSeconds: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusRow(qs = state, difficulty = difficulty, timerVisible = timerVisible, timerSeconds = timerSeconds)

        val recentCountries = remember(state.recentCorrect, state.quiz.countries) {
            state.recentCorrect.mapNotNull { code -> state.quiz.countries.find { it.code == code } }
        }
        if (recentCountries.isNotEmpty()) {
            // RecentAnswers only special-cases Capitals mode; Countries always shows the name,
            // which is exactly what Name all wants (never a capital, D40).
            RecentAnswers(recent = recentCountries, quizMode = QuizMode.COUNTRIES, showCountryHint = false)
        }
    }
}

/**
 * The answer field (with its feedback line) and the Give up/Submit row, pinned below the
 * scrolling [TapMapNameAllDetails] (D27, same structure as the classic quiz's `answerControls`).
 */
@Composable
private fun TapMapNameAllFooter(
    state: QuizState,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGiveUp: () -> Unit,
    motion: FeedbackMotion?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnswerInput(
            value = state.currentInput,
            onValueChange = onInputChange,
            onSubmit = onSubmit,
            lastResult = state.lastAnswerResult,
            enabled = enabled,
            // QuizMode.COUNTRIES' input label ("Enter a country name") is exactly this variant's
            // own prompt; reused rather than duplicated (D40: same validation and copy as the
            // classic Countries quiz).
            quizMode = QuizMode.COUNTRIES,
            motion = motion
        )
        TapMapNameAllButtonsRow(enabled = enabled, onSubmit = onSubmit, onGiveUp = onGiveUp)
    }
}

/** Give up and Submit: stacked instead of clipped when they don't fit (S3, same as Find). */
@Composable
private fun TapMapNameAllButtonsRow(enabled: Boolean, onSubmit: () -> Unit, onGiveUp: () -> Unit) {
    AdaptiveButtonRow(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onGiveUp, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(inlineIconSize(GIVE_UP_ICON_SIZE))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.quiz_give_up))
        }
        Button(onClick = onSubmit, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.quiz_submit))
        }
    }
}

/** Found = blue tick; nothing else is ever marked (no wrong/highlighted feature at Name all, D40). */
internal fun tapMapNameAllFeatureStates(state: QuizState): Map<String, MapFeatureState> =
    state.answeredCountries.associateWith { MapFeatureState.Found }

private val GIVE_UP_ICON_SIZE = 18.dp
