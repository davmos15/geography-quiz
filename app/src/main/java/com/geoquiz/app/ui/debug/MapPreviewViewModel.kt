package com.geoquiz.app.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.domain.map.EqualEarthProjection
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.planeBounds
import com.geoquiz.app.ui.map.MapLevelPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Debug-only map preview (task 4.2): loads the world layers and holds the toggles. */
@HiltViewModel
class MapPreviewViewModel @Inject constructor(
    private val mapRepository: MapRepository,
) : ViewModel() {

    val projection: MapProjection = EqualEarthProjection

    private val _uiState = MutableStateFlow(MapPreviewUiState())
    val uiState: StateFlow<MapPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadLayers() }
    }

    private suspend fun loadLayers() {
        for (id in PREVIEW_LAYERS) {
            val start = System.nanoTime()
            val layer = try {
                mapRepository.layer(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "${id.fileName}: ${e.message ?: e::class.simpleName}") }
                continue
            }
            val millis = (System.nanoTime() - start) / 1_000_000
            _uiState.update { it.withLayer(layer).copy(loadMillis = it.loadMillis + (id to millis)) }
        }
    }

    fun onLevelChange(level: MapLevelPreference) = _uiState.update { it.copy(level = level) }

    fun onShowLakesChange(show: Boolean) = _uiState.update { it.copy(showLakes = show) }

    fun onShowRiversChange(show: Boolean) = _uiState.update { it.copy(showRivers = show) }

    fun onShowDemoStatesChange(show: Boolean) = _uiState.update { it.copy(showDemoStates = show) }

    fun onShowFrameRateChange(show: Boolean) = _uiState.update { it.copy(showFrameRate = show) }

    /** A tap in projected coordinates; records the longitude and latitude under it. */
    fun onTap(x: Float, y: Float) {
        val lonLat = projection.inverse(x.toDouble(), y.toDouble())
        _uiState.update { it.copy(hasTapped = true, lastTapLon = lonLat?.x, lastTapLat = lonLat?.y) }
    }

    /** Plane bounds of mainland Australia (remote islands left out), or null before 50m/110m load. */
    fun australiaBounds(): PlaneRect? {
        val state = _uiState.value
        val feature = (state.detail ?: state.coarse)?.feature(AUSTRALIA) ?: return null
        return projection.planeBounds(feature, includeRemote = false)
    }

    companion object {
        const val AUSTRALIA = "AUS"

        val PREVIEW_LAYERS = listOf(
            GeoLayerId.ADMIN0_110M, GeoLayerId.ADMIN0_50M, GeoLayerId.LAKES_50M, GeoLayerId.RIVERS_50M,
        )

        /** One or two countries per state, spread over the world. */
        val DEMO_STATES: Map<String, MapFeatureState> = mapOf(
            "AUS" to MapFeatureState.Found,
            "BRA" to MapFeatureState.Found,
            "CAN" to MapFeatureState.Found,
            "NZL" to MapFeatureState.Found,
            "IDN" to MapFeatureState.Wrong,
            "DZA" to MapFeatureState.Wrong,
            "PNG" to MapFeatureState.Highlighted,
            "IND" to MapFeatureState.Highlighted,
            "FRA" to MapFeatureState.Start,
            "JPN" to MapFeatureState.End,
        )
    }
}

data class MapPreviewUiState(
    val coarse: GeoLayer? = null,
    val detail: GeoLayer? = null,
    val lakes: GeoLayer? = null,
    val rivers: GeoLayer? = null,
    /** Time to read and decode each layer (first load only; later loads hit the cache). */
    val loadMillis: Map<GeoLayerId, Long> = emptyMap(),
    val error: String? = null,
    val level: MapLevelPreference = MapLevelPreference.AUTO,
    val showLakes: Boolean = true,
    val showRivers: Boolean = true,
    val showDemoStates: Boolean = true,
    val showFrameRate: Boolean = false,
    val hasTapped: Boolean = false,
    /** Null after a tap off the edge of the map. */
    val lastTapLon: Double? = null,
    val lastTapLat: Double? = null,
) {
    val featureStates: Map<String, MapFeatureState>
        get() = if (showDemoStates) MapPreviewViewModel.DEMO_STATES else emptyMap()

    val isLoading: Boolean get() = coarse == null && error == null

    fun withLayer(layer: GeoLayer): MapPreviewUiState = when (layer.id) {
        GeoLayerId.ADMIN0_110M -> copy(coarse = layer)
        GeoLayerId.ADMIN0_50M -> copy(detail = layer)
        GeoLayerId.LAKES_50M -> copy(lakes = layer)
        GeoLayerId.RIVERS_50M -> copy(rivers = layer)
        else -> this
    }
}
