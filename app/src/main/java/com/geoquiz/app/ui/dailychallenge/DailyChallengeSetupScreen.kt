package com.geoquiz.app.ui.dailychallenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.streak.StreakCalculator
import com.geoquiz.app.domain.streak.StreakStatus
import com.geoquiz.app.ui.components.ReadableWidthFrame
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.theme.geoColors

/**
 * Daily challenge's setup screen (task 8.1): intro text, then either a Start button or (already
 * played today) a "come back tomorrow" message with no Start button. [onStart] is responsible for
 * navigating to the game screen; there is no difficulty or category choice (the round is always
 * [com.geoquiz.app.domain.model.Difficulty.NORMAL], picked for the player).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyChallengeSetupScreen(
    onStart: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: DailyChallengeSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_daily_challenge_name),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ReadableWidthFrame(modifier = Modifier.fillMaxSize().padding(padding)) { sideInset ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp + sideInset, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.daily_challenge_setup_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StreakSummary(state.streak)
                    if (state.alreadyCompletedToday) {
                        CompletedTodayCard()
                    } else {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.daily_challenge_start_action))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Plain-text streak summary (task 8.2): current streak (or "no streak yet"), freezes held and
 * days to the next freeze. No colour or icon carries meaning here - theme text colours only, and
 * text wraps rather than clipping at large font scales.
 */
@Composable
private fun StreakSummary(streak: StreakStatus) {
    // One TalkBack stop for the whole summary.
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (streak.current > 0) {
                pluralStringResource(R.plurals.daily_challenge_streak_current, streak.current, streak.current)
            } else {
                stringResource(R.string.daily_challenge_streak_none)
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = pluralStringResource(R.plurals.daily_challenge_streak_freezes, streak.freezesHeld, streak.freezesHeld),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            // At the cap a 7th day earns nothing, so don't promise a freeze.
            text = if (streak.freezesHeld >= StreakCalculator.MAX_FREEZES) {
                stringResource(R.string.daily_challenge_streak_freezes_full)
            } else {
                pluralStringResource(
                    R.plurals.daily_challenge_streak_next_freeze,
                    streak.daysToNextFreeze,
                    streak.daysToNextFreeze
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompletedTodayCard() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.geoColors.correct
        )
        Text(
            text = stringResource(R.string.daily_challenge_completed_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.daily_challenge_completed_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
