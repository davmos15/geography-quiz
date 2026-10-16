package com.geoquiz.app.ui.physicalfeatures

import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeature

/**
 * The map layers a round needs (task 7.2): unlike US states and Canadian provinces (always
 * `admin1_50m`) or Tap the map (always `admin0_*`), a Mountains round can mix mountain ranges
 * (`regions_50m`, polygon) and peaks (`geo_points_50m`, point), so this loads every distinct
 * [PhysicalFeature.layerId] the round's items use (at most two) up front, kept separate from the
 * quiz/choice state (D37's pattern: a tap, a submit or a pick never reloads or re-fits the map).
 *
 * [baseLayer] (`admin0_50m`) is loaded too, once per round, for real geographic context under
 * whichever feature is highlighted (task 7.2 review fix C2): drawn as non-playable/inactive land
 * (`PhysicalFeaturesGameScreen` passes an empty `members` set), never as this mode's own
 * "answerable" layer - there is no country to answer here. A failed [baseLayer] load is not
 * fatal (`null`): the screen falls back to showing the target feature alone, with no basemap
 * underneath, rather than hiding the whole round over one optional layer.
 */
data class PhysicalFeaturesMapState(
    val layersById: Map<GeoLayerId, GeoLayer> = emptyMap(),
    val baseLayer: GeoLayer? = null,
    /**
     * Layer ids this round's features asked for that [MapRepository.layer] failed to resolve
     * (task 7.2 review fix L1): tracked per layer rather than one round-wide flag, so a
     * Mountains round with a working `regions_50m` but a failed `geo_points_50m` shows the map
     * for range questions and the error text - never an endless spinner - only for peak
     * questions. [baseLayer]'s own load failure is not tracked here: it is optional context,
     * not a question-blocking layer (see [baseLayer]'s doc).
     */
    val failedLayerIds: Set<GeoLayerId> = emptySet()
) {
    val isLoaded: Boolean get() = layersById.isNotEmpty()
}

suspend fun loadPhysicalFeaturesMapState(
    mapRepository: MapRepository,
    features: List<PhysicalFeature>
): PhysicalFeaturesMapState {
    val ids = features.map { it.layerId }.distinct()
    val loaded = HashMap<GeoLayerId, GeoLayer>(ids.size)
    val failed = HashSet<GeoLayerId>()
    for (id in ids) {
        val layer = runCatching { mapRepository.layer(id) }.getOrNull()
        if (layer != null) loaded[id] = layer else failed.add(id)
    }
    val baseLayer = runCatching { mapRepository.layer(GeoLayerId.ADMIN0_50M) }.getOrNull()
    return PhysicalFeaturesMapState(layersById = loaded, baseLayer = baseLayer, failedLayerIds = failed)
}
