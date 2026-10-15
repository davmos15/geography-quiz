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
 */
data class PhysicalFeaturesMapState(
    val layersById: Map<GeoLayerId, GeoLayer> = emptyMap(),
    val loadFailed: Boolean = false
) {
    val isLoaded: Boolean get() = layersById.isNotEmpty()
}

suspend fun loadPhysicalFeaturesMapState(
    mapRepository: MapRepository,
    features: List<PhysicalFeature>
): PhysicalFeaturesMapState {
    val ids = features.map { it.layerId }.distinct()
    val loaded = HashMap<GeoLayerId, GeoLayer>(ids.size)
    var anyFailed = false
    for (id in ids) {
        val layer = runCatching { mapRepository.layer(id) }.getOrNull()
        if (layer != null) loaded[id] = layer else anyFailed = true
    }
    return PhysicalFeaturesMapState(layersById = loaded, loadFailed = anyFailed && loaded.isEmpty())
}
