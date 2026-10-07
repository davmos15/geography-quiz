package com.geoquiz.app.ui.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.data.local.db.ChallengeEntity
import com.geoquiz.app.R
import com.geoquiz.app.ui.theme.geoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeLeaderboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChallengeLeaderboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Challenges") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stats bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn("Wins", state.wins, MaterialTheme.geoColors.correct)
                        StatColumn("Losses", state.losses, MaterialTheme.geoColors.wrong)
                        StatColumn("Ties", state.ties, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (state.challenges.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        text = "No challenges yet!\nShare a quiz result or challenge a friend to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp)
                    )
                }
            }

            items(state.challenges) { challenge ->
                ChallengeCard(challenge)
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ChallengeCard(challenge: ChallengeEntity) {
    // Normalise scores: for outgoing, YOU are the challenger
    val yourScore = if (challenge.direction == "outgoing") challenge.challengerScore else challenge.myScore
    val yourTotal = if (challenge.direction == "outgoing") challenge.challengerTotal else challenge.myTotal
    val opponentName = if (challenge.direction == "outgoing") "Opponent" else challenge.challengerName
    val opponentScore = if (challenge.direction == "outgoing") challenge.myScore else challenge.challengerScore
    val opponentTotal = if (challenge.direction == "outgoing") challenge.myTotal else challenge.challengerTotal

    val isWin = challenge.status == "completed" &&
            yourScore != null && opponentScore != null &&
            yourScore > opponentScore
    val isTie = challenge.status == "completed" &&
            yourScore != null && opponentScore != null &&
            yourScore == opponentScore

    val isLoss = challenge.status == "completed" && !isWin && !isTie
    val geoColors = MaterialTheme.geoColors
    val containerColor = when {
        isWin -> geoColors.correctContainer
        isLoss -> geoColors.wrongContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isWin -> geoColors.onCorrectContainer
        isLoss -> geoColors.onWrongContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Won/lost is shown by a tick or cross as well as the card colour.
                if (isWin || isLoss) {
                    Icon(
                        imageVector = if (isWin) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = stringResource(
                            if (isWin) R.string.a11y_challenge_won else R.string.a11y_challenge_lost
                        ),
                        tint = if (isWin) geoColors.correct else geoColors.wrong,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = challenge.categoryDisplayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (yourScore != null && yourTotal != null) {
                        Text(
                            text = "$yourScore/$yourTotal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "vs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = opponentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (opponentScore != null && opponentTotal != null) {
                        Text(
                            text = "$opponentScore/$opponentTotal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
