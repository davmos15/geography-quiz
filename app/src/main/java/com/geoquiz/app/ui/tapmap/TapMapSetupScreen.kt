package com.geoquiz.app.ui.tapmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import com.geoquiz.app.ui.components.DifficultySelector
import com.geoquiz.app.ui.components.WrappingTopAppBar

/**
 * Region, variant and difficulty picker for Tap the map (task 5.2, 5.2b). Starting navigates to
 * [TapMapGameScreen] via [onStart].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TapMapSetupScreen(
    onStart: (route: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TapMapSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_tap_map_name),
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
                    text = stringResource(R.string.mode_tap_map_setup_region_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.presets.forEach { preset ->
                        FilterChip(
                            selected = state.selectedPreset == preset,
                            onClick = { viewModel.onPresetSelected(preset) },
                            label = { Text(stringResource(preset.label)) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.mode_tap_map_setup_variant_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                TapMapVariantSelector(
                    selected = state.variant,
                    onSelect = viewModel::onVariantSelected
                )
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
                    options = state.availableDifficulties
                )
            }

            Button(
                onClick = { onStart(viewModel.buildStartRoute()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.mode_tap_map_setup_start))
            }
        }
    }
}

/**
 * Find vs Name all (task 5.2b): two full-width radio rows, each with a short, generic
 * description (L9: never names another game), so the choice reads clearly at every font size
 * (a column works at 100% and 200% alike, unlike a segmented row that would clip at large text).
 */
@Composable
private fun TapMapVariantSelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        TapMapGameMode.VARIANT_FIND to (R.string.mode_tap_map_variant_find to R.string.mode_tap_map_variant_find_summary),
        TapMapGameMode.VARIANT_NAME_ALL to
            (R.string.mode_tap_map_variant_name_all to R.string.mode_tap_map_variant_name_all_summary)
    )
    Column(modifier = modifier.fillMaxWidth().selectableGroup()) {
        options.forEach { (variant, labels) ->
            val (titleRes, summaryRes) = labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = variant == selected,
                        onClick = { onSelect(variant) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The row handles the tap and the semantics.
                RadioButton(selected = variant == selected, onClick = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(summaryRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
