package com.geoquiz.app.domain.map

import androidx.annotation.StringRes
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Country

/**
 * A map view of the world or one continent: which countries belong (from the app's country
 * data, the same regions and subregions as the quiz categories, not from the map files), the
 * projection, and how the frame is padded.
 *
 * Continents use Lambert azimuthal equal-area centred on the region. Its trigonometry wraps
 * longitude, so countries across the antimeridian (Fiji, Kiribati, New Zealand's Chatham
 * Islands, Russia's Chukotka, the Aleutians) stay in one piece without shifting parts by
 * 360 degrees; land more than [LambertAzimuthalEqualAreaProjection.maxDistanceDeg] from the
 * centre is culled.
 */
enum class RegionPreset(
    val id: String,
    @StringRes val label: Int,
    val projection: MapProjection,
    /** Padding round the members' bounds, as a fraction of the larger side of those bounds. */
    val paddingFraction: Float = DEFAULT_PADDING,
    /**
     * Only vertices with longitude in this range count towards the frame. Europe uses it
     * because the data puts all of Russia in Europe: the frame stops at the Urals (60°E)
     * instead of reaching the Bering Strait. Null: every vertex counts.
     */
    val frameLonRange: ClosedFloatingPointRange<Double>? = null,
) {
    WORLD("world", R.string.region_world, EqualEarthProjection, paddingFraction = 0f),
    AFRICA("africa", R.string.region_africa, LambertAzimuthalEqualAreaProjection(17.0, 3.0)),
    ASIA("asia", R.string.region_asia, LambertAzimuthalEqualAreaProjection(90.0, 28.0)),
    EUROPE(
        "europe", R.string.region_europe, LambertAzimuthalEqualAreaProjection(15.0, 54.0),
        frameLonRange = -32.0..60.0,
    ),
    NORTH_AMERICA("north_america", R.string.region_north_america, LambertAzimuthalEqualAreaProjection(-100.0, 45.0)),
    OCEANIA("oceania", R.string.region_oceania, LambertAzimuthalEqualAreaProjection(162.0, -16.0)),
    SOUTH_AMERICA("south_america", R.string.region_south_america, LambertAzimuthalEqualAreaProjection(-60.0, -20.0));

    /** Whether [country] belongs to this preset (region and subregion strings from the data). */
    fun includes(country: Country): Boolean = when (this) {
        WORLD -> true
        AFRICA -> country.region.equals("Africa", ignoreCase = true)
        ASIA -> country.region.equals("Asia", ignoreCase = true)
        EUROPE -> country.region.equals("Europe", ignoreCase = true)
        OCEANIA -> country.region.equals("Oceania", ignoreCase = true)
        NORTH_AMERICA -> NORTH_AMERICA_SUBREGIONS.any { country.subregion.equals(it, ignoreCase = true) }
        SOUTH_AMERICA -> country.subregion.equals("South America", ignoreCase = true)
    }

    /** Pan margin round the frame, as a fraction of the frame (context land to look round). */
    val panMarginFraction: Float get() = if (this == WORLD) 0f else DEFAULT_PAN_MARGIN

    companion object {
        const val DEFAULT_PADDING = 0.03f
        const val DEFAULT_PAN_MARGIN = 0.25f

        /** North America = subregions North America, Central America and Caribbean. */
        val NORTH_AMERICA_SUBREGIONS = listOf("North America", "Central America", "Caribbean")

        fun fromId(id: String): RegionPreset? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The plane rectangle a preset frames: the union of the members' bounds in the preset's
 * projection, leaving out parts flagged remote (far-flung islands; a member whose parts are
 * all remote counts in full) and, for Europe, vertices east of the Urals; then padded by
 * [RegionPreset.paddingFraction]. Members missing from [layer] are skipped. Null if no member
 * is in the layer.
 */
fun RegionPreset.frame(layer: GeoLayer, memberCodes: Set<String>): PlaneRect? {
    val acc = BoundsAccumulator()
    val scratch = DoubleArray(2)
    val lonRange = frameLonRange
    for (code in memberCodes) {
        val feature = layer.feature(code) ?: continue
        val allRemote = feature.parts.all { it.isRemote }
        for (part in feature.parts) {
            if (part.isRemote && !allRemote) continue
            for (i in 0 until part.size) {
                val lon = part.lon[i].toDouble()
                val lat = part.lat[i].toDouble()
                if (lonRange != null && lon !in lonRange) continue
                if (!projection.isVisible(lon, lat)) continue
                acc.add(projection, lon, lat, scratch)
            }
        }
    }
    val rect = acc.rect() ?: return null
    return rect.inflate(maxOf(rect.width, rect.height) * paddingFraction)
}
