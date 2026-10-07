package com.geoquiz.app.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.components.buttonSemantics
import com.geoquiz.app.ui.share.ShareUtils
import com.geoquiz.app.ui.theme.CorrectGreen
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    quizMode: String = "countries",
    onNavigateBack: () -> Unit,
    onStartQuiz: (categoryType: String, categoryValue: String) -> Unit,
    viewModel: CategoryListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerName by viewModel.playerName.collectAsStateWithLifecycle(initialValue = "A friend")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.groupName, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (state.hideCompleted) "Show all" else "Hide done",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = { viewModel.toggleHideCompleted() }) {
                            Icon(
                                imageVector = if (state.hideCompleted)
                                    Icons.Default.VisibilityOff
                                else
                                    Icons.Default.Visibility,
                                contentDescription = if (state.hideCompleted)
                                    "Show all quizzes"
                                else
                                    "Hide completed quizzes"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.groupDescription.isNotBlank()) {
                    item {
                        Text(
                            text = state.groupDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                val displayedOptions = if (state.hideCompleted) {
                    state.quizOptions.filter { !it.isCompleted }
                } else {
                    state.quizOptions
                }
                items(displayedOptions) { option ->
                    QuizOptionCard(
                        option = option,
                        quizMode = QuizMode.fromId(quizMode),
                        onClick = { onStartQuiz(option.categoryType, option.categoryValue) },
                        onChallenge = {
                            val category = QuizCategory.fromRoute(option.categoryType, option.categoryValue)
                            val deepLink = ChallengeDeepLink(
                                challengeId = UUID.randomUUID().toString(),
                                categoryType = option.categoryType,
                                categoryValue = option.categoryValue,
                                challengerName = playerName,
                                challengerScore = null,
                                challengerTotal = null,
                                challengerTime = null,
                                quizMode = quizMode
                            )
                            viewModel.saveOutgoingChallenge(deepLink.challengeId, option.categoryType, option.categoryValue)
                            ShareUtils.shareChallenge(
                                context = context,
                                categoryName = category.displayName,
                                deepLink = deepLink.toShareUrl()
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizOptionCard(
    option: QuizOptionInfo,
    quizMode: QuizMode,
    onClick: () -> Unit,
    onChallenge: () -> Unit
) {
    val res = a11yResources()
    // The card merges its texts into one TalkBack item ("Completed, Africa, 54 countries,
    // double-tap to start quiz"); the share button stays a separate item.
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .buttonSemantics(stringResource(R.string.action_start_quiz), onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (option.isCompleted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.a11y_completed),
                    tint = CorrectGreen,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (option.description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (option.bestCorrect != null && option.bestTotal != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val bestPoints = String.format(Locale.US, "%.0f", option.bestScore)
                    val bestDescription = stringResource(
                        R.string.a11y_best_score, option.bestCorrect, option.bestTotal, bestPoints
                    )
                    Text(
                        text = "Best: ${option.bestCorrect}/${option.bestTotal} (${bestPoints}pts)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = bestDescription }
                    )
                }
            }
            val countDescription = A11yText.quizOptionCount(res, quizMode, option.countryCount)
            Text(
                text = "${option.countryCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clearAndSetSemantics { contentDescription = countDescription }
            )
            IconButton(
                onClick = onChallenge,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Challenge a friend",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
