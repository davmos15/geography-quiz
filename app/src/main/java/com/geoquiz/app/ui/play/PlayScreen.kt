package com.geoquiz.app.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.ui.ads.BannerAd
import com.geoquiz.app.ui.components.buttonSemantics
import com.geoquiz.app.ui.mode.imageVector

/**
 * The Play tab. Top to bottom: the cards of tasks 3.4b/3.4c (see the slot comments), the
 * "Resume quiz" card, the classic-mode switch, that mode's "All" tile and category groups, and
 * the "New modes" grid (hidden while no new mode is available).
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Slot (3.4b): "Today's challenge" card goes here.

            // Slot (3.4c): "Continue", "Recommended next" and "Pinned" cards go here; until then
            // the "Resume quiz" card stands in for "Continue".
            state.savedQuiz?.let { saved ->
                fullWidth {
                    ResumeCard(
                        saved = saved,
                        onResume = { onStartQuiz(saved.quizModeId, saved.categoryType, saved.categoryValue) },
                        onDismiss = viewModel::dismissSavedQuiz
                    )
                }
            }

            if (state.classicModes.isNotEmpty()) {
                fullWidth {
                    ModeSwitch(
                        modes = state.classicModes,
                        selectedId = state.selectedModeId,
                        onSelect = viewModel::selectMode
                    )
                }
            }

            val content = state.selectedContent
            if (content == null) {
                fullWidth { Loading() }
            } else {
                content.allItems?.let { all ->
                    fullWidth {
                        AllItemsCard(all, onClick = { onStartQuiz(content.modeId, "all", "_") })
                    }
                }

                fullWidth { BannerAd(modifier = Modifier.padding(vertical = 8.dp)) }

                fullWidth { SectionHeading(stringResource(R.string.play_categories_heading)) }

                items(content.groups, key = { "${content.modeId}/${it.id}" }) { group ->
                    GroupTileCard(group, onClick = { onOpenCategory(content.modeId, group.id) })
                }
            }

            if (state.newModes.isNotEmpty()) {
                fullWidth {
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
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
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
            val labelRoom = segmentWidth - SEGMENT_CHROME
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

/** Horizontal padding, icon and gap inside a segment, around its label. */
private val SEGMENT_CHROME = 12.dp * 2 + 18.dp + 8.dp + 4.dp

@Composable
private fun ResumeCard(
    saved: SavedQuizInfo,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.action_resume_quiz),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.play_resume_detail,
                        stringResource(saved.modeLabel),
                        saved.categoryDisplayName,
                        saved.answeredCount
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.play_resume_dismiss))
            }
        }
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
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluralStringResource(all.countLabel, all.count, all.count),
                style = MaterialTheme.typography.bodyLarge
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
