package com.geoquiz.app.ui.silhouettes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.ui.category.QuizOptionInfo
import com.geoquiz.app.ui.components.DifficultySelector
import com.geoquiz.app.ui.components.ReadableWidthFrame
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.buttonSemantics

/**
 * Country silhouettes' own setup screen (task 5.1), registered at
 * [com.geoquiz.app.ui.navigation.Screen.ModeSetup]: a difficulty tier then the filters this mode
 * offers ([SilhouettesSetupViewModel], built from the Countries category list,
 * `ui/category/CategoryOptionsBuilder.kt`). Tapping an option starts the mode via [onStart].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilhouettesSetupScreen(
    onStart: (route: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SilhouettesSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_silhouettes_name),
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
            // Every section but the first starts collapsed, so a long list of letters doesn't
            // bury "All countries" and "By region" (3.6: a plain Set survives rotation and, with
            // rememberSaveable, process death).
            var expanded by rememberSaveable {
                mutableStateOf(state.sections.firstOrNull()?.titleRes?.let { setOf(it) } ?: emptySet())
            }
            ReadableWidthFrame(modifier = Modifier.fillMaxSize().padding(padding)) { sideInset ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp + sideInset, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "difficulty") {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(
                                text = stringResource(R.string.difficulty_title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
                            )
                            DifficultySelector(
                                selected = state.difficulty,
                                onSelect = viewModel::onDifficultySelected
                            )
                        }
                    }
                    item(key = "intro") {
                        Text(
                            text = stringResource(R.string.silhouettes_setup_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    state.sections.forEach { section ->
                        val isExpanded = section.titleRes in expanded
                        item(key = "header-${section.titleRes}") {
                            SectionHeader(
                                titleRes = section.titleRes,
                                expanded = isExpanded,
                                onToggle = {
                                    expanded = if (isExpanded) expanded - section.titleRes else expanded + section.titleRes
                                }
                            )
                        }
                        if (isExpanded) {
                            items(section.options, key = { "${section.titleRes}-${it.categoryType}-${it.categoryValue}" }) { option ->
                                SilhouetteFilterCard(
                                    option = option,
                                    onClick = { onStart(viewModel.routeFor(option)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(titleRes: Int, expanded: Boolean, onToggle: () -> Unit) {
    val title = stringResource(titleRes)
    val actionLabel = stringResource(if (expanded) R.string.silhouettes_collapse_section else R.string.silhouettes_expand_section)
    // Surface's own onClick makes the whole row a real touch target; buttonSemantics only
    // replaces the TalkBack action label (see CategoryListScreen/PlayScreen for the same pairing).
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .buttonSemantics(actionLabel, onToggle),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun SilhouetteFilterCard(option: QuizOptionInfo, onClick: () -> Unit) {
    val countDescription = pluralStringResource(R.plurals.silhouettes_filter_country_count, option.countryCount, option.countryCount)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .buttonSemantics(stringResource(R.string.silhouettes_start_action), onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = option.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = countDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
