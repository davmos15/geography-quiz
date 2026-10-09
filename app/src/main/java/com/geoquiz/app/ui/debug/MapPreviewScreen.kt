package com.geoquiz.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.rememberReducedMotion
import com.geoquiz.app.ui.map.MapCanvas
import com.geoquiz.app.ui.map.MapLayerPaths
import com.geoquiz.app.ui.map.MapLevel
import com.geoquiz.app.ui.map.MapLevelPreference
import com.geoquiz.app.ui.map.MapScene
import com.geoquiz.app.ui.map.MapViewState
import com.geoquiz.app.ui.map.rememberMapScene
import com.geoquiz.app.ui.map.rememberMapViewState
import com.geoquiz.app.ui.map.resolveMapLevel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Debug-only world map with level, water-layer and demo-state toggles (task 4.2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapPreviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: MapPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapState = rememberMapViewState()
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val scene = rememberMapScene(
        projection = viewModel.projection,
        coarse = state.coarse,
        detail = state.detail,
        lakes = state.lakes,
        rivers = state.rivers,
        tapZones = state.tapZones,
    )

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.map_preview_title),
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (scene != null) {
                    MapCanvas(
                        scene = scene,
                        state = mapState,
                        modifier = Modifier.fillMaxSize(),
                        featureStates = state.featureStates,
                        levelPreference = state.level,
                        showLakes = state.showLakes,
                        showRivers = state.showRivers,
                        onTapProjected = viewModel::onTap,
                        onFeatureTap = viewModel::onFeatureTap,
                    )
                } else if (state.error == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.map_preview_loading), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val levels = listOf(
                    MapLevelPreference.AUTO to R.string.map_preview_level_auto,
                    MapLevelPreference.COARSE to R.string.map_preview_level_110m,
                    MapLevelPreference.DETAIL to R.string.map_preview_level_50m,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    levels.forEachIndexed { index, (level, label) ->
                        SegmentedButton(
                            selected = state.level == level,
                            onClick = { viewModel.onLevelChange(level) },
                            shape = SegmentedButtonDefaults.itemShape(index, levels.size)
                        ) { Text(stringResource(label)) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.showLakes,
                        onClick = { viewModel.onShowLakesChange(!state.showLakes) },
                        label = { Text(stringResource(R.string.map_preview_lakes)) }
                    )
                    FilterChip(
                        selected = state.showRivers,
                        onClick = { viewModel.onShowRiversChange(!state.showRivers) },
                        label = { Text(stringResource(R.string.map_preview_rivers)) }
                    )
                    FilterChip(
                        selected = state.showDemoStates,
                        onClick = { viewModel.onShowDemoStatesChange(!state.showDemoStates) },
                        label = { Text(stringResource(R.string.map_preview_demo_states)) }
                    )
                    FilterChip(
                        selected = state.showFrameRate,
                        onClick = { viewModel.onShowFrameRateChange(!state.showFrameRate) },
                        label = { Text(stringResource(R.string.map_preview_frame_rate_toggle)) }
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val bounds = viewModel.australiaBounds() ?: return@OutlinedButton
                            scope.launch {
                                mapState.fitTo(bounds, paddingPx = with(density) { 24.dp.toPx() }, animate = !reducedMotion)
                            }
                        }
                    ) { Text(stringResource(R.string.map_preview_fit_australia)) }
                    OutlinedButton(onClick = { mapState.reset() }) {
                        Text(stringResource(R.string.map_preview_reset_view))
                    }
                }

                val infoStyle = MaterialTheme.typography.bodySmall
                if (scene != null) ZoomReadout(mapState, scene, state.level)
                if (state.showFrameRate) FrameRateReadout()
                if (state.hasHit) {
                    val hit = state.lastHit
                    Text(
                        when {
                            hit == null -> stringResource(R.string.map_preview_hit_water)
                            else -> stringResource(
                                R.string.map_preview_hit,
                                hit.featureId,
                                state.lastHitName.orEmpty(),
                                stringResource(if (hit.isPlayable) R.string.map_preview_hit_playable else R.string.map_preview_hit_not_playable),
                                stringResource(if (hit.viaTapZone) R.string.map_preview_hit_zone else R.string.map_preview_hit_shape),
                                (state.featureStates[hit.featureId] ?: com.geoquiz.app.domain.map.MapFeatureState.Default).name,
                            )
                        },
                        style = infoStyle
                    )
                }
                if (state.hasTapped) {
                    val lon = state.lastTapLon
                    val lat = state.lastTapLat
                    Text(
                        if (lon != null && lat != null) stringResource(R.string.map_preview_last_tap, lon, lat)
                        else stringResource(R.string.map_preview_last_tap_outside),
                        style = infoStyle
                    )
                }
                val paths = mapOf(
                    GeoLayerId.ADMIN0_110M to scene?.coarse,
                    GeoLayerId.ADMIN0_50M to scene?.detail,
                    GeoLayerId.LAKES_50M to scene?.lakes,
                    GeoLayerId.RIVERS_50M to scene?.rivers,
                )
                state.loadMillis.forEach { (id, millis) ->
                    Text(
                        stringResource(R.string.map_preview_timing, id.fileName, millis, paths[id].buildLabel()),
                        style = infoStyle
                    )
                }
                state.error?.let {
                    Text(
                        stringResource(R.string.map_preview_error, it),
                        style = infoStyle,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Zoom and drawn level. Reads the camera through [derivedStateOf] (zoom rounded to 0.1) so
 * it recomposes only when the shown text changes, not on every gesture frame.
 */
@Composable
private fun ZoomReadout(mapState: MapViewState, scene: MapScene, levelPreference: MapLevelPreference) {
    val density = LocalDensity.current.density
    val zoomTenths by remember(mapState) {
        derivedStateOf { if (mapState.isReady) (mapState.zoom * 10).roundToInt() else -1 }
    }
    val level by remember(mapState, scene, levelPreference, density) {
        derivedStateOf {
            resolveMapLevel(
                levelPreference,
                dpPerUnit = mapState.scale / density,
                coarseAvailable = scene.coarse != null,
                detailAvailable = scene.detail != null,
            )
        }
    }
    if (zoomTenths < 0) return
    val levelLabel = stringResource(
        if (level == MapLevel.DETAIL) R.string.map_preview_level_50m else R.string.map_preview_level_110m
    )
    Text(
        stringResource(R.string.map_preview_zoom, zoomTenths / 10f, levelLabel),
        style = MaterialTheme.typography.bodySmall
    )
}

private fun MapLayerPaths?.buildLabel(): String = this?.buildMillis?.toString() ?: "–"

/**
 * Frames per second over the last half second, for a rough on-device check of pan and zoom.
 * It requests every frame while shown, so turn it off for battery or idle checks.
 */
@Composable
private fun FrameRateReadout() {
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var windowStart = withFrameNanos { it }
        var frames = 0
        while (true) {
            val now = withFrameNanos { it }
            frames++
            val elapsed = now - windowStart
            if (elapsed >= 500_000_000L) {
                fps = (frames * 1_000_000_000L / elapsed).toInt()
                frames = 0
                windowStart = now
            }
        }
    }
    Text(stringResource(R.string.map_preview_frame_rate, fps), style = MaterialTheme.typography.bodySmall)
}
