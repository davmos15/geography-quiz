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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.AdaptiveTrailingRow
import com.geoquiz.app.ui.components.DifficultySelector
import com.geoquiz.app.ui.components.MasteryStarsRow
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.components.buttonSemantics
import com.geoquiz.app.ui.quiz.components.inlineIconSize
import com.geoquiz.app.ui.share.ShareUtils
import com.geoquiz.app.ui.theme.geoColors
import java.text.NumberFormat
import kotlin.math.roundToInt
import com.geoquiz.app.ui.components.ReadableWidthFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    quizMode: String = "countries",
    onNavigateBack: () -> Unit,
    /** [difficultyId] is the selected [com.geoquiz.app.domain.model.Difficulty.id]. */
    onStartQuiz: (categoryType: String, categoryValue: String, difficultyId: String) -> Unit,
    viewModel: CategoryListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            // The title wraps to two lines at large text sizes (TalkBack reads it in full).
            WrappingTopAppBar(
                title = state.groupName,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        // At large text sizes only the icon stays (its description says what it
                        // does), so the title keeps the room.
                        if (LocalDensity.current.fontScale < LARGE_TEXT_FONT_SCALE) {
                            Text(
                                text = stringResource(
                                    if (state.hideCompleted) R.string.category_show_all else R.string.category_hide_done
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.toggleHideCompleted() }) {
                            Icon(
                                imageVector = if (state.hideCompleted)
                                    Icons.Default.VisibilityOff
                                else
                                    Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (state.hideCompleted) R.string.category_show_all_description
                                    else R.string.category_hide_completed_description
                                )
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
            ReadableWidthFrame(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) { sideInset ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp + sideInset, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // The tier for the next quiz, remembered as the default: one tap on a category
                    // then starts at this tier.
                    item(key = "difficulty") {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(
                                text = stringResource(R.string.difficulty_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .semantics { heading() }
                            )
                            DifficultySelector(
                                selected = difficulty,
                                onSelect = viewModel::onDifficultySelected,
                                options = viewModel.difficulties
                            )
                        }
                    }
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
                            onClick = { onStartQuiz(option.categoryType, option.categoryValue, difficulty.id) },
                            onTogglePin = { viewModel.onTogglePin(option) },
                            onChallenge = {
                                val category = QuizCategory.fromRoute(option.categoryType, option.categoryValue)
                                ShareUtils.shareChallenge(
                                    context = context,
                                    categoryName = category.displayName,
                                    deepLink = viewModel.createChallengeShareUrl(option.categoryType, option.categoryValue)
                                )
                            }
                        )
                    }
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
    onTogglePin: () -> Unit,
    onChallenge: () -> Unit
) {
    val res = a11yResources()
    // The card merges its texts into one TalkBack item ("Completed, Africa, 54 countries,
    // double-tap to start quiz"); the pin toggle and the share button stay separate items.
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
        // The count, pin and share buttons sit to the right of the name while it keeps enough
        // room; at large text sizes they move below it, so the name, stars and Best wrap at
        // full width instead of being squeezed (3.6).
        AdaptiveTrailingRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            main = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (option.isCompleted) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.a11y_completed),
                            tint = MaterialTheme.geoColors.correct,
                            modifier = Modifier
                                .size(inlineIconSize(20.dp))
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
                        MasteryStarsRow(
                            stars = option.masteryStars,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (option.bestCorrect != null && option.bestTotal != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val bestPoints = formatBestPoints(option.bestScore ?: 0.0)
                            val bestDescription = stringResource(
                                R.string.a11y_best_score, option.bestCorrect, option.bestTotal, bestPoints
                            )
                            Text(
                                text = stringResource(
                                    R.string.category_best_score, option.bestCorrect, option.bestTotal, bestPoints
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clearAndSetSemantics { contentDescription = bestDescription }
                            )
                        }
                    }
                }
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val countDescription = A11yText.quizOptionCount(res, quizMode, option.countryCount)
                    Text(
                        text = "${option.countryCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clearAndSetSemantics { contentDescription = countDescription }
                    )
                    // A checkbox-style toggle: TalkBack reads "Pin Africa, not checked" / "Unpin Africa, checked".
                    IconToggleButton(
                        checked = option.isPinned,
                        onCheckedChange = { onTogglePin() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (option.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(
                                if (option.isPinned) R.string.category_unpin else R.string.category_pin,
                                option.name
                            ),
                            tint = if (option.isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onChallenge,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.category_challenge_friend),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )
    }
}

/** Best points, rounded to a whole number (half up) in the device locale's digits. */
internal fun formatBestPoints(score: Double): String =
    NumberFormat.getIntegerInstance().format(score.roundToInt())

/** From this text scale the top bar drops the "Hide done" / "Show all" label and keeps the icon. */
private const val LARGE_TEXT_FONT_SCALE = 1.5f
