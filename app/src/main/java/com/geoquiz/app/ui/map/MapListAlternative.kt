package com.geoquiz.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.ui.theme.geoColors

/** Which view a map screen shows; see [MapOrListToggle]. */
enum class MapViewMode { Map, List }

/**
 * A segmented "Map"/"List" switch a map screen places in its top bar's actions (task 4.6). Pair
 * it with [rememberIsTouchExplorationEnabled] so a screen defaults to [MapListAlternative] when
 * TalkBack (or another touch-exploration service) is already on.
 */
@Composable
fun MapOrListToggle(
    mode: MapViewMode,
    onModeChange: (MapViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        val options = listOf(MapViewMode.Map to R.string.map_view_mode_map, MapViewMode.List to R.string.map_view_mode_list)
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

/**
 * The list alternative to [MapCanvas] (task 4.6): every one of [countries] as a 48 dp button
 * row, so every map interaction has a TalkBack path with no need to locate a shape on a canvas.
 * A row merges its name and state into one spoken description (never colour alone): Found shows
 * a tick and the word "Found", Wrong a cross and "Wrong"; Highlighted, Start and End are
 * labelled with no icon (there is no map marker simple enough to miniaturise here). Selecting a
 * row calls [onSelect] with its cca3 code, exactly as tapping that country on the map would.
 *
 * [showSearch] adds a field that filters by name, normalised the same way as typed answers (so
 * "cote d'ivoire" matches "Côte d'Ivoire").
 */
@Composable
fun MapListAlternative(
    countries: List<Country>,
    featureStates: Map<String, MapFeatureState>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    showSearch: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalize = remember { NormalizeInputUseCase() }
    val sorted = remember(countries) { countries.sortedBy { it.name } }
    val filtered = remember(sorted, query, normalize) {
        val needle = normalize(query)
        if (needle.isBlank()) sorted else sorted.filter { normalize(it.name).contains(needle) }
    }

    Column(modifier = modifier) {
        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MAP_LIST_SEARCH_FIELD_TAG)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.map_list_search_label)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.map_list_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        mapListBody(filtered, featureStates, onSelect, listState)
    }
}

/** Separate so the [LazyColumn] always gets the remaining [ColumnScope] weight, even when empty. */
@Composable
private fun ColumnScope.mapListBody(
    filtered: List<Country>,
    featureStates: Map<String, MapFeatureState>,
    onSelect: (String) -> Unit,
    listState: LazyListState,
) {
    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxSize()) {
        items(filtered, key = { it.code }) { country ->
            MapListRow(
                country = country,
                state = featureStates[country.code] ?: MapFeatureState.Default,
                onSelect = onSelect,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun MapListRow(
    country: Country,
    state: MapFeatureState,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { onSelect(country.code) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MapListStateIcon(state)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = country.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        mapListStateLabel(state)?.let { label ->
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A tick, a cross, or nothing (kept the same width so names still line up). */
@Composable
private fun MapListStateIcon(state: MapFeatureState) {
    when (state) {
        MapFeatureState.Found -> Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.geoColors.correct,
            modifier = Modifier.size(24.dp).testTag(MAP_LIST_FOUND_ICON_TAG),
        )
        MapFeatureState.Wrong -> Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = MaterialTheme.geoColors.wrong,
            modifier = Modifier.size(24.dp).testTag(MAP_LIST_WRONG_ICON_TAG),
        )
        MapFeatureState.Default, MapFeatureState.Highlighted, MapFeatureState.Start, MapFeatureState.End ->
            Spacer(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun mapListStateLabel(state: MapFeatureState): String? = when (state) {
    MapFeatureState.Default -> null
    MapFeatureState.Found -> stringResource(R.string.map_list_state_found)
    MapFeatureState.Wrong -> stringResource(R.string.map_list_state_wrong)
    MapFeatureState.Highlighted -> stringResource(R.string.map_list_state_highlighted)
    MapFeatureState.Start -> stringResource(R.string.map_list_state_start)
    MapFeatureState.End -> stringResource(R.string.map_list_state_end)
}

/** Test hooks (not read by TalkBack; the row's merged name and state text already cover that). */
const val MAP_LIST_SEARCH_FIELD_TAG = "map_list_search_field"
const val MAP_LIST_FOUND_ICON_TAG = "map_list_found_icon"
const val MAP_LIST_WRONG_ICON_TAG = "map_list_wrong_icon"
