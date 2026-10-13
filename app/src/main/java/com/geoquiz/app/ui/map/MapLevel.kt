package com.geoquiz.app.ui.map

/** Which country geometry to draw. */
enum class MapLevel {
    /** `admin0_110m`: the zoomed-out world view. */
    COARSE,
    /** `admin0_50m`: zoomed in, regions and outlines. */
    DETAIL,
}

/** The caller's choice of level; [AUTO] switches on zoom. */
enum class MapLevelPreference { AUTO, COARSE, DETAIL }

/**
 * In [MapLevelPreference.AUTO], the 50m geometry is used from this many dp per projected
 * unit (about dp per radian near the projection's centre). A 360 dp phone shows the whole
 * Equal Earth world at about 66 dp per unit, so 50m takes over at roughly 2.5x zoom there,
 * and a continent view usually starts in 50m.
 */
const val DETAIL_LEVEL_DP_PER_UNIT = 160f

/**
 * The level to draw at [dpPerUnit] (the view's scale divided by screen density). Falls back
 * to [MapLevel.COARSE] while the detail layer isn't loaded, and to [MapLevel.DETAIL] if only
 * the detail layer is.
 */
fun resolveMapLevel(
    preference: MapLevelPreference,
    dpPerUnit: Float,
    coarseAvailable: Boolean,
    detailAvailable: Boolean,
): MapLevel {
    val wanted = when (preference) {
        MapLevelPreference.COARSE -> MapLevel.COARSE
        MapLevelPreference.DETAIL -> MapLevel.DETAIL
        MapLevelPreference.AUTO -> if (dpPerUnit >= DETAIL_LEVEL_DP_PER_UNIT) MapLevel.DETAIL else MapLevel.COARSE
    }
    return when {
        wanted == MapLevel.DETAIL && !detailAvailable -> MapLevel.COARSE
        wanted == MapLevel.COARSE && !coarseAvailable && detailAvailable -> MapLevel.DETAIL
        else -> wanted
    }
}
