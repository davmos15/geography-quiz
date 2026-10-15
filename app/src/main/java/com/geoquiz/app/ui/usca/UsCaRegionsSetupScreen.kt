package com.geoquiz.app.ui.usca

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.ui.components.DifficultySelector
import com.geoquiz.app.ui.components.WrappingTopAppBar

/**
 * US states and Canadian provinces' setup screen (task 6.2), registered at
 * [com.geoquiz.app.ui.navigation.Screen.ModeSetup]: group, DC-optional checkbox (US only),
 * variant and difficulty. Tapping Start navigates via [onStart].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UsCaRegionsSetupScreen(
    onStart: (route: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: UsCaRegionsSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_us_ca_regions_name),
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
                    text = stringResource(R.string.us_ca_regions_setup_group_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.group == UsCaRegionGroup.US_STATES,
                        onClick = { viewModel.onGroupSelected(UsCaRegionGroup.US_STATES) },
                        label = { Text(stringResource(R.string.us_ca_regions_group_us)) }
                    )
                    FilterChip(
                        selected = state.group == UsCaRegionGroup.CANADIAN_PROVINCES,
                        onClick = { viewModel.onGroupSelected(UsCaRegionGroup.CANADIAN_PROVINCES) },
                        label = { Text(stringResource(R.string.us_ca_regions_group_ca)) }
                    )
                }
                if (state.group == UsCaRegionGroup.US_STATES) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = state.includeDc,
                                onClick = { viewModel.onIncludeDcChanged(!state.includeDc) },
                                role = Role.Checkbox
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = state.includeDc, onCheckedChange = null)
                        Text(
                            text = stringResource(R.string.us_ca_regions_include_dc),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.us_ca_regions_setup_variant_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                UsCaRegionsVariantSelector(selected = state.variant, onSelect = viewModel::onVariantSelected)
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

            Button(onClick = { onStart(viewModel.buildStartRoute()) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.us_ca_regions_setup_start))
            }
        }
    }
}

/**
 * Tap / Name all / Multiple choice (task 6.2): three full-width radio rows with a short, generic
 * description each (L9: never names another game), so the choice reads clearly at every font
 * size, matching `TapMapSetupScreen`'s variant selector.
 */
@Composable
private fun UsCaRegionsVariantSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        UsCaRegionsGameMode.VARIANT_TAP to
            (R.string.us_ca_regions_variant_tap to R.string.us_ca_regions_variant_tap_summary),
        UsCaRegionsGameMode.VARIANT_NAME_ALL to
            (R.string.us_ca_regions_variant_name_all to R.string.us_ca_regions_variant_name_all_summary),
        UsCaRegionsGameMode.VARIANT_CHOICE to
            (R.string.us_ca_regions_variant_choice to R.string.us_ca_regions_variant_choice_summary)
    )
    Column(modifier = modifier.fillMaxWidth().selectableGroup()) {
        options.forEach { (variant, labels) ->
            val (titleRes, summaryRes) = labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = variant == selected, onClick = { onSelect(variant) }, role = Role.RadioButton)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
