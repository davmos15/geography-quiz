package com.geoquiz.app.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.di.DefaultDispatcher
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.map.frame
import com.geoquiz.app.domain.map.planeBounds
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.GetRegionMembersUseCase
import com.geoquiz.app.ui.map.MapLevelPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Debug-only map preview (task 4.2): loads the world layers and holds the toggles. */
@HiltViewModel
class MapPreviewViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val getRegionMembers: GetRegionMembersUseCase,
    private val countryRepository: CountryRepository,
    /** Where [RegionPreset.frame] (CPU-bound: walks every member's vertices) runs; see [onPresetChange]. */
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** The current preset's projection. */
    val projection: MapProjection get() = _uiState.value.preset.projection

    private val _uiState = MutableStateFlow(MapPreviewUiState())
    val uiState: StateFlow<MapPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadLayers() }
        viewModelScope.launch {
            countryRepository.getAllCountries().collect { countries ->
                _uiState.update { it.copy(allCountries = countries) }
            }
        }
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
            _uiState.update { it.withLayer(layer).copy(loadMillis = it.loadMillis + (id to millis)).withFrame() }
        }
    }

    /** The most recently requested preset, so a slower, superseded request's result is dropped. */
    private var latestPresetRequest: RegionPreset = RegionPreset.WORLD

    /**
     * Switches the region preset: its members, projection and frame.
     *
     * Published as one state update with preset, members and frame already resolved (see
     * [MapViewState.updateContent][com.geoquiz.app.ui.map.MapViewState.updateContent]'s doc):
     * publishing an interim state with the new preset but a stale or null frame, followed by a
     * second update with the real frame, let [com.geoquiz.app.ui.map.MapCanvas] see two updates
     * under the one (new) projection key - the second only clamps the still-unmoved camera into
     * the real frame instead of centring on it, which could clip the region. [RegionPreset.frame]
     * itself (walking every member's vertices) runs on [defaultDispatcher], off the caller.
     */
    fun onPresetChange(preset: RegionPreset) {
        latestPresetRequest = preset
        if (preset == RegionPreset.WORLD) {
            _uiState.update { it.copy(preset = preset, members = null, frame = null) }
            return
        }
        viewModelScope.launch {
            val members = getRegionMembers(preset)
            val layer = _uiState.value.detail ?: _uiState.value.coarse
            val frame = layer?.let { l -> withContext(defaultDispatcher) { preset.frame(l, members) } }
            if (latestPresetRequest != preset) return@launch
            _uiState.update { state ->
                if (latestPresetRequest != preset) state else state.copy(preset = preset, members = members, frame = frame)
            }
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

    /**
     * A tap resolved to a feature (or null for water): records it and moves it to the next
     * state (Default, Found, Wrong, Highlighted, Start, End, then back to Default).
     */
    fun onFeatureTap(hit: MapHit?) {
        _uiState.update { state ->
            if (hit == null) return@update state.copy(lastHit = null, hasHit = true)
            val current = state.featureStates[hit.featureId] ?: MapFeatureState.Default
            val next = STATE_CYCLE[(STATE_CYCLE.indexOf(current) + 1) % STATE_CYCLE.size]
            state.copy(
                hasHit = true,
                lastHit = hit,
                lastHitName = (state.detail ?: state.coarse)?.feature(hit.featureId)?.properties?.stringOrNull(GeoFeature.PROP_NAME),
                tapStates = state.tapStates + (hit.featureId to next),
            )
        }
    }

    /**
     * A row picked in [com.geoquiz.app.ui.map.MapListAlternative]: the same path as tapping
     * that country's shape (task 4.6), reported as a shape hit (not via a tap zone) on a
     * playable country.
     */
    fun onListSelect(code: String) = onFeatureTap(MapHit(featureId = code, isPlayable = true, viaTapZone = false))

    /** Plane bounds of mainland Australia (remote islands left out), or null before 50m/110m load. */
    fun australiaBounds(): PlaneRect? {
        val state = _uiState.value
        val feature = (state.detail ?: state.coarse)?.feature(AUSTRALIA) ?: return null
        return projection.planeBounds(feature, includeRemote = false)
    }

    companion object {
        const val AUSTRALIA = "AUS"

        val PRESETS = RegionPreset.entries

        val PREVIEW_LAYERS = listOf(
            GeoLayerId.ADMIN0_110M, GeoLayerId.TAP_ZONES, GeoLayerId.ADMIN0_50M, GeoLayerId.LAKES_50M, GeoLayerId.RIVERS_50M,
        )

        val STATE_CYCLE = listOf(
            MapFeatureState.Default, MapFeatureState.Found, MapFeatureState.Wrong,
            MapFeatureState.Highlighted, MapFeatureState.Start, MapFeatureState.End,
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
    val tapZones: GeoLayer? = null,
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
    /** True once a tap has been resolved (to a feature or water). */
    val hasHit: Boolean = false,
    /** Null after a tap on water. */
    val lastHit: MapHit? = null,
    val lastHitName: String? = null,
    /** States set by tapping; they win over the demo states. */
    val tapStates: Map<String, MapFeatureState> = emptyMap(),
    val preset: RegionPreset = RegionPreset.WORLD,
    /** The preset's member codes; null for the world (every country) or while loading. */
    val members: Set<String>? = null,
    /** The preset's frame in its projection; null for the world or until members and a country layer are ready. */
    val frame: PlaneRect? = null,
    /** Every country from [CountryRepository], for the task 4.6 list alternative; empty while loading. */
    val allCountries: List<Country> = emptyList(),
) {
    /** Recomputes [frame] from the members and the most detailed country layer loaded. */
    fun withFrame(): MapPreviewUiState {
        val layer = detail ?: coarse
        val m = members
        return if (layer == null || m == null || preset == RegionPreset.WORLD) this else copy(frame = preset.frame(layer, m))
    }

    val featureStates: Map<String, MapFeatureState>
        get() = (if (showDemoStates) MapPreviewViewModel.DEMO_STATES else emptyMap()) + tapStates

    /** [allCountries] narrowed to the current preset (every country for [RegionPreset.WORLD]). */
    val countries: List<Country>
        get() = allCountries.filter { preset.includes(it) }

    val isLoading: Boolean get() = coarse == null && error == null

    fun withLayer(layer: GeoLayer): MapPreviewUiState = when (layer.id) {
        GeoLayerId.ADMIN0_110M -> copy(coarse = layer)
        GeoLayerId.ADMIN0_50M -> copy(detail = layer)
        GeoLayerId.LAKES_50M -> copy(lakes = layer)
        GeoLayerId.RIVERS_50M -> copy(rivers = layer)
        GeoLayerId.TAP_ZONES -> copy(tapZones = layer)
        else -> this
    }
}
