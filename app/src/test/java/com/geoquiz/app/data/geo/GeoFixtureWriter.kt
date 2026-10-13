package com.geoquiz.app.data.geo

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hand-built GQGM files for reader tests, written byte by byte after `data/geo/FORMAT.md`
 * (independent of the reader, like `tools/geodata/geoformat.py`'s writer). Coordinates are
 * given as quantised i16 grid values.
 */
class GeoFixtureWriter(
    var magic: Int = GeoLayerReader.MAGIC,
    var version: Int = 1,
    var layerId: Int = 2,
    var kind: Int = 3,
    var bounds: DoubleArray = doubleArrayOf(-180.0, -90.0, 180.0, 90.0),
    var source: String = "Test fixture",
    /** Name to type code (1 string, 2 i32, 3 u8). */
    var properties: List<Pair<String, Int>> = listOf("name" to 1, "playable" to 3, "area_km2" to 2),
) {
    class Part(val flags: Int, val points: List<Pair<Int, Int>>)

    class Feature(
        val id: String,
        val values: List<Any>,
        val bbox: IntArray,
        val label: Pair<Int, Int>,
        val parts: List<Part>,
        /** Declared point count override per part (to write corrupt files). */
        val declaredPartCount: Int? = null,
    )

    val features = mutableListOf<Feature>()
    var declaredFeatureCount: Int? = null

    fun bytes(): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(size: Int, block: ByteBuffer.() -> Unit) {
            val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            b.block()
            out.write(b.array())
        }
        fun u8(v: Int) = out.write(v and 0xFF)
        fun u16(v: Int) = le(2) { putShort(v.toShort()) }
        fun i16(v: Int) = le(2) { putShort(v.toShort()) }
        fun i32(v: Int) = le(4) { putInt(v) }
        fun str(s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            u16(b.size)
            out.write(b)
        }
        fun varint(value: Int) = writeVarint(out, value)
        fun zigzag(n: Int) = varint((n shl 1) xor (n shr 31))

        i32(magic)
        u16(version)
        u8(layerId)
        u8(kind)
        bounds.forEach { d -> le(8) { putDouble(d) } }
        i32(declaredFeatureCount ?: features.size)
        str(source)
        u8(properties.size)
        properties.forEach { (name, type) -> str(name); u8(type) }
        for (f in features) {
            str(f.id)
            f.values.forEachIndexed { i, v ->
                when (properties[i].second) {
                    1 -> str(v as String)
                    2 -> i32(v as Int)
                    3 -> u8(v as Int)
                    else -> u8(v as Int)
                }
            }
            f.bbox.forEach { i16(it) }
            i16(f.label.first)
            i16(f.label.second)
            varint(f.declaredPartCount ?: f.parts.size)
            var cx = f.bbox[0]
            var cy = f.bbox[1]
            for (part in f.parts) {
                u8(part.flags)
                varint(part.points.size)
                for ((x, y) in part.points) {
                    zigzag(x - cx)
                    zigzag(y - cy)
                    cx = x
                    cy = y
                }
            }
        }
        return out.toByteArray()
    }

    companion object {
        fun writeVarint(out: ByteArrayOutputStream, value: Int) {
            var v = value
            while (true) {
                val low = v and 0x7F
                v = v ushr 7
                if (v == 0) {
                    out.write(low)
                    return
                }
                out.write(low or 0x80)
            }
        }

        /** Grid value of a longitude for the default bounds. */
        fun qLon(lon: Double, minLon: Double = -180.0, maxLon: Double = 180.0): Int =
            Math.floor((lon - minLon) / (maxLon - minLon) * 65535 + 0.5).toInt() - 32768

        fun qLat(lat: Double, minLat: Double = -90.0, maxLat: Double = 90.0): Int =
            Math.floor((lat - minLat) / (maxLat - minLat) * 65535 + 0.5).toInt() - 32768

        /**
         * A valid three-feature polygon layer covering the edge cases: a square with a hole,
         * a multi-part feature with negative deltas and multi-byte varints across the whole
         * grid, and a feature with remote and wrap parts plus a reserved flag bit.
         */
        fun sample(): GeoFixtureWriter = GeoFixtureWriter().apply {
            features += Feature(
                id = "AAA", values = listOf("Squareland", 1, 12345),
                bbox = intArrayOf(0, 0, 1000, 1000), label = 500 to 500,
                parts = listOf(
                    Part(0, listOf(0 to 0, 1000 to 0, 1000 to 1000, 0 to 1000)),
                    Part(GeoPart_HOLE, listOf(250 to 250, 250 to 750, 750 to 750, 750 to 250)),
                ),
            )
            features += Feature(
                id = "BBB", values = listOf("Édge Ïsles", 0, -7),
                bbox = intArrayOf(-32768, -32768, 32767, 32767), label = -32768 to 32767,
                parts = listOf(
                    // Deltas from the bbox corner: +65535 (3-byte varint), then large negatives.
                    Part(0, listOf(32767 to 32767, -32768 to 32767, -32768 to -32768)),
                    // Small negative deltas (1-byte zigzag) and a 2-byte one (+101).
                    Part(0, listOf(-32700 to -32700, -32701 to -32702, -32600 to -32650)),
                ),
            )
            features += Feature(
                id = "CCC", values = listOf("", 1, Int.MIN_VALUE),
                bbox = intArrayOf(100, 100, 300, 300), label = 200 to 200,
                parts = listOf(
                    Part(0, listOf(100 to 100, 200 to 100, 200 to 200)),
                    Part(0x02 or 0x80, listOf(250 to 250, 300 to 250, 300 to 300)),
                    Part(0x04, listOf(100 to 250, 150 to 250, 150 to 300)),
                ),
            )
        }

        private const val GeoPart_HOLE = 0x01
    }
}
