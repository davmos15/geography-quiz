package com.geoquiz.app.data.geo

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoFormatException
import com.geoquiz.app.domain.map.GeoFormatException.Reason
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeoPart
import com.geoquiz.app.domain.map.GeoProperties
import com.geoquiz.app.domain.map.GeoPropertyDef
import com.geoquiz.app.domain.map.GeoPropertyType
import com.geoquiz.app.domain.map.GeometryKind
import java.nio.Buffer
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Decodes one GQGM map layer (`data/geo/FORMAT.md`, version 1; reference reader
 * `tools/geodata/geoformat.py`). Pure JVM, no Android types.
 *
 * Strict: a wrong magic or version, an unknown layer id, geometry kind or property type, a
 * short read, trailing bytes, invalid UTF-8, a duplicate feature id or a geometry rule broken
 * (too few points, a polygon starting with a hole, parts in a point layer, a point outside the
 * quantisation grid) throws [GeoFormatException]. Reserved part flag bits are ignored.
 */
object GeoLayerReader {

    /** "GQGM" read as a little-endian int. */
    const val MAGIC = 0x4D475147
    const val FORMAT_VERSION = 1

    private const val Q_MIN = -32768
    private const val Q_MAX = 32767
    private const val Q_RANGE = 65535.0

    /** Smallest possible encoded feature: empty id, bbox, label point, part count. */
    private const val MIN_FEATURE_BYTES = 2 + 8 + 4 + 1

    fun read(bytes: ByteArray): GeoLayer {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            Decoder(bytes, buf).decode()
        } catch (e: BufferUnderflowException) {
            throw GeoFormatException(Reason.TRUNCATED, "File ends early at byte ${buf.position()} of ${bytes.size}")
        }
    }

    private class Decoder(private val bytes: ByteArray, private val buf: ByteBuffer) {
        private val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        fun decode(): GeoLayer {
            if (buf.remaining() < 4 || buf.int != MAGIC) fail(Reason.BAD_MAGIC, "Not a GQGM file")
            val version = u16()
            if (version != FORMAT_VERSION) fail(Reason.UNSUPPORTED_VERSION, "Unsupported format version $version")
            val layerCode = u8()
            val layerId = GeoLayerId.fromCode(layerCode) ?: fail(Reason.UNKNOWN_LAYER, "Unknown layer id $layerCode")
            val kindCode = u8()
            val kind = GeometryKind.fromCode(kindCode) ?: fail(Reason.UNKNOWN_GEOMETRY_KIND, "Unknown geometry kind $kindCode")
            if (kind != layerId.kind) fail(Reason.MALFORMED, "Layer $layerId must be ${layerId.kind}, file says $kind")

            val minLon = buf.double
            val minLat = buf.double
            val maxLon = buf.double
            val maxLat = buf.double
            if (!(minLon.isFinite() && minLat.isFinite() && maxLon.isFinite() && maxLat.isFinite()) ||
                maxLon < minLon || maxLat < minLat
            ) {
                fail(Reason.MALFORMED, "Bad layer bounds")
            }
            val count = buf.int
            if (count < 0) fail(Reason.MALFORMED, "Feature count too large")
            val source = string()
            val propCount = u8()
            val schema = List(propCount) {
                val name = string()
                val typeCode = u8()
                val type = GeoPropertyType.fromCode(typeCode)
                    ?: fail(Reason.UNKNOWN_PROPERTY_TYPE, "Unknown property type $typeCode for $name")
                GeoPropertyDef(name, type)
            }
            if (count.toLong() * MIN_FEATURE_BYTES > buf.remaining()) {
                fail(Reason.TRUNCATED, "$count features can't fit in ${buf.remaining()} bytes")
            }

            val lonStep = (maxLon - minLon) / Q_RANGE
            val latStep = (maxLat - minLat) / Q_RANGE
            fun lonOf(q: Int): Double = minLon + (q + 32768) * lonStep
            fun latOf(q: Int): Double = minLat + (q + 32768) * latStep

            val ids = HashSet<String>(count * 2)
            val features = ArrayList<GeoFeature>(count)
            repeat(count) {
                val id = string()
                if (!ids.add(id)) fail(Reason.MALFORMED, "Duplicate feature id $id")
                val values = Array<Any>(schema.size) { i ->
                    when (schema[i].type) {
                        GeoPropertyType.STRING -> string()
                        GeoPropertyType.INT32 -> buf.int
                        GeoPropertyType.UINT8 -> u8()
                    }
                }
                val minX = buf.short.toInt()
                val minY = buf.short.toInt()
                val maxX = buf.short.toInt()
                val maxY = buf.short.toInt()
                val labelX = buf.short.toInt()
                val labelY = buf.short.toInt()
                val partCount = varint()
                if (kind == GeometryKind.POINT && partCount != 0) {
                    fail(Reason.MALFORMED, "Point feature $id has $partCount parts")
                }
                if (partCount < 0 || partCount > buf.remaining() / 2) {
                    fail(Reason.TRUNCATED, "Feature $id declares $partCount parts")
                }
                var cx = minX
                var cy = minY
                val parts = ArrayList<GeoPart>(partCount)
                for (p in 0 until partCount) {
                    val flags = u8()
                    val n = varint()
                    // Each point takes at least two bytes.
                    if (n < 0 || n > buf.remaining() / 2) fail(Reason.TRUNCATED, "Feature $id part $p declares $n points")
                    val minPoints = if (kind == GeometryKind.POLYGON) 3 else 2
                    if (n < minPoints) fail(Reason.MALFORMED, "Feature $id part $p has $n points")
                    if (kind == GeometryKind.POLYGON && p == 0 && flags and GeoPart.FLAG_HOLE != 0) {
                        fail(Reason.MALFORMED, "Feature $id starts with a hole")
                    }
                    val lon = FloatArray(n)
                    val lat = FloatArray(n)
                    for (i in 0 until n) {
                        cx += zigzag()
                        cy += zigzag()
                        if (cx < Q_MIN || cx > Q_MAX || cy < Q_MIN || cy > Q_MAX) {
                            fail(Reason.MALFORMED, "Feature $id leaves the quantisation grid")
                        }
                        lon[i] = lonOf(cx).toFloat()
                        lat[i] = latOf(cy).toFloat()
                    }
                    parts.add(GeoPart(flags, lon, lat))
                }
                features.add(
                    GeoFeature(
                        id = id,
                        properties = GeoProperties(schema, values),
                        bbox = GeoBounds(lonOf(minX), latOf(minY), lonOf(maxX), latOf(maxY)),
                        labelLon = lonOf(labelX),
                        labelLat = latOf(labelY),
                        parts = parts,
                    )
                )
            }
            if (buf.hasRemaining()) fail(Reason.TRAILING_BYTES, "${buf.remaining()} trailing bytes")
            return GeoLayer(
                id = layerId,
                kind = kind,
                bounds = GeoBounds(minLon, minLat, maxLon, maxLat),
                source = source,
                properties = schema,
                features = features,
            )
        }

        private fun u8(): Int = buf.get().toInt() and 0xFF

        private fun u16(): Int = buf.short.toInt() and 0xFFFF

        private fun string(): String {
            val n = u16()
            if (n > buf.remaining()) throw BufferUnderflowException()
            val start = buf.position()
            val text = try {
                utf8.reset()
                utf8.decode(ByteBuffer.wrap(bytes, start, n)).toString()
            } catch (e: CharacterCodingException) {
                fail(Reason.MALFORMED, "Invalid UTF-8 at byte $start")
            }
            // Through Buffer: ByteBuffer.position(int) returning ByteBuffer is missing on older Android.
            (buf as Buffer).position(start + n)
            return text
        }

        /** Unsigned LEB128, at most 5 bytes (32 bits). */
        private fun varint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = u8()
                // The 5th byte holds bits 28-31 only; anything above would not fit in 32 bits.
                if (shift == 28 && b and 0x70 != 0) fail(Reason.MALFORMED, "Varint exceeds 32 bits")
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift >= 35) fail(Reason.MALFORMED, "Varint longer than 5 bytes")
            }
        }

        private fun zigzag(): Int {
            val z = varint()
            return (z ushr 1) xor -(z and 1)
        }
    }

    private fun fail(reason: Reason, message: String): Nothing = throw GeoFormatException(reason, message)
}
