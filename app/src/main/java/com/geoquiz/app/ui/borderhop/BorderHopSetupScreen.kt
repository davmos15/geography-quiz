package com.geoquiz.app.ui.borderhop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.category.QuizOptionInfo
import com.geoquiz.app.ui.components.DifficultySelector
import com.geoquiz.app.ui.components.ReadableWidthFrame
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.buttonSemantics

/**
 * Border hop's own setup screen (task 6.1), registered at
 * [com.geoquiz.app.ui.navigation.Screen.ModeSetup]: a difficulty tier (which sets the puzzle's
 * hop range, D44) then the filters this mode offers ([BorderHopSetupViewModel], built from the
 * Countries category list). Tapping an option starts the mode via [onStart].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorderHopSetupScreen(
    onStart: (route: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: BorderHopSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_border_hop_name),
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
                                onSelect = viewModel::onDifficultySelected,
                                summaryRes = { difficulty ->
                                    when (difficulty) {
                                        Difficulty.EASY -> R.string.border_hop_easy_summary
                                        Difficulty.NORMAL -> R.string.border_hop_normal_summary
                                        Difficulty.HARD -> R.string.border_hop_hard_summary
                                    }
                                }
                            )
                        }
                    }
                    item(key = "intro") {
                        Text(
                            text = stringResource(R.string.border_hop_setup_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    state.sections.forEach { section ->
                        item(key = "header-${section.titleRes}") {
                            Text(
                                text = stringResource(section.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 8.dp).semantics { heading() }
                            )
                        }
                        items(section.options, key = { "${section.titleRes}-${it.categoryType}-${it.categoryValue}" }) { option ->
                            BorderHopFilterCard(
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

@Composable
private fun BorderHopFilterCard(option: QuizOptionInfo, onClick: () -> Unit) {
    val countDescription = pluralStringResource(R.plurals.border_hop_filter_country_count, option.countryCount, option.countryCount)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .buttonSemantics(stringResource(R.string.border_hop_start_action), onClick),
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
