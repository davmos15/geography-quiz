@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.geoquiz.app.ui.silhouettes

import androidx.activity.compose.BackHandler
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
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
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.components.DifficultyLabel
import com.geoquiz.app.ui.components.ReadableWidthFrame
import com.geoquiz.app.ui.components.WindowLayout
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.rememberWindowLayout
import com.geoquiz.app.ui.components.rememberReducedMotion
import com.geoquiz.app.ui.quiz.components.MultipleChoicePanel
import com.geoquiz.app.ui.quiz.components.TimerDisplay
import com.geoquiz.app.ui.quiz.feedback.FeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.FeedbackSignal
import com.geoquiz.app.ui.quiz.feedback.rememberFeedbackMotion
import com.geoquiz.app.ui.quiz.feedback.rememberHapticFeedbackPlayer
import com.geoquiz.app.ui.quiz.quizSpace

/**
 * Country silhouettes' game screen (task 5.1), registered at
 * [com.geoquiz.app.ui.navigation.Screen.Silhouettes]: one silhouette at a time
 * ([SilhouetteOutlineBox]), typed (Normal/Hard) or four choices (Easy,
 * [com.geoquiz.app.ui.quiz.components.MultipleChoicePanel]). Finishing (every silhouette answered
 * or passed, or lives gone) or Give up navigates to Results via [onFinished]. Backgrounding pauses
 * the round like the classic quiz (task 5.1 review, S3); a paused overlay with Resume is the only
 * way back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SilhouettesGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SilhouettesGameViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerVisible by viewModel.timerVisible.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
    val vibrationEnabled by rememberUpdatedState(vibration)
    val completion by viewModel.completion.collectAsStateWithLifecycle()
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

    // Auto-pause when the app goes to the background, like the classic quiz (task 5.1 review,
    // S3). Rotation also stops the activity, but the ViewModel survives it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if ((context as? Activity)?.isChangingConfigurations != true) viewModel.onBackgrounded()
    }

    LaunchedEffect(completion) {
        val current = completion
        if (current != null && current.step == SilhouettesCompletion.Step.NAVIGATE) {
            onFinished(current.resultId)
            viewModel.onNavigatedToResults()
        }
    }

    var showGiveUpDialog by remember { mutableStateOf(false) }
    // Back during a round asks first, like Tap the map (D39): there is no resume save (D37), so
    // leaving would throw the round away unrecorded.
    val roundInProgress = (uiState as? SilhouettesUiState.Active)?.state?.isComplete == false
    BackHandler(enabled = roundInProgress) { showGiveUpDialog = true }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_silhouettes_name),
                navigationIcon = {
                    IconButton(onClick = { if (roundInProgress) showGiveUpDialog = true else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is SilhouettesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is SilhouettesUiState.Active -> {
                val round = state.state
                val fontScale = LocalDensity.current.fontScale
                // The whole window's layout (task 5.1 review, S5 fix), not this content area's:
                // unlike the classic quiz, this screen has its own top app bar, so the space left
                // for content can come out wider than tall at a large font on a short phone (the
                // bar itself takes most of the height), which would wrongly read as a landscape
                // phone and switch to two panes on a narrow screen.
                val windowLayout = rememberWindowLayout()
                BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
                    val space = quizSpace(maxHeight, fontScale)
                    SilhouettesActiveContent(
                        round = round,
                        windowLayout = windowLayout,
                        headerScrolls = space.titleScrollsWithList,
                        timerVisible = timerVisible,
                        timerSeconds = timerSeconds,
                        feedbackMotion = feedbackMotion,
                        viewModel = viewModel,
                        onGiveUp = { showGiveUpDialog = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (round.paused) Modifier.clearAndSetSemantics { } else Modifier)
                    )

                    if (round.paused) {
                        PausedOverlay(
                            progressText = stringResource(
                                R.string.mode_silhouettes_paused_progress, round.answeredCodes.size, round.countries.size
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
                                    R.string.mode_silhouettes_give_up,
                                    round.answeredCodes.size,
                                    round.countries.size
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
    }
}

/**
 * The round's content for the space it has (task 5.1 review, S5): on short heights (or large
 * text) the header scrolls away with the content and only the answer field + Submit stay pinned
 * at the bottom (D27), Pass/Give up moving into the scrolling area; on wide screens
 * ([WindowLayout.useTwoPanes]) the outline, hints and guessed-so-far chips sit in a start pane and
 * the answer controls in an end pane.
 */
@Composable
private fun SilhouettesActiveContent(
    round: SilhouetteState,
    windowLayout: WindowLayout,
    headerScrolls: Boolean,
    timerVisible: Boolean,
    timerSeconds: Int,
    feedbackMotion: FeedbackMotion?,
    viewModel: SilhouettesGameViewModel,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTyped = round.difficulty != Difficulty.EASY
    val header: @Composable (Modifier) -> Unit = { blockModifier ->
        SilhouettesHeader(
            current = round.progress,
            total = round.countries.size,
            lives = round.lives,
            difficulty = round.difficulty,
            timerVisible = timerVisible,
            timerSeconds = timerSeconds,
            modifier = blockModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    val mainContent: @Composable (Modifier) -> Unit = { blockModifier ->
        ReadableWidthFrame(modifier = blockModifier.fillMaxWidth()) { sideInset ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp + sideInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val a11ySilhouette = stringResource(
                        R.string.silhouettes_a11y_progress,
                        (round.progress + 1).coerceAtMost(round.countries.size.coerceAtLeast(1)),
                        round.countries.size
                    )
                if (round.difficulty == Difficulty.EASY) {
                    Text(
                        text = stringResource(R.string.silhouettes_prompt_which_country),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                SilhouetteOutlineBox(
                    outline = round.outline,
                    isLoading = round.isOutlineLoading,
                    isUnavailable = round.isOutlineUnavailable,
                    showNeighbours = round.showNeighbours,
                    contentDescription = a11ySilhouette,
                    modifier = Modifier.fillMaxWidth()
                )
                SilhouetteFeedbackBanner(
                    feedback = round.feedback,
                    feedbackToken = round.feedbackToken,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isTyped) {
                    GuessedSoFarChips(names = round.wrongGuessNamesForCurrent, modifier = Modifier.fillMaxWidth())
                }
                SilhouetteHintsRow(
                    available = round.availableHints,
                    used = round.hintsUsedForCurrent,
                    revealedSubregion = round.current?.subregion,
                    revealedFirstLetter = round.current?.name?.firstOrNull(),
                    neighbourNames = round.neighbourNames,
                    enabled = !round.isComplete && !round.isAdvancing && !round.paused,
                    onHint = viewModel::onHint,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    val choicePanel: @Composable () -> Unit = {
        round.choice?.let { choice ->
            MultipleChoicePanel(
                question = choice,
                feedback = round.choiceFeedback,
                enabled = !round.isAdvancing && !round.paused,
                onSelect = viewModel::onChoiceSelected,
                modifier = Modifier.fillMaxWidth(),
                motion = feedbackMotion,
                showPrompt = false
            )
        }
    }

    val controlsEnabled = !round.isComplete && !round.isAdvancing && !round.paused
    val giveUpEnabled = !round.paused

    val controls: @Composable (Modifier) -> Unit = { blockModifier ->
        Column(modifier = blockModifier.fillMaxWidth()) {
            if (isTyped) {
                SilhouetteInputRow(
                    input = round.currentInput,
                    onInputChange = viewModel::onInputChange,
                    onSubmit = viewModel::onSubmit,
                    enabled = !round.isComplete && !round.paused,
                    readOnly = round.isAdvancing,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                SilhouetteSecondaryActions(
                    onPass = viewModel::onPass,
                    onGiveUp = onGiveUp,
                    passEnabled = controlsEnabled,
                    giveUpEnabled = giveUpEnabled
                )
            } else {
                choicePanel()
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onGiveUp, enabled = giveUpEnabled) {
                        Text(stringResource(R.string.quiz_give_up))
                    }
                }
            }
        }
    }

    if (windowLayout.useTwoPanes) {
        Column(modifier = modifier) {
            header(Modifier)
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(TWO_PANE_GAP)) {
                mainContent(Modifier.weight(1f).fillMaxHeight())
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                    controls(Modifier)
                }
            }
        }
    } else if (!headerScrolls) {
        Column(modifier = modifier) {
            header(Modifier)
            mainContent(Modifier.weight(1f))
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                controls(Modifier)
            }
        }
    } else {
        // Short screens / large text (task 5.1 review, S5): the header scrolls away with the
        // content, Pass/Give up move into the scrolling area, and only the field + Submit (or,
        // at Easy, the choice panel + Give up) stay pinned at the bottom.
        Column(modifier = modifier) {
            ReadableWidthFrame(modifier = Modifier.weight(1f).fillMaxWidth()) { sideInset ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp + sideInset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // The outline comes first (task 5.1 review, S5): it is the round's whole
                    // point, so on the shortest screens it is what stays closest to view, with
                    // the header (already shown once as the pinned bar's title) scrolling below.
                    val a11ySilhouette = stringResource(
                        R.string.silhouettes_a11y_progress,
                        (round.progress + 1).coerceAtMost(round.countries.size.coerceAtLeast(1)),
                        round.countries.size
                    )
                    if (round.difficulty == Difficulty.EASY) {
                        Text(
                            text = stringResource(R.string.silhouettes_prompt_which_country),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    SilhouetteOutlineBox(
                        outline = round.outline,
                        isLoading = round.isOutlineLoading,
                        isUnavailable = round.isOutlineUnavailable,
                        showNeighbours = round.showNeighbours,
                        contentDescription = a11ySilhouette,
                        modifier = Modifier.fillMaxWidth()
                    )
                    header(Modifier)
                    SilhouetteFeedbackBanner(
                        feedback = round.feedback,
                        feedbackToken = round.feedbackToken,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isTyped) {
                        GuessedSoFarChips(names = round.wrongGuessNamesForCurrent, modifier = Modifier.fillMaxWidth())
                    } else {
                        choicePanel()
                    }
                    SilhouetteHintsRow(
                        available = round.availableHints,
                        used = round.hintsUsedForCurrent,
                        revealedSubregion = round.current?.subregion,
                        revealedFirstLetter = round.current?.name?.firstOrNull(),
                        neighbourNames = round.neighbourNames,
                        enabled = !round.isComplete && !round.isAdvancing && !round.paused,
                        onHint = viewModel::onHint,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isTyped) {
                        SilhouetteSecondaryActions(
                            onPass = viewModel::onPass,
                            onGiveUp = onGiveUp,
                            passEnabled = controlsEnabled,
                            giveUpEnabled = giveUpEnabled
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = onGiveUp, enabled = giveUpEnabled) {
                                Text(stringResource(R.string.quiz_give_up))
                            }
                        }
                    }
                }
            }
            if (isTyped) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    SilhouetteInputRow(
                        input = round.currentInput,
                        onInputChange = viewModel::onInputChange,
                        onSubmit = viewModel::onSubmit,
                        enabled = !round.isComplete && !round.paused,
                        readOnly = round.isAdvancing,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
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

@Composable
private fun SilhouettesHeader(
    current: Int,
    total: Int,
    lives: Int,
    difficulty: Difficulty,
    timerVisible: Boolean,
    timerSeconds: Int,
    modifier: Modifier = Modifier
) {
    val progressText = stringResource(R.string.silhouettes_progress, (current + 1).coerceAtMost(total.coerceAtLeast(1)), total)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Read as its plain visible text ("3 of 15"); the fuller "Silhouette 3 of 15" lives on
        // the outline below, the one element on screen that otherwise has nothing to say.
        Text(
            text = progressText,
            style = MaterialTheme.typography.titleMedium
        )
        LivesRow(lives = lives, maxLives = SilhouetteState.MAX_LIVES)
        DifficultyLabel(difficulty = difficulty)
        if (timerVisible) {
            TimerDisplay(elapsedSeconds = timerSeconds, timerSeconds = null)
        }
    }
}

/** The answer field + Submit (D27): always pinned at the bottom in a tall layout. */
@Composable
private fun SilhouetteInputRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.mode_silhouettes_input_label)) },
            singleLine = true,
            // Enabled throughout (task 5.1 review, S1): disabling the field during the brief
            // advance closes the keyboard on every silhouette. Read-only blocks typing without
            // doing that.
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onSubmit, enabled = enabled && !readOnly && input.isNotBlank()) {
            Text(stringResource(R.string.quiz_submit))
        }
    }
}

/** Pass and Give up: pinned alongside the field on a tall screen, or moved into the scroll area. */
@Composable
private fun SilhouetteSecondaryActions(
    onPass: () -> Unit,
    onGiveUp: () -> Unit,
    passEnabled: Boolean,
    giveUpEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPass, enabled = passEnabled) {
            Text(stringResource(R.string.silhouettes_pass))
        }
        OutlinedButton(onClick = onGiveUp, enabled = giveUpEnabled) {
            Text(stringResource(R.string.quiz_give_up))
        }
    }
}

/** Gap between the two panes on wide screens, matching the classic quiz. */
private val TWO_PANE_GAP = 24.dp
