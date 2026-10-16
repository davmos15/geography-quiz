package com.geoquiz.app.ui.results

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.data.service.rememberSoundEffectPlayer
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.AdaptiveButtonRow
import com.geoquiz.app.ui.components.TWO_PANE_MAX_WIDTH
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.components.readableWidth
import com.geoquiz.app.ui.components.WindowLayout
import com.geoquiz.app.ui.navigation.ModeRoutes
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.quiz.feedback.ConfettiOverlay
import com.geoquiz.app.ui.share.ShareCardContent
import com.geoquiz.app.ui.share.ShareCardDialog
import com.geoquiz.app.ui.share.ShareCardRenderer
import com.geoquiz.app.ui.share.ShareUtils
import com.geoquiz.app.ui.share.shareCardStrings
import com.geoquiz.app.ui.theme.geoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import java.util.Locale

@Composable
fun ResultsScreen(
    /** A route built by [com.geoquiz.app.ui.navigation.ModeRoutes.playAgain]. */
    onPlayAgain: (route: String) -> Unit,
    onGoHome: (quizMode: String) -> Unit,
    onViewAnswers: (resultId: String) -> Unit,
    /**
     * "Practise the ones you missed" (3.5c): a quiz of the same mode and tier with exactly the
     * missed items, as a [com.geoquiz.app.domain.model.QuizCategory.Practice] route, built by
     * [com.geoquiz.app.ui.navigation.ModeRoutes.practise].
     */
    onPractiseMissed: (route: String) -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
        ResultsUiState.Loading -> ResultLoading()
        ResultsUiState.Missing -> ResultMissing(onGoHome = { onGoHome(QuizMode.COUNTRIES.id) })
        is ResultsUiState.Loaded -> {
            val result = state.result
            val practice = if (state.modeSpec.supportsPractice) result.practiceCategoryOrNull() else null
            InterstitialAfterFirstFrame(viewModel)
            ResultsContent(
                score = result.score,
                correctAnswers = result.correct,
                totalCountries = result.total,
                timeElapsedSeconds = result.timeSeconds,
                perfectBonus = result.perfectBonus,
                // A practice quiz shows its title from strings.xml (D21: it is not shared either).
                categoryName = if (result.isPractice) {
                    stringResource(R.string.practice_quiz_title)
                } else {
                    result.categoryName
                },
                categoryType = result.categoryType,
                categoryValue = result.categoryValue,
                quizMode = result.quizModeId,
                modeSpec = state.modeSpec,
                showChallenge = state.showChallenge,
                incorrectGuesses = result.incorrectGuesses,
                newAchievements = result.newAchievements,
                isPractice = result.isPractice,
                missedCount = practice?.codes?.size ?: 0,
                onPractiseMissed = {
                    if (practice != null) {
                        onPractiseMissed(
                            ModeRoutes.practise(
                                modeId = result.quizModeId,
                                categoryType = practice.typeKey,
                                categoryValue = practice.valueKey,
                                difficulty = result.difficulty.id,
                                variantId = result.variantId
                            )
                        )
                    }
                },
                onPlayAgain = {
                    onPlayAgain(
                        ModeRoutes.playAgain(
                            modeId = result.quizModeId,
                            categoryType = result.categoryType,
                            categoryValue = result.categoryValue,
                            difficulty = result.difficulty.id,
                            variantId = result.variantId
                        )
                    )
                },
                onGoHome = { onGoHome(result.quizModeId) },
                onViewAnswers = { onViewAnswers(result.id) },
                viewModel = viewModel,
                resultId = result.id,
                // 8.3: the share card builder reads this directly (date, mode id, etc.) rather
                // than threading yet more individual fields through this already-long parameter
                // list.
                completedQuiz = result
            )
        }
    }
}

/**
 * Shows a due interstitial only after the Results content has been composed and drawn: the
 * effect starts once this composition is applied, and the next frame callback comes after that
 * frame was drawn.
 */
@Composable
private fun InterstitialAfterFirstFrame(viewModel: ResultsViewModel) {
    val due by viewModel.interstitialDue.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    if (due && activity != null) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            viewModel.showDueInterstitial(activity)
        }
    }
}

@Composable
private fun ResultsContent(
    score: Double,
    correctAnswers: Int,
    totalCountries: Int,
    timeElapsedSeconds: Int,
    perfectBonus: Boolean,
    categoryName: String,
    categoryType: String,
    categoryValue: String,
    quizMode: String,
    modeSpec: GameModeSpec,
    showChallenge: Boolean,
    incorrectGuesses: Int,
    newAchievements: List<Achievement>,
    isPractice: Boolean,
    missedCount: Int,
    onPractiseMissed: () -> Unit,
    onPlayAgain: () -> Unit,
    onGoHome: () -> Unit,
    onViewAnswers: () -> Unit,
    viewModel: ResultsViewModel,
    /** Keys the one-off perfect-score celebration (8.5) below, so it never replays for the same
     * result: [com.geoquiz.app.ui.results.ResultsViewModel] loads one result per Results entry. */
    resultId: String,
    /** The full result, for the share card (8.3, [ShareCardContent.from]) only. */
    completedQuiz: CompletedQuiz
) {
    val context = LocalContext.current
    val challengeResult by viewModel.challengeResult.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    // The share card preview dialog (8.3): open/closed and the challenge link live in the
    // ViewModel's SavedStateHandle, so the dialog survives rotation and the link (with its
    // outgoing-challenge row) is made once per Results entry.
    val sharePreviewOpen by viewModel.sharePreviewOpen.collectAsStateWithLifecycle()
    val shareLink by viewModel.shareLink.collectAsStateWithLifecycle()

    // Perfect score (8.5): confetti plus a chime, shown once per result, not on every
    // recomposition or rotation (the "New achievements" card above needs no such guard: it is
    // static, not an animation). `celebrated` survives rotation via rememberSaveable; the
    // `remember` below locks in this composition's decision so the overlay is not pulled out of
    // composition the instant `celebrated` flips to true.
    val soundEffectPlayer = rememberSoundEffectPlayer()
    var celebrated by rememberSaveable(resultId) { mutableStateOf(false) }
    val showCelebration = remember(resultId) { perfectBonus && !celebrated }
    LaunchedEffect(resultId) {
        if (showCelebration) {
            celebrated = true
            soundEffectPlayer.playPerfect()
        }
    }

    val percentage = if (totalCountries > 0) {
        (correctAnswers.toDouble() / totalCountries * 100)
    } else 0.0

    val minutes = timeElapsedSeconds / 60
    val seconds = timeElapsedSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    // The score, stats and cards (3.7: one pane on wide screens)...
    val summary: @Composable ColumnScope.() -> Unit = {
        Text(
            text = "Quiz Complete!",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )

        if (categoryName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .semantics(mergeDescendants = true) { },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", score),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Score",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                val resultLabel = stringResource(modeSpec.labels.name)
                val res = a11yResources()
                ResultRow(
                    resultLabel,
                    "$correctAnswers / $totalCountries",
                    valueDescription = A11yText.progress(res, modeSpec, correctAnswers, totalCountries)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("Percentage", String.format(Locale.US, "%.1f%%", percentage))
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("Time", timeFormatted, valueDescription = A11yText.duration(res, timeElapsedSeconds))
                Spacer(modifier = Modifier.height(8.dp))
                ResultRow("Incorrect Guesses", incorrectGuesses.toString())
                if (perfectBonus) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutcomeLine(
                        text = "Perfect! +20% bonus!",
                        icon = Icons.Default.Check,
                        color = MaterialTheme.geoColors.correct,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        if (newAchievements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            NewAchievementsCard(newAchievements)
        }

        // Challenge result comparison card
        val challenge = challengeResult
        if (challenge != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ChallengeResultCard(
                challengerName = challenge.challengerName,
                challengerScore = challenge.challengerScore,
                challengerTotal = challenge.challengerTotal,
                challengerTime = challenge.challengerTime,
                myScore = correctAnswers,
                myTotal = totalCountries,
                myTime = timeElapsedSeconds
            )
        }
    }

    // ...and what to do next (the other pane on wide screens). [widthFraction]: the share of the
    // pane's width the buttons take (see resultsActionsWidthFraction).
    val actions: @Composable ColumnScope.(widthFraction: Float) -> Unit = { widthFraction ->
        // Resolved here (not inside the AdaptiveButtonRow content below) so the share card
        // dialog, placed after that row, can use them too.
        val itemsNoun = stringResource(modeSpec.labels.itemsNoun)
        val modeName = stringResource(modeSpec.labels.name)
        // D21: a practice quiz is not recorded, so it is not shared or sent as a challenge.
        if (isPractice) {
            Text(
                text = stringResource(R.string.results_practice_not_recorded),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(widthFraction)
            )
        } else AdaptiveButtonRow(
            // Side by side when both labels fit on one line, otherwise stacked (3.6).
            modifier = Modifier.fillMaxWidth(widthFraction)
        ) {
            OutlinedButton(
                onClick = {
                    // D35: new modes have no challenge link, so their share text/card falls back
                    // to the Play Store link (ShareCardContent.from).
                    viewModel.openSharePreview()
                },
                // Material's padding for a button with an icon: a little more room for the label.
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Share", textAlign = TextAlign.Center)
            }
            // D35: no Challenge button for a new (non-classic) mode.
            if (showChallenge) OutlinedButton(
                onClick = {
                    val deepLink = viewModel.createChallengeShareUrl(
                        categoryType, categoryValue, quizMode,
                        score = null, total = null, time = null
                    )
                    if (deepLink != null) {
                        ShareUtils.shareChallenge(
                            context = context,
                            categoryName = categoryName,
                            deepLink = deepLink
                        )
                    }
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Challenge", textAlign = TextAlign.Center)
            }
        }

        // The share card preview dialog (8.3): previews the same card either shared as a PNG
        // (through ShareCardRenderer/FileProvider) or described as plain text (ShareUtils).
        if (sharePreviewOpen) {
            val strings = shareCardStrings()
            val deepLink = shareLink?.let(Uri::parse)
            val content = remember(completedQuiz, modeName, deepLink, strings) {
                ShareCardContent.from(completedQuiz, modeName, deepLink, strings)
            }
            val shareText = {
                ShareUtils.shareResults(
                    context = context,
                    categoryName = categoryName,
                    itemsNoun = itemsNoun,
                    score = correctAnswers,
                    total = totalCountries,
                    time = timeElapsedSeconds,
                    deepLink = deepLink,
                    date = content.date
                )
            }
            ShareCardDialog(
                content = content,
                correct = correctAnswers,
                total = totalCountries,
                onDismiss = viewModel::closeSharePreview,
                onShareText = {
                    shareText()
                    viewModel.closeSharePreview()
                },
                onShareImage = { bitmap ->
                    val text = ShareUtils.buildResultsShareText(
                        categoryName, itemsNoun, correctAnswers, totalCountries,
                        timeElapsedSeconds, deepLink, content.date
                    )
                    // Writing the file is I/O, so it's off the main thread; the chooser itself
                    // must start from the main thread. Any failure (full storage, provider
                    // misconfiguration) falls back to the text share instead of crashing.
                    coroutineScope.launch {
                        val shared = runCatching {
                            val file = withContext(Dispatchers.IO) { ShareCardRenderer.writeToCache(context, bitmap) }
                            ShareCardRenderer.shareImage(
                                context = context,
                                file = file,
                                text = text,
                                chooserTitle = context.getString(R.string.share_chooser_title_image)
                            )
                        }.onFailure { if (it is CancellationException) throw it }.isSuccess
                        if (!shared) {
                            Toast.makeText(context, R.string.share_image_failed, Toast.LENGTH_SHORT).show()
                            shareText()
                        }
                    }
                    viewModel.closeSharePreview()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // View Answers button: hidden for a mode whose codes can't be resolved back to a real
        // country (GameModeSpec.supportsAnswerReview).
        if (modeSpec.supportsAnswerReview) {
            Button(
                onClick = onViewAnswers,
                modifier = Modifier.fillMaxWidth(widthFraction),
                // Tonal secondary: white on the light amber secondary fill was only 2.3:1.
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("View Answers", textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = onPlayAgain,
            modifier = Modifier.fillMaxWidth(widthFraction)
        ) {
            Text("Play Again", textAlign = TextAlign.Center)
        }

        if (missedCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            PractiseMissedButton(
                missedCount = missedCount,
                onClick = onPractiseMissed,
                modifier = Modifier.fillMaxWidth(widthFraction)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoHome,
            modifier = Modifier.fillMaxWidth(widthFraction)
        ) {
            Text("Home", textAlign = TextAlign.Center)
        }
    }

    val fontScale = LocalDensity.current.fontScale
    Scaffold { padding ->
        // One or two panes from the space this screen has (beside the rail, in multi-window).
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val twoPanes = WindowLayout.of(maxWidth, maxHeight).useTwoPanes
            val actionsWidth = resultsActionsWidthFraction(twoPanes = twoPanes, fontScale = fontScale)
            if (twoPanes) {
                // Tablets and phones in landscape: summary on the start side, actions beside it.
                // Each pane scrolls on its own.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth(TWO_PANE_MAX_WIDTH)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    ResultsPane(modifier = Modifier.weight(1f), content = summary)
                    ResultsPane(modifier = Modifier.weight(1f)) { actions(actionsWidth) }
                }
            } else {
                ResultsPane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    summary()
                    Spacer(modifier = Modifier.height(20.dp))
                    actions(actionsWidth)
                }
            }
            // Drawn last so it sits on top of both layouts above. ConfettiOverlay itself skips
            // drawing anything when reduced motion is on (checked inside it).
            if (showCelebration) {
                ConfettiOverlay(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** A centred, scrolling column of Results content. */
@Composable
private fun ResultsPane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * "Practise the ones you missed (3)": starts a quiz of exactly the missed items. No fixed height,
 * so the label wraps at large font sizes; Material buttons keep the 48 dp touch target. The icon
 * is Material's button icon size, growing with the text, and is measured before the label, so it
 * never squeezes it.
 */
@Composable
private fun PractiseMissedButton(missedCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        // Material's padding for a button with an icon: a little more room for the label.
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Icon(
            imageVector = Icons.Default.Replay,
            // Decorative: the label says it.
            contentDescription = null,
            modifier = Modifier.size(inlineIconSize(ButtonDefaults.IconSize))
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            text = pluralStringResource(R.plurals.results_practise_missed, missedCount, missedCount),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * The share of the content width the Results action buttons take: 80% normally, the full width
 * on one pane at large text ([LARGE_TEXT_FULL_WIDTH_SCALE] and up), so long labels such as
 * "Practise the ones you missed (3)" are not squeezed into a narrow pill. Two panes keep 80%.
 */
internal fun resultsActionsWidthFraction(twoPanes: Boolean, fontScale: Float): Float =
    if (!twoPanes && fontScale >= LARGE_TEXT_FULL_WIDTH_SCALE) 1f else ACTIONS_WIDTH_FRACTION

/** The usual share of the content width the Results action buttons take. */
internal const val ACTIONS_WIDTH_FRACTION = 0.8f

/** From this font scale the one-pane Results buttons use the full content width. */
internal const val LARGE_TEXT_FULL_WIDTH_SCALE = 1.5f

/**
 * The achievements this quiz unlocked. A polite live region, so TalkBack reads it once when it
 * appears; each achievement is one TalkBack item ("First Steps, Complete any quiz").
 */
@Composable
internal fun NewAchievementsCard(achievements: List<Achievement>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(
                text = pluralStringResource(R.plurals.results_new_achievements_heading, achievements.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.semantics { heading() }
            )
            achievements.forEach { achievement ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        // Decorative: the title says it.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(inlineIconSize(32.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = achievement.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeResultCard(
    challengerName: String,
    challengerScore: Int?,
    challengerTotal: Int?,
    challengerTime: Int?,
    myScore: Int,
    myTotal: Int,
    myTime: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Challenge Result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (challengerScore != null && challengerTotal != null) {
                val myPct = if (myTotal > 0) myScore.toDouble() / myTotal * 100 else 0.0
                val theirPct = if (challengerTotal > 0) challengerScore.toDouble() / challengerTotal * 100 else 0.0

                // Comparison header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = challengerName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                        // Wraps rather than cutting a long name short.
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Score row
                ComparisonRow("$myScore / $myTotal", "Score", "$challengerScore / $challengerTotal")

                Spacer(modifier = Modifier.height(4.dp))

                // Percentage row
                ComparisonRow(
                    String.format(Locale.US, "%.1f%%", myPct),
                    "Percentage",
                    String.format(Locale.US, "%.1f%%", theirPct)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Time row
                val myMin = myTime / 60
                val mySec = myTime % 60
                val theirMin = (challengerTime ?: 0) / 60
                val theirSec = (challengerTime ?: 0) % 60
                ComparisonRow(
                    String.format("%02d:%02d", myMin, mySec),
                    "Time",
                    String.format("%02d:%02d", theirMin, theirSec)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Win/loss/tie
                val resultText: String
                val resultColor: androidx.compose.ui.graphics.Color
                val resultIcon: ImageVector?
                when {
                    myPct > theirPct -> {
                        resultText = "You won!"
                        resultColor = MaterialTheme.geoColors.correct
                        resultIcon = Icons.Default.Check
                    }
                    myPct < theirPct -> {
                        resultText = "You lost - challenge them back!"
                        resultColor = MaterialTheme.geoColors.wrong
                        resultIcon = Icons.Default.Close
                    }
                    else -> {
                        resultText = "It's a tie!"
                        resultColor = MaterialTheme.colorScheme.onTertiaryContainer
                        resultIcon = null
                    }
                }

                OutcomeLine(
                    text = resultText,
                    icon = resultIcon,
                    color = resultColor,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                // Challenger didn't share their score
                Text(
                    text = "Challenge from $challengerName completed!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Share your result so they can compare.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(leftValue: String, label: String, rightValue: String) {
    // One item for TalkBack: "<you>, <label>, <them>".
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leftValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(
            text = rightValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueDescription: String? = null) {
    // One item for TalkBack: "Time, 3 minutes 12 seconds".
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // The label wraps at large text sizes; the value keeps its own space at the end.
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = 12.dp)
                .then(
                    if (valueDescription != null) {
                        Modifier.clearAndSetSemantics { contentDescription = valueDescription }
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/** Centred bold outcome text with an optional leading icon (decorative: the text says it). */
@Composable
private fun OutcomeLine(
    text: String,
    icon: ImageVector?,
    color: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(inlineIconSize(20.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = style,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
