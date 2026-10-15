@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.geoquiz.app.ui.physicalfeatures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureTab
import com.geoquiz.app.ui.components.DifficultySelector
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.domain.model.Difficulty

/**
 * Rivers, mountains and lakes' setup screen (task 7.2), registered at
 * [com.geoquiz.app.ui.navigation.Screen.ModeSetup]: tab, optional continent filter and
 * difficulty. Tapping Start navigates via [onStart].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalFeaturesSetupScreen(
    onStart: (route: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PhysicalFeaturesSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_physical_features_name),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.physical_features_setup_tab_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.tab == PhysicalFeatureTab.RIVERS,
                        onClick = { viewModel.onTabSelected(PhysicalFeatureTab.RIVERS) },
                        label = { Text(stringResource(R.string.physical_features_tab_rivers)) }
                    )
                    FilterChip(
                        selected = state.tab == PhysicalFeatureTab.MOUNTAINS,
                        onClick = { viewModel.onTabSelected(PhysicalFeatureTab.MOUNTAINS) },
                        label = { Text(stringResource(R.string.physical_features_tab_mountains)) }
                    )
                    FilterChip(
                        selected = state.tab == PhysicalFeatureTab.LAKES,
                        onClick = { viewModel.onTabSelected(PhysicalFeatureTab.LAKES) },
                        label = { Text(stringResource(R.string.physical_features_tab_lakes)) }
                    )
                }
            }

            if (state.availableContinents.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.physical_features_setup_continent_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.continent == null,
                            onClick = { viewModel.onContinentSelected(null) },
                            label = { Text(stringResource(R.string.physical_features_continent_all)) }
                        )
                        state.availableContinents.forEach { continent ->
                            FilterChip(
                                selected = state.continent == continent,
                                onClick = { viewModel.onContinentSelected(continent) },
                                label = { Text(continent) }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.difficulty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                DifficultySelector(
                    selected = state.difficulty,
                    onSelect = viewModel::onDifficultySelected,
                    options = Difficulty.entries.toList()
                )
            }

            Button(onClick = { onStart(viewModel.buildStartRoute()) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.physical_features_setup_start))
            }
        }
    }
}
