package com.geoquiz.app.domain.map

/**
 * Loads the bundled map layers. Each layer is read from assets and decoded the first time it
 * is asked for (off the main thread), then cached for the life of the process.
 */
interface MapRepository {

    /**
     * The decoded layer. Throws [GeoFormatException] if the asset is corrupt and
     * [java.io.IOException] if it can't be read; a failed load is not cached, so a later call
     * tries again.
     */
    suspend fun layer(id: GeoLayerId): GeoLayer

    /** The layer if it has already been loaded, without loading it. */
    fun cachedLayer(id: GeoLayerId): GeoLayer?
}

/** A map asset that doesn't follow `data/geo/FORMAT.md`. */
class GeoFormatException(val reason: Reason, message: String) : java.io.IOException(message) {
    enum class Reason {
        BAD_MAGIC,
        UNSUPPORTED_VERSION,
        UNKNOWN_LAYER,
        UNKNOWN_GEOMETRY_KIND,
        UNKNOWN_PROPERTY_TYPE,
        /** The file ends before a value it declares. */
        TRUNCATED,
        TRAILING_BYTES,
        /** A value is out of range or breaks a geometry rule (duplicate id, too few points, ...). */
        MALFORMED,
        /** The file decoded but is not the layer that was asked for. */
        WRONG_LAYER,
    }
}
