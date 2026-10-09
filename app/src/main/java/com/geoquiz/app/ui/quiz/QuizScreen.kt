package com.geoquiz.app.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.R
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.components.rememberReducedMotion
import com.geoquiz.app.ui.components.WindowLayout
import com.geoquiz.app.ui.quiz.components.RecentAnswers
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.quiz.feedback.FeedbackSignal
import com.geoquiz.app.ui.quiz.feedback.HapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.feedback.rememberFeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.components.AnswerInput
import com.geoquiz.app.ui.quiz.components.CountryList
import com.geoquiz.app.ui.quiz.components.MultipleChoicePanel
import com.geoquiz.app.ui.quiz.components.PanelAboveList
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.theme.geoColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuizScreen(
    onQuizComplete: (resultId: String) -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel(),
    hapticFeedbackPlayer: HapticFeedbackPlayer = rememberHapticFeedbackPlayer()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val showTimer by viewModel.showTimer.collectAsStateWithLifecycle()
    val showFlags by viewModel.showFlags.collectAsStateWithLifecycle()
    val showCountryHint by viewModel.showCountryHint.collectAsStateWithLifecycle()
    val timerVisible by viewModel.timerVisible.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    var showGiveUpDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val completion by viewModel.completion.collectAsStateWithLifecycle()

    // Answer feedback (3.3): a haptic and a short animation per answer. The haptic respects the
    // system touch-feedback setting; the animation is skipped when animations are turned off.
    val haptics by rememberUpdatedState(hapticFeedbackPlayer)
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
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

    // Auto-pause and save when the app goes to the background. Rotation also stops the
    // activity, but the ViewModel survives it, so the quiz keeps running.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    // Once the quiz is recorded, go to Results. Any interstitial is shown there, after Results
    // has rendered (see ResultsScreen), never on the way.
    LaunchedEffect(completion) {
        val done = completion ?: return@LaunchedEffect
        when (done.step) {
            QuizCompletion.Step.DONE -> Unit
            QuizCompletion.Step.NAVIGATE -> {
                viewModel.onNavigatedToResults()
                onQuizComplete(done.resultId)
            }
        }
    }

    when (val state = uiState) {
        is QuizUiState.Loading -> {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.quiz_loading))
                }
            }
        }

        is QuizUiState.Active -> {
            val quizState = state.state
            val res = a11yResources()
            val answeredCount = quizState.answeredCountries.size
            val totalCount = quizState.quiz.countries.size
            val progressDescription = A11yText.progress(res, viewModel.quizMode, answeredCount, totalCount)
            val pausedText = stringResource(R.string.quiz_paused)
            val resumedText = stringResource(R.string.quiz_resumed)

            // Says "Paused" / "Resumed" when the player pauses or resumes (follow-up (f)). The
            // live region node below is always composed, so TalkBack hears its text change;
            // a live region on the overlay itself is created at the same moment and may be
            // missed. Nothing is said for the state the screen opens in. "Resumed" is cleared
            // after a few seconds so a later swipe does not land on stale text.
            var pauseAnnouncement by remember { mutableStateOf("") }
            var announcedPaused by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(quizState.isPaused) {
                val previous = announcedPaused
                announcedPaused = quizState.isPaused
                if (previous == null || previous == quizState.isPaused) return@LaunchedEffect
                if (quizState.isPaused) {
                    pauseAnnouncement = pausedText
                } else {
                    pauseAnnouncement = resumedText
                    delay(RESUMED_ANNOUNCEMENT_MILLIS)
                    pauseAnnouncement = ""
                }
            }

            // Pieces of the quiz screen, arranged below for one or two panes (3.7).

            // One list position for every layout, so it survives switching between one and two
            // panes (rotation, multi-window resizing).
            val listState = rememberLazyListState()

            // Category name, tier and pattern explainer
            val titleBlock: @Composable (Modifier) -> Unit = { blockModifier ->
                Column(modifier = blockModifier.fillMaxWidth()) {
                    Text(
                        text = quizState.quiz.category.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    // The tier is fixed for the whole quiz, so it is a label, not a control.
                    DifficultyLabel(
                        difficulty = difficulty,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    // Pattern explainer banner
                    val patternDescription = quizState.quiz.category.description
                    if (patternDescription != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(inlineIconSize(18.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = patternDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Progress, optional timer, incorrect count, settings and pause, then the progress bar.
            // The stats wrap onto a second line at large text sizes, so the settings and pause
            // buttons always keep their 48 dp (3.6).
            val statusBlock: @Composable (Modifier) -> Unit = { blockModifier ->
                Column(modifier = blockModifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Polite live region: announced when the count changes (a correct
                            // answer), e.g. "12 of 197 countries named". Not tied to the timer.
                            Text(
                                text = stringResource(R.string.quiz_count_of, answeredCount, totalCount),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = progressDescription
                                    liveRegion = LiveRegionMode.Polite
                                }
                            )
                            if (timerVisible) {
                                TimerDisplay(
                                    elapsedSeconds = timerSeconds,
                                    timerSeconds = null
                                )
                            }
                            val strikeLimit = difficulty.strikeLimit
                            if (strikeLimit != null) {
                                val strikesDescription = A11yText.strikes(res, quizState.incorrectGuesses, strikeLimit)
                                val strikesColor = if (quizState.incorrectGuesses >= strikeLimit - 1) MaterialTheme.geoColors.wrong
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                // Cross icon + count, so strikes never rely on colour alone.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = strikesDescription
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = strikesColor,
                                        modifier = Modifier.size(inlineIconSize(18.dp))
                                    )
                                    Text(
                                        text = stringResource(R.string.quiz_count_of, quizState.incorrectGuesses, strikeLimit),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = strikesColor
                                    )
                                }
                            } else if (quizState.incorrectGuesses > 0) {
                                val incorrectDescription = A11yText.incorrectGuesses(res, quizState.incorrectGuesses)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = incorrectDescription
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.geoColors.wrong,
                                        modifier = Modifier.size(inlineIconSize(18.dp))
                                    )
                                    Text(
                                        text = "${quizState.incorrectGuesses}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.geoColors.wrong
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.quiz_settings)
                            )
                        }
                        IconButton(onClick = { viewModel.togglePause() }) {
                            Icon(
                                imageVector = if (quizState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = stringResource(
                                    if (quizState.isPaused) R.string.quiz_resume else R.string.quiz_pause
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Same information as the count above, so hidden from TalkBack.
                    LinearProgressIndicator(
                        progress = { quizState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics { },
                        color = MaterialTheme.geoColors.correct,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            val giveUpButton: @Composable () -> Unit = {
                IconButton(
                    onClick = { showGiveUpDialog = true },
                    enabled = !quizState.isPaused
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.quiz_give_up),
                        tint = if (!quizState.isPaused) MaterialTheme.geoColors.wrong
                            else MaterialTheme.geoColors.wrong.copy(alpha = 0.38f)
                    )
                }
            }

            val countryList: @Composable (Modifier, (@Composable () -> Unit)?) -> Unit = { listModifier, header ->
                CountryList(
                    countries = quizState.quiz.countries,
                    answeredCodes = quizState.answeredCountries,
                    quizMode = viewModel.quizMode,
                    showFlags = showFlags,
                    showCountryHint = showCountryHint,
                    modifier = listModifier,
                    header = header,
                    state = listState
                )
            }

            // Easy: pick from 4 options (D14).
            val choicePanel: @Composable () -> Unit = {
                val choice = quizState.choice
                if (choice != null) {
                    MultipleChoicePanel(
                        question = choice,
                        feedback = quizState.choiceFeedback,
                        enabled = !quizState.isComplete && !quizState.isPaused,
                        onSelect = viewModel::onChoiceSelected,
                        motion = feedbackMotion
                    )
                }
                giveUpButton()
            }

            // Typed tiers: the last three correct answers, newest first, then the field and the
            // Give up + Submit row, pinned at the bottom (3.7): just above the keyboard and in
            // thumb reach. The list scrolls above them, never behind the keyboard.
            val answerControls: @Composable (showRecent: Boolean, Modifier) -> Unit = { showRecent, blockModifier ->
                Column(modifier = blockModifier.fillMaxWidth()) {
                    val recentCountries = quizState.recentCorrect.mapNotNull { code ->
                        quizState.quiz.countries.find { it.code == code }
                    }
                    if (showRecent && recentCountries.isNotEmpty()) {
                        RecentAnswers(
                            recent = recentCountries,
                            quizMode = viewModel.quizMode,
                            showCountryHint = showCountryHint
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    AnswerInput(
                        value = quizState.currentInput,
                        onValueChange = viewModel::onInputChange,
                        onSubmit = viewModel::onSubmitAnswer,
                        lastResult = quizState.lastAnswerResult,
                        enabled = !quizState.isComplete && !quizState.isPaused,
                        quizMode = viewModel.quizMode,
                        motion = feedbackMotion
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        giveUpButton()
                        Button(
                            onClick = viewModel::onSubmitAnswer,
                            modifier = Modifier.weight(1f),
                            enabled = !quizState.isComplete && !quizState.isPaused
                        ) {
                            Text(stringResource(R.string.quiz_submit))
                        }
                    }
                }
            }

            val fontScale = LocalDensity.current.fontScale

            Scaffold { padding ->
                // The Scaffold pads for the system bars; consuming that padding lets imePadding()
                // add only the part of the keyboard above the navigation bar (adjustResize, 3.7).
                // One or two panes follow the space this screen really has, before the keyboard
                // takes its share (so opening the keyboard never switches the layout).
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
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                // While paused, only the pause overlay is reachable by TalkBack.
                                .then(if (quizState.isPaused) Modifier.clearAndSetSemantics { } else Modifier)
                        ) {
                            val space = quizSpace(maxHeight, fontScale)
                            if (windowLayout.useTwoPanes) {
                                // Tablets and phones in landscape: the prompt, status and answer
                                // controls on the start side, the list beside them. TalkBack reads
                                // the controls pane first.
                                val controlsScroll = rememberScrollState()
                                LaunchedEffect(quizState.choice?.targetCode) { controlsScroll.scrollTo(0) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .semantics { isTraversalGroup = true },
                                    horizontalArrangement = Arrangement.spacedBy(TWO_PANE_GAP)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .traversalOrder(CONTROLS_ORDER)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .verticalScroll(controlsScroll)
                                        ) {
                                            titleBlock(Modifier)
                                            statusBlock(Modifier)
                                            if (difficulty == Difficulty.EASY) choicePanel()
                                        }
                                        if (difficulty != Difficulty.EASY) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            answerControls(space.showRecentAnswers, Modifier)
                                        }
                                    }
                                    countryList(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        null
                                    )
                                }
                            } else {
                                // Phones in portrait. On short screens (keyboard open, large text)
                                // the title scrolls away, so the rows keep their space: with the
                                // list in the typed tiers, and at the top of the options panel in
                                // Easy, so it is still read and seen before the question.
                                val isEasy = difficulty == Difficulty.EASY
                                val titleInList = space.titleScrollsWithList && !isEasy
                                val titleInPanel = space.titleScrollsWithList && isEasy
                                val listHeader: (@Composable () -> Unit)? =
                                    if (titleInList) ({ titleBlock(Modifier) }) else null
                                // When the title moves into the list of a list still at the top
                                // (the keyboard opens), show it rather than the first row. A list
                                // the player has scrolled stays where it is.
                                LaunchedEffect(titleInList) {
                                    if (titleInList &&
                                        listState.firstVisibleItemIndex <= 1 &&
                                        listState.firstVisibleItemScrollOffset == 0
                                    ) {
                                        listState.scrollToItem(0)
                                    }
                                }
                                // The answer controls sit below the list on screen but are read
                                // straight after the status, before the list (traversal order).
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .semantics { isTraversalGroup = true }
                                ) {
                                    if (!space.titleScrollsWithList) titleBlock(Modifier.traversalOrder(TITLE_ORDER))
                                    statusBlock(Modifier.traversalOrder(STATUS_ORDER))
                                    if (isEasy) {
                                        // The options get the space they need, but never so much that
                                        // the list disappears: at large font scales or on short
                                        // screens they scroll (3.6). A title that scrolls is the
                                        // panel's first item, above the prompt, so each question
                                        // scrolls back to it.
                                        PanelAboveList(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            panel = {
                                                if (titleInPanel) titleBlock(Modifier)
                                                choicePanel()
                                            },
                                            list = { listModifier -> countryList(listModifier, listHeader) },
                                            scrollKey = quizState.choice?.targetCode
                                        )
                                    } else {
                                        countryList(Modifier.weight(1f), listHeader)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        answerControls(space.showRecentAnswers, Modifier.traversalOrder(CONTROLS_ORDER))
                                    }
                                }
                            }
                        }

                        // Pause overlay
                        if (quizState.isPaused) {
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
                                        Icons.Default.PauseCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    // Pausing is announced by the status node after the overlay.
                                    Text(
                                        pausedText,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(
                                            viewModel.modeSpec.labels.pausedProgress, answeredCount, totalCount
                                        ),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clearAndSetSemantics {
                                            contentDescription = progressDescription
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(onClick = { viewModel.togglePause() }) {
                                        Text(stringResource(R.string.quiz_resume))
                                    }
                                }
                            }
                        }

                        // Always-composed status for TalkBack (see pauseAnnouncement). 1 dp and
                        // clipped: nothing visible, but still on screen, so TalkBack keeps it.
                        // Without a description while idle, so it is never an empty focus stop.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(1.dp)
                                .clipToBounds()
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    if (pauseAnnouncement.isNotEmpty()) contentDescription = pauseAnnouncement
                                }
                        )
                    }
                }
            }

            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false }
                ) {
                    // Scrolls when large text makes the switches taller than the sheet.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            stringResource(R.string.quiz_settings),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .semantics { heading() }
                        )
                        if (difficulty.timerAlwaysShown) {
                            // Hard always shows the timer (D15), so there is nothing to switch.
                            SettingsToggleRow(
                                label = stringResource(R.string.quiz_setting_show_timer),
                                description = stringResource(R.string.quiz_timer_always_shown_hard),
                                checked = true,
                                enabled = false,
                                onToggle = {}
                            )
                        } else {
                            SettingsToggleRow(
                                stringResource(R.string.quiz_setting_show_timer),
                                stringResource(R.string.quiz_setting_show_timer_summary),
                                showTimer
                            ) {
                                viewModel.toggleShowTimer()
                            }
                        }
                        SettingsToggleRow(
                            stringResource(R.string.setting_vibration),
                            stringResource(R.string.setting_vibration_summary),
                            vibration
                        ) {
                            viewModel.toggleVibration()
                        }
                        SettingsToggleRow(
                            stringResource(R.string.quiz_setting_show_flags),
                            stringResource(R.string.quiz_setting_show_flags_summary),
                            showFlags
                        ) {
                            viewModel.toggleShowFlags()
                        }
                        if (viewModel.quizMode == QuizMode.CAPITALS) {
                            SettingsToggleRow(
                                stringResource(R.string.quiz_setting_country_hint),
                                stringResource(R.string.quiz_setting_country_hint_summary),
                                showCountryHint
                            ) {
                                viewModel.toggleShowCountryHint()
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            if (showGiveUpDialog) {
                AlertDialog(
                    onDismissRequest = { showGiveUpDialog = false },
                    title = { Text(stringResource(R.string.quiz_give_up_title)) },
                    text = {
                        Text(
                            stringResource(
                                viewModel.modeSpec.labels.giveUpMessage,
                                quizState.answeredCountries.size,
                                quizState.quiz.countries.size
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showGiveUpDialog = false
                            viewModel.onGiveUp()
                        }) {
                            Text(stringResource(R.string.quiz_give_up_confirm))
                        }
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

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

/**
 * TalkBack order of the quiz's parts (3.7): title, status, answer controls, then the list (0),
 * whatever their place on screen.
 */
internal const val TITLE_ORDER = -3f
internal const val STATUS_ORDER = -2f
internal const val CONTROLS_ORDER = -1f

/** Reads this part, as one group, at [index] among its siblings. */
private fun Modifier.traversalOrder(index: Float): Modifier = semantics {
    isTraversalGroup = true
    traversalIndex = index
}

/** Gap between the two panes on wide screens. */
private val TWO_PANE_GAP = 24.dp

/** How long "Resumed" stays in the TalkBack status after it has been announced. */
private const val RESUMED_ANNOUNCEMENT_MILLIS = 3_000L
