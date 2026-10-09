package com.geoquiz.app.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.ui.ads.BannerAd
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.MasteryStarsRow
import com.geoquiz.app.ui.components.SEGMENT_LABEL_CHROME
import com.geoquiz.app.ui.components.TWO_PANE_MAX_WIDTH
import com.geoquiz.app.ui.components.UpToTwoColumns
import com.geoquiz.app.ui.components.WidthClass
import com.geoquiz.app.ui.components.WindowLayout
import com.geoquiz.app.ui.components.a11yResources
import com.geoquiz.app.ui.components.buttonSemantics
import com.geoquiz.app.ui.components.readableWidth
import com.geoquiz.app.ui.mode.imageVector
import java.util.Locale

/**
 * The Play tab. Top to bottom: "Today's challenge" (placeholder, only behind its feature flag),
 * "Continue" (the saved quiz), "Recommended next" (for the selected mode), "Pinned" (every mode), the
 * classic-mode switch, that mode's "All" tile and category groups, and the "New modes" grid
 * (hidden while no new mode is available).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    onOpenCategory: (modeId: String, groupId: String) -> Unit,
    onStartQuiz: (modeId: String, categoryType: String, categoryValue: String) -> Unit,
    onOpenMode: (modeId: String) -> Unit,
    viewModel: PlayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        // One position for the mode switch and tiles in either layout, so it survives the
        // switch between them (rotation, multi-window, the cards coming and going).
        val browseState = rememberLazyGridState()
        // The layout follows the space this screen has (beside the rail), not the whole window.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val windowLayout = WindowLayout.of(maxWidth, maxHeight)
            val hasCards = state.showTodayChallenge || state.savedQuiz != null ||
                state.recommended != null || state.pinned.isNotEmpty()
            val onResume: (SavedQuizInfo) -> Unit = { saved ->
                onStartQuiz(saved.quizModeId, saved.categoryType, saved.categoryValue)
            }
            if (windowLayout.useWideHub && hasCards) {
                // Large tablets (3.7): the cards in their own column beside the mode switch
                // and tiles, which take more columns as the width allows.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth(TWO_PANE_MAX_WIDTH)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier
                            .weight(CARDS_PANE_WEIGHT)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        playCards(state, onResume, viewModel::dismissSavedQuiz, onStartQuiz)
                    }
                    LazyVerticalGrid(
                        columns = UpToTwoColumns(
                            minCellWidth = MIN_TILE_WIDTH,
                            scaleAbove = TILE_SCALE_ABOVE,
                            maxColumns = WIDE_MAX_TILE_COLUMNS
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        state = browseState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        playBrowse(state, viewModel::selectMode, onOpenCategory, onStartQuiz, onOpenMode)
                    }
                }
            } else {
                LazyVerticalGrid(
                    // Two columns of tiles on phones (one at large text sizes, so tile titles
                    // don't break mid-word); more on wider screens when they fit.
                    columns = UpToTwoColumns(
                        minCellWidth = MIN_TILE_WIDTH,
                        scaleAbove = TILE_SCALE_ABOVE,
                        maxColumns = maxTileColumns(windowLayout.widthClass)
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .readableWidth(TWO_PANE_MAX_WIDTH),
                    state = browseState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    playCards(state, onResume, viewModel::dismissSavedQuiz, onStartQuiz)
                    playBrowse(state, viewModel::selectMode, onOpenCategory, onStartQuiz, onOpenMode)
                }
            }
        }
    }
}

/** Today's challenge, Continue, Recommended next and Pinned, each only when there is one. */
private fun LazyGridScope.playCards(
    state: PlayUiState,
    onResume: (SavedQuizInfo) -> Unit,
    onDismissSaved: () -> Unit,
    onStartQuiz: (modeId: String, categoryType: String, categoryValue: String) -> Unit
) {
    if (state.showTodayChallenge) {
        fullWidth("today") { TodayChallengeCard() }
    }

    state.savedQuiz?.let { saved ->
        fullWidth("continue") {
            ContinueCard(
                saved = saved,
                onResume = { onResume(saved) },
                onDismiss = onDismissSaved
            )
        }
    }

    state.recommended?.let { recommended ->
        fullWidth("recommended") {
            RecommendedCard(
                recommended = recommended,
                onStart = {
                    onStartQuiz(recommended.quizModeId, recommended.categoryType, recommended.categoryValue)
                }
            )
        }
    }

    if (state.pinned.isNotEmpty()) {
        fullWidth("pinned-heading") { SectionHeading(stringResource(R.string.play_pinned_heading)) }
        items(
            state.pinned,
            key = { "pin/${it.quizModeId}/${it.categoryType}/${it.categoryValue}" },
            span = { GridItemSpan(maxLineSpan) }
        ) { pin ->
            PinnedRow(
                pin = pin,
                onStart = { onStartQuiz(pin.quizModeId, pin.categoryType, pin.categoryValue) }
            )
        }
    }
}

/** The mode switch, the selected mode's All tile and groups, and the New modes grid. */
private fun LazyGridScope.playBrowse(
    state: PlayUiState,
    onSelectMode: (String) -> Unit,
    onOpenCategory: (modeId: String, groupId: String) -> Unit,
    onStartQuiz: (modeId: String, categoryType: String, categoryValue: String) -> Unit,
    onOpenMode: (modeId: String) -> Unit
) {
    if (state.classicModes.isNotEmpty()) {
        fullWidth("mode-switch") {
            ModeSwitch(
                modes = state.classicModes,
                selectedId = state.selectedModeId,
                onSelect = onSelectMode
            )
        }
    }

    val content = state.selectedContent
    if (content == null) {
        fullWidth("loading") { Loading() }
    } else {
        content.allItems?.let { all ->
            fullWidth("all/${content.modeId}") {
                AllItemsCard(all, onClick = { onStartQuiz(content.modeId, "all", "_") })
            }
        }

        fullWidth("banner") { BannerAd(modifier = Modifier.padding(vertical = 8.dp)) }

        fullWidth("categories-heading") { SectionHeading(stringResource(R.string.play_categories_heading)) }

        items(content.groups, key = { "${content.modeId}/${it.id}" }) { group ->
            GroupTileCard(group, onClick = { onOpenCategory(content.modeId, group.id) })
        }
    }

    if (state.newModes.isNotEmpty()) {
        fullWidth("new-modes-heading") {
            SectionHeading(
                stringResource(R.string.play_new_modes_heading),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(state.newModes, key = { "mode/${it.id}" }) { mode ->
            NewModeTile(mode, onClick = { onOpenMode(mode.id) })
        }
    }
}

/** The most tile columns for the window's [widthClass]: 2 on phones, 3 on tablets (3.7). */
internal fun maxTileColumns(widthClass: WidthClass): Int =
    if (widthClass == WidthClass.Compact) 2 else WIDE_MAX_TILE_COLUMNS

/** Tile columns allowed on wide windows (when the tiles still fit, see [UpToTwoColumns]). */
private const val WIDE_MAX_TILE_COLUMNS = 3

/** The cards column's share of the width beside the tiles (tiles get 1). */
private const val CARDS_PANE_WEIGHT = 0.8f

/**
 * A full-width item. Every item has a [key], so a grid keeps its scroll position by item when
 * items before it come and go or it moves to the other layout (3.7).
 */
private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.play_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(bottom = 4.dp)
            .semantics { heading() }
    )
}

/**
 * Countries / Capitals / Flags as single-choice segmented buttons (read as radio buttons with
 * their selected state). When a label would not fit on one line (large text or a narrow screen)
 * the segments show only the mode icon, described by the mode name; the selected one keeps its
 * tick, so the selection never relies on colour alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(
    modes: List<ModeOption>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    val labels = modes.map { stringResource(it.label) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.play_mode_switch_description),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .semantics { heading() }
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val segmentWidth = maxWidth / modes.size
            val labelRoom = segmentWidth - SEGMENT_LABEL_CHROME
            val labelsFit = labels.all { label ->
                val width = measurer.measure(label, labelStyle, maxLines = 1, softWrap = false).size.width
                with(density) { width.toDp() } <= labelRoom
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    val selected = mode.id == selectedId
                    SegmentedButton(
                        selected = selected,
                        onClick = { onSelect(mode.id) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        icon = {
                            if (labelsFit) {
                                SegmentedButtonDefaults.Icon(active = selected) {
                                    Icon(
                                        mode.icon.imageVector,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                    )
                                }
                            } else {
                                SegmentedButtonDefaults.Icon(active = selected)
                            }
                        },
                        label = {
                            if (labelsFit) {
                                Text(labels[index])
                            } else {
                                Icon(mode.icon.imageVector, contentDescription = labels[index])
                            }
                        }
                    )
                }
            }
        }
    }
}

/** Narrowest group tile before the grid drops to one column... */
private val MIN_TILE_WIDTH = 120.dp

/** ...scaled with the text only above this font scale, so 100–130% keeps two columns on phones. */
private const val TILE_SCALE_ABOVE = 1.3f

/**
 * Placeholder for the daily challenge (D24): shown only while its feature flag is on, not
 * clickable and with no made-up content. Phase 8 wires it up. Read as one item.
 */
@Composable
private fun TodayChallengeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        CardRow(icon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(32.dp)) }) {
            Text(
                text = stringResource(R.string.play_today_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.play_today_body),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * The quiz saved part-way through: mode, category, answered count and time played. One tap
 * resumes it in its own mode; the cross dismisses it (a separate 48 dp button).
 */
@Composable
private fun ContinueCard(
    saved: SavedQuizInfo,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val res = a11yResources()
    val minutes = saved.timeElapsedSeconds.coerceAtLeast(0) / 60
    val seconds = saved.timeElapsedSeconds.coerceAtLeast(0) % 60
    val shownTime = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    val spokenProgress = stringResource(
        R.string.play_continue_progress,
        saved.answeredCount,
        A11yText.duration(res, saved.timeElapsedSeconds)
    )
    Card(
        onClick = onResume,
        modifier = Modifier
            .fillMaxWidth()
            .buttonSemantics(stringResource(R.string.action_resume_quiz), onResume),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        CardRow(
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp)) },
            trailing = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.play_resume_dismiss))
                }
            }
        ) {
            Text(
                text = stringResource(R.string.play_continue_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.play_card_mode_category,
                    stringResource(saved.modeLabel),
                    saved.categoryDisplayName
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.play_continue_progress, saved.answeredCount, shownTime),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spokenProgress }
            )
        }
    }
}

/** "Recommended next" for the selected mode, with its mastery stars; one tap starts it. */
@Composable
private fun RecommendedCard(
    recommended: RecommendedQuiz,
    onStart: () -> Unit
) {
    Card(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .buttonSemantics(stringResource(R.string.action_start_quiz), onStart),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        CardRow(icon = { Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(32.dp)) }) {
            Text(
                text = stringResource(R.string.play_recommended_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.play_card_mode_category,
                    stringResource(recommended.modeLabel),
                    recommended.categoryDisplayName
                ),
                style = MaterialTheme.typography.bodySmall
            )
            MasteryStarsRow(stars = recommended.stars, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * A pinned category as a compact full-width row (never a tile grid): mode icon, category name
 * and mode name. One tap starts it in its mode; read as one button.
 */
@Composable
private fun PinnedRow(pin: PinnedQuiz, onStart: () -> Unit) {
    Card(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .buttonSemantics(stringResource(R.string.action_start_quiz), onStart),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(pin.modeIcon.imageVector, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pin.categoryDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(pin.modeLabel),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** Icon, text column and optional trailing button of the cards above the mode switch. */
@Composable
private fun CardRow(
    icon: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = if (trailing == null) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            text()
        }
        trailing?.invoke()
    }
}

@Composable
private fun AllItemsCard(all: AllItemsTile, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .buttonSemantics(stringResource(R.string.action_start_quiz), onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(all.title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluralStringResource(all.countLabel, all.count, all.count),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GroupTileCard(group: GroupTile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.buttonSemantics(stringResource(R.string.action_open_category), onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.play_group_quiz_count, group.quizCount, group.quizCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun NewModeTile(mode: ModeOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.buttonSemantics(stringResource(R.string.action_open_mode), onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(mode.icon.imageVector, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(mode.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
