package com.geoquiz.app.ui.map

import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.map.TapZones
import com.geoquiz.app.domain.map.resolveTap

/** Tap-zone circles are this wide on screen at any zoom (a 44 dp touch target). */
const val TAP_ZONE_DIAMETER_DP = 44f

/** A country from `tap_zones.bin` keeps its zone until its largest piece is this big on screen. */
const val TAP_ZONE_LISTED_MAX_DP = 44f

/** Any other playable country gets a zone while its largest piece is under this on screen. */
const val TAP_ZONE_OTHER_MAX_DP = 24f

/** Zone centres closer than this count as a tie, settled by which polygon holds the tap. */
const val TAP_ZONE_TIE_DP = 6f

/**
 * Zone thresholds converted to plane units for one frame or tap ([scale] px per plane unit,
 * [density] px per dp).
 */
internal class ZoneMetrics(scale: Float, density: Float) {
    val radius = TAP_ZONE_DIAMETER_DP / 2 * density / scale
    val listedMax = TAP_ZONE_LISTED_MAX_DP * density / scale
    val otherMax = TAP_ZONE_OTHER_MAX_DP * density / scale
    val tie = TAP_ZONE_TIE_DP * density / scale
}

/** Whether zone [i] is drawn and tappable at [scale] px per plane unit. */
internal fun TapZones.visibleAt(i: Int, scale: Float, density: Float): Boolean =
    visible(i, TAP_ZONE_LISTED_MAX_DP * density / scale, TAP_ZONE_OTHER_MAX_DP * density / scale)

/**
 * What a tap at screen ([screenX], [screenY]) hits with the camera in [state], using the
 * country layer drawn at the current zoom: tap zones (with the neighbour rule of [resolveTap]),
 * then polygons, else null (water).
 * [density] is px per dp.
 */
fun MapScene.hitTest(
    state: MapViewState,
    screenX: Float,
    screenY: Float,
    density: Float,
    levelPreference: MapLevelPreference = MapLevelPreference.AUTO,
    useTapZones: Boolean = true,
): MapHit? {
    if (!state.isReady) return null
    val scale = state.scale
    val level = resolveMapLevel(levelPreference, scale / density, coarse != null, detail != null)
    val land = land(level) ?: return null
    val index = land.hitIndex ?: return null
    val m = ZoneMetrics(scale, density)
    return resolveTap(
        index, if (useTapZones) zones(level) else null, state.screenToPlaneX(screenX), state.screenToPlaneY(screenY),
        zoneRadius = m.radius, alwaysMaxExtent = m.listedMax, otherMaxExtent = m.otherMax, tieDistance = m.tie,
    )
}
