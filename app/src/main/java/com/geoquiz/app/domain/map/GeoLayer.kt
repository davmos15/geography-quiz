package com.geoquiz.app.domain.map

/**
 * One bundled map layer (`assets/geo/<file>.bin`, byte contract in `data/geo/FORMAT.md`),
 * decoded once and immutable. Coordinates are WGS 84 degrees (dequantised at load time);
 * project them with a [MapProjection] to draw.
 */
class GeoLayer(
    val id: GeoLayerId,
    val kind: GeometryKind,
    /** Extent of the layer's data, in degrees (the quantisation grid of the file). */
    val bounds: GeoBounds,
    /** Source description from the file header, e.g. "Natural Earth 5.1.2 ne_50m_admin_0_countries". */
    val source: String,
    val properties: List<GeoPropertyDef>,
    /** Features in file order, which is also draw order. */
    val features: List<GeoFeature>,
) {
    private val byId: Map<String, GeoFeature> by lazy { features.associateBy { it.id } }

    /** The feature with this id (unique within a layer), or null. */
    fun feature(id: String): GeoFeature? = byId[id]

    /** Ids of all features, in file order. */
    val featureIds: List<String> get() = features.map { it.id }
}

/** Layer ids from the header table in `data/geo/FORMAT.md`. */
enum class GeoLayerId(val code: Int, val fileName: String, val kind: GeometryKind) {
    /** Countries for the zoomed-out world view (110m geometry, 29 small countries from 50m). */
    ADMIN0_110M(1, "admin0_110m.bin", GeometryKind.POLYGON),
    /** Countries for zoomed-in views, regions and outlines. */
    ADMIN0_50M(2, "admin0_50m.bin", GeometryKind.POLYGON),
    /** Tap-zone centres for small countries. */
    TAP_ZONES(3, "tap_zones.bin", GeometryKind.POINT),
    /** US states + DC and Canadian provinces and territories. */
    ADMIN1_50M(4, "admin1_50m.bin", GeometryKind.POLYGON),
    /** Rivers, lake centrelines and canals. */
    RIVERS_50M(5, "rivers_50m.bin", GeometryKind.LINE),
    /** Lakes and reservoirs. */
    LAKES_50M(6, "lakes_50m.bin", GeometryKind.POLYGON),
    /** Physical regions (mountain ranges, deserts, plateaus, ...). */
    REGIONS_50M(7, "regions_50m.bin", GeometryKind.POLYGON),
    /** Peaks, depressions, passes, capes, islands, waterfalls, poles. */
    GEO_POINTS_50M(8, "geo_points_50m.bin", GeometryKind.POINT);

    /** Path inside the APK's assets. */
    val assetPath: String get() = "geo/$fileName"

    companion object {
        fun fromCode(code: Int): GeoLayerId? = entries.firstOrNull { it.code == code }
    }
}

enum class GeometryKind(val code: Int) {
    POINT(1), LINE(2), POLYGON(3);

    companion object {
        fun fromCode(code: Int): GeometryKind? = entries.firstOrNull { it.code == code }
    }
}

enum class GeoPropertyType(val code: Int) {
    STRING(1), INT32(2), UINT8(3);

    companion object {
        fun fromCode(code: Int): GeoPropertyType? = entries.firstOrNull { it.code == code }
    }
}

/** One column of the layer's property schema. */
data class GeoPropertyDef(val name: String, val type: GeoPropertyType)

/** A longitude/latitude rectangle in degrees. */
data class GeoBounds(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    val centreLon: Double get() = (minLon + maxLon) / 2
    val centreLat: Double get() = (minLat + maxLat) / 2
}

/**
 * One feature (country, state, river, lake, region or point).
 *
 * @property bbox bounding box of all parts, in degrees (for tap zones: the extent of the
 *   country's non-remote 50m parts).
 * @property labelLon label point (Natural Earth's label position; for point layers the point
 *   itself, for tap zones the tap centre).
 */
class GeoFeature(
    val id: String,
    val properties: GeoProperties,
    val bbox: GeoBounds,
    val labelLon: Double,
    val labelLat: Double,
    /** Empty for point layers. */
    val parts: List<GeoPart>,
) {
    /**
     * Admin-0 only: `false` for land that is not one of the app's 197 countries (Antarctica,
     * Greenland, dependencies, ...). `true` for features of layers without a `playable` property.
     */
    val isPlayable: Boolean get() = properties.intOrNull(PROP_PLAYABLE)?.let { it == 1 } ?: true

    /** Total number of points over all parts. */
    val pointCount: Int get() = parts.sumOf { it.size }

    companion object {
        const val PROP_PLAYABLE = "playable"
        const val PROP_NAME = "name"
    }
}

/**
 * A feature's property values, in the layer's schema order. Strings are [String], `i32` and
 * `u8` values are [Int].
 */
class GeoProperties(private val schema: List<GeoPropertyDef>, private val values: Array<Any>) {
    init {
        require(schema.size == values.size) { "Expected ${schema.size} values, got ${values.size}" }
    }

    private fun indexOf(name: String): Int = schema.indexOfFirst { it.name == name }

    operator fun contains(name: String): Boolean = indexOf(name) >= 0

    fun stringOrNull(name: String): String? = indexOf(name).takeIf { it >= 0 }?.let { values[it] as? String }

    fun intOrNull(name: String): Int? = indexOf(name).takeIf { it >= 0 }?.let { values[it] as? Int }

    fun string(name: String): String = requireNotNull(stringOrNull(name)) { "No string property $name" }

    fun int(name: String): Int = requireNotNull(intOrNull(name)) { "No int property $name" }

    /** Name to value, in schema order (for debugging and tests). */
    fun asMap(): Map<String, Any> = schema.indices.associate { schema[it].name to values[it] }
}

/**
 * One ring (polygon layers) or polyline (line layers). Rings are not closed: the last point
 * is not a copy of the first. Outer rings are counter-clockwise, holes clockwise (lon east,
 * lat north); draw with even-odd fill.
 *
 * Coordinates are kept as primitive arrays in degrees; floats hold the quantisation grid
 * (about 0.003-0.006 degrees for a world layer) with plenty to spare.
 */
class GeoPart(
    val flags: Int,
    val lon: FloatArray,
    val lat: FloatArray,
) {
    init {
        require(lon.size == lat.size) { "lon and lat sizes differ" }
    }

    val size: Int get() = lon.size

    /** Polygon layers: a hole in the outer ring before it. */
    val isHole: Boolean get() = flags and FLAG_HOLE != 0

    /** Admin-0: far from the feature's home territory (leave out when framing the feature). */
    val isRemote: Boolean get() = flags and FLAG_REMOTE != 0

    /**
     * Continues the feature across the antimeridian (e.g. Chukotka for Russia). Drawn where it
     * is on a world map; shift by 360 degrees to draw or frame the feature contiguously.
     */
    val isWrap: Boolean get() = flags and FLAG_WRAP != 0

    companion object {
        const val FLAG_HOLE = 0x01
        const val FLAG_REMOTE = 0x02
        const val FLAG_WRAP = 0x04
    }
}
