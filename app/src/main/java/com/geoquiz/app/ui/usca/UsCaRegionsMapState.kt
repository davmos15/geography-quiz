package com.geoquiz.app.ui.usca

import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.usCaFrame

/**
 * The projection used for a US states / Canadian provinces round (task 6.2): there is no
 * [RegionPreset] for either (D43 review says not to extend it), so this reuses
 * [RegionPreset.NORTH_AMERICA]'s own Lambert azimuthal equal-area projection, centred well
 * enough for either country that a round's own frame (around just its members) does the rest of
 * the work. Kept as the name every existing caller (production and test) already uses.
 */
val US_CA_PROJECTION: MapProjection = RegionPreset.NORTH_AMERICA.projection

/**
 * Australia's own Lambert azimuthal equal-area projection (P10-D2), centred roughly on the
 * continent (lon 134, lat -28, the same kind of hand-picked centre [RegionPreset.OCEANIA] uses
 * for the wider Pacific) so a round's frame (around just its 8 states/territories) does the
 * rest of the work, the same pattern as [US_CA_PROJECTION].
 */
val AU_STATES_PROJECTION: MapProjection = LambertAzimuthalEqualAreaProjection(134.0, -28.0)

/** The projection for [group]'s round (P10-D2): North America's for US states or Canadian
 * provinces, Australia's own for Australian states and territories. */
fun projectionForGroup(group: UsCaRegionGroup): MapProjection = when (group) {
    UsCaRegionGroup.US_STATES, UsCaRegionGroup.CANADIAN_PROVINCES -> US_CA_PROJECTION
    UsCaRegionGroup.AU_STATES -> AU_STATES_PROJECTION
}

/** This round's map layer, projection and frame, loaded once and kept separate from the
 * quiz/choice state (a tap, a submit or a pick should never reload or re-fit the map). */
data class UsCaRegionsMapState(
    val layer: GeoLayer? = null,
    val frame: PlaneRect? = null,
    val projection: MapProjection = US_CA_PROJECTION,
    val loadFailed: Boolean = false
) {
    val isLoaded: Boolean get() = layer != null
}

suspend fun loadUsCaRegionsMapState(
    mapRepository: MapRepository,
    members: Set<String>,
    group: UsCaRegionGroup
): UsCaRegionsMapState {
    val layer = runCatching { mapRepository.layer(GeoLayerId.ADMIN1_50M) }.getOrNull()
    val projection = projectionForGroup(group)
    val frame = layer?.let { usCaFrame(projection, it, members) }
    return UsCaRegionsMapState(layer = layer, frame = frame, projection = projection, loadFailed = layer == null)
}
