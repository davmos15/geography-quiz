package com.geoquiz.app.ui.usca

import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.usca.usCaFrame

/**
 * The projection used for every US states / Canadian provinces round (task 6.2): there is no
 * [RegionPreset] for either (D43 review says not to extend it), so this reuses
 * [RegionPreset.NORTH_AMERICA]'s own Lambert azimuthal equal-area projection, centred well
 * enough for either country that a round's own frame (around just its members) does the rest of
 * the work.
 */
val US_CA_PROJECTION: MapProjection = RegionPreset.NORTH_AMERICA.projection

/** This round's map layer and frame, loaded once and kept separate from the quiz/choice state
 * (a tap, a submit or a pick should never reload or re-fit the map). */
data class UsCaRegionsMapState(
    val layer: GeoLayer? = null,
    val frame: PlaneRect? = null,
    val loadFailed: Boolean = false
) {
    val isLoaded: Boolean get() = layer != null
}

suspend fun loadUsCaRegionsMapState(mapRepository: MapRepository, members: Set<String>): UsCaRegionsMapState {
    val layer = runCatching { mapRepository.layer(GeoLayerId.ADMIN1_50M) }.getOrNull()
    val frame = layer?.let { usCaFrame(US_CA_PROJECTION, it, members) }
    return UsCaRegionsMapState(layer = layer, frame = frame, loadFailed = layer == null)
}
