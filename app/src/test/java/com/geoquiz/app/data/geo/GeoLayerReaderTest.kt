package com.geoquiz.app.data.geo

import com.geoquiz.app.data.geo.GeoFixtureWriter.Feature
import com.geoquiz.app.data.geo.GeoFixtureWriter.Part
import com.geoquiz.app.domain.map.GeoFormatException
import com.geoquiz.app.domain.map.GeoFormatException.Reason
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeometryKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The Kotlin reader against the committed `assets/geo` files (counts from
 * `data/geo/build_log.txt`) and a hand-built fixture for edge and corrupt cases.
 * Gradle runs unit tests with the module directory (app/) as the working directory.
 */
class GeoLayerReaderTest {

    private val geoDir = File("src/main/assets/geo")

    private fun readAsset(id: GeoLayerId): GeoLayer = GeoLayerReader.read(File(geoDir, id.fileName).readBytes())

    /** The app's 197 countries, from the bundled flag SVG names (`FlagAssetsPresentTest` checks those). */
    private val appCountries: Set<String> by lazy {
        File("src/main/assets/flags").listFiles()!!
            .filter { it.name.endsWith(".svg") }
            .map { it.name.removeSuffix(".svg").uppercase() }
            .toSet()
    }

    // ---- Committed assets ----

    @Test
    fun `every committed layer decodes with the build log feature counts`() {
        val expected = mapOf(
            GeoLayerId.ADMIN0_110M to 204,
            GeoLayerId.ADMIN0_50M to 240,
            GeoLayerId.TAP_ZONES to 46,
            GeoLayerId.ADMIN1_50M to 64,
            GeoLayerId.RIVERS_50M to 463,
            GeoLayerId.LAKES_50M to 406,
            GeoLayerId.REGIONS_50M to 517,
            GeoLayerId.GEO_POINTS_50M to 248,
        )
        assertEquals(GeoLayerId.entries.toSet(), expected.keys)
        for ((id, count) in expected) {
            val layer = readAsset(id)
            assertEquals(id, layer.id)
            assertEquals(id.kind, layer.kind)
            assertEquals("$id features", count, layer.features.size)
            assertTrue("$id source", layer.source.startsWith("Natural Earth"))
        }
    }

    @Test
    fun `ring and point totals match the build log`() {
        val expected = mapOf(
            // The build log counts source rings; one PRK ring (110m) and 14 region rings collapsed and were dropped.
            GeoLayerId.ADMIN0_110M to (361 to 10605),
            GeoLayerId.ADMIN0_50M to (1630 to 70471),
            GeoLayerId.ADMIN1_50M to (334 to 15830),
            GeoLayerId.LAKES_50M to (465 to 11830),
            GeoLayerId.REGIONS_50M to (1859 to 49898),
        )
        for ((id, ringsAndPoints) in expected) {
            val layer = readAsset(id)
            assertEquals("$id rings", ringsAndPoints.first, layer.features.sumOf { it.parts.size })
            assertEquals("$id points", ringsAndPoints.second, layer.features.sumOf { it.pointCount })
        }
        assertEquals(22558, readAsset(GeoLayerId.RIVERS_50M).features.sumOf { it.pointCount })
    }

    @Test
    fun `both admin0 levels have all 197 countries and only they are playable`() {
        assertEquals(197, appCountries.size)
        for (id in listOf(GeoLayerId.ADMIN0_110M, GeoLayerId.ADMIN0_50M)) {
            val layer = readAsset(id)
            val playable = layer.features.filter { it.isPlayable }.map { it.id }.toSet()
            assertEquals("$id playable set", appCountries, playable)
            assertTrue("$id has non-playable land", layer.features.any { !it.isPlayable })
            assertEquals("ATA", layer.feature("ATA")?.id)
            assertFalse(layer.feature("ATA")!!.isPlayable)
            assertEquals("Kosovo is keyed UNK", true, layer.feature("UNK")?.isPlayable)
        }
    }

    @Test
    fun `admin1 has 51 US and 13 Canadian features`() {
        val layer = readAsset(GeoLayerId.ADMIN1_50M)
        val byCountry = layer.features.groupingBy { it.properties.string("country") }.eachCount()
        assertEquals(mapOf("USA" to 51, "CAN" to 13), byCountry)
        assertEquals("Québec", layer.feature("CA-QC")?.properties?.string("name"))
    }

    @Test
    fun `wrap and remote flags are decoded`() {
        val layer = readAsset(GeoLayerId.ADMIN0_110M)
        fun wraps(id: String) = layer.feature(id)!!.parts.count { it.isWrap }
        assertEquals(1, wraps("FJI"))
        assertEquals(2, wraps("RUS"))
        assertEquals(9, wraps("KIR"))
        assertTrue(layer.features.any { f -> f.parts.any { it.isRemote } })
    }

    @Test
    fun `decoded coordinates stay inside the layer bounds and rings follow the rules`() {
        for (id in GeoLayerId.entries) {
            val layer = readAsset(id)
            val b = layer.bounds
            val eps = 1e-3f
            for (f in layer.features) {
                if (layer.kind == GeometryKind.POINT) assertTrue(f.parts.isEmpty())
                if (layer.kind == GeometryKind.POLYGON) assertFalse("${f.id} starts with a hole", f.parts.first().isHole)
                for (part in f.parts) {
                    for (i in 0 until part.size) {
                        assertTrue(part.lon[i] >= b.minLon - eps && part.lon[i] <= b.maxLon + eps)
                        assertTrue(part.lat[i] >= b.minLat - eps && part.lat[i] <= b.maxLat + eps)
                    }
                }
            }
        }
    }

    @Test
    fun `properties have the documented types`() {
        val points = readAsset(GeoLayerId.GEO_POINTS_50M)
        assertTrue(points.features.any { it.properties.int("elevation_m") == Int.MIN_VALUE || it.properties.int("elevation_m") < 0 })
        val taps = readAsset(GeoLayerId.TAP_ZONES)
        val vatican = taps.feature("VAT")
        assertNotNull(vatican)
        assertTrue(vatican!!.properties.int("area_km2") < 20_000)
    }

    // ---- Hand-built fixture ----

    @Test
    fun `fixture decodes holes, negative deltas, multi-byte varints and flags`() {
        val layer = GeoLayerReader.read(GeoFixtureWriter.sample().bytes())
        assertEquals(GeoLayerId.ADMIN0_50M, layer.id)
        assertEquals("Test fixture", layer.source)
        assertEquals(listOf("AAA", "BBB", "CCC"), layer.featureIds)

        val a = layer.feature("AAA")!!
        assertEquals(mapOf("name" to "Squareland", "playable" to 1, "area_km2" to 12345), a.properties.asMap())
        assertEquals(2, a.parts.size)
        assertFalse(a.parts[0].isHole)
        assertTrue(a.parts[1].isHole)
        assertLonLat(a.parts[1].lon[0], a.parts[1].lat[0], 250, 250)

        val b = layer.feature("BBB")!!
        assertEquals("Édge Ïsles", b.properties.string("name"))
        assertFalse(b.isPlayable)
        assertEquals(-7, b.properties.int("area_km2"))
        // Grid extremes map exactly onto the layer bounds.
        assertArrayEquals(floatArrayOf(180f, -180f, -180f), b.parts[0].lon, 1e-4f)
        assertArrayEquals(floatArrayOf(90f, 90f, -90f), b.parts[0].lat, 1e-4f)
        assertEquals(-180.0, b.bbox.minLon, 1e-9)
        assertEquals(90.0, b.bbox.maxLat, 1e-9)
        assertEquals(-180.0, b.labelLon, 1e-9)
        // The cursor carries on across parts.
        assertLonLat(b.parts[1].lon[1], b.parts[1].lat[1], -32701, -32702)
        assertLonLat(b.parts[1].lon[2], b.parts[1].lat[2], -32600, -32650)

        val c = layer.feature("CCC")!!
        assertEquals(Int.MIN_VALUE, c.properties.int("area_km2"))
        assertEquals("", c.properties.string("name"))
        assertTrue(c.parts[1].isRemote)
        assertFalse(c.parts[1].isWrap)
        assertTrue(c.parts[2].isWrap)
        assertFalse(c.parts[2].isRemote)
    }

    private fun assertLonLat(lon: Float, lat: Float, qx: Int, qy: Int) {
        assertEquals(-180.0 + (qx + 32768) * 360.0 / 65535, lon.toDouble(), 1e-4)
        assertEquals(-90.0 + (qy + 32768) * 180.0 / 65535, lat.toDouble(), 1e-4)
    }

    // ---- Corrupt files ----

    private fun assertReason(reason: Reason, bytes: ByteArray) {
        try {
            GeoLayerReader.read(bytes)
            fail("Expected $reason")
        } catch (e: GeoFormatException) {
            assertEquals(e.message, reason, e.reason)
        }
    }

    @Test
    fun `wrong magic`() = assertReason(Reason.BAD_MAGIC, GeoFixtureWriter.sample().apply { magic = 0x4D475148 }.bytes())

    @Test
    fun `empty file`() = assertReason(Reason.BAD_MAGIC, ByteArray(0))

    @Test
    fun `unsupported version`() = assertReason(Reason.UNSUPPORTED_VERSION, GeoFixtureWriter.sample().apply { version = 2 }.bytes())

    @Test
    fun `unknown layer id`() = assertReason(Reason.UNKNOWN_LAYER, GeoFixtureWriter.sample().apply { layerId = 9 }.bytes())

    @Test
    fun `unknown geometry kind`() = assertReason(Reason.UNKNOWN_GEOMETRY_KIND, GeoFixtureWriter.sample().apply { kind = 4 }.bytes())

    @Test
    fun `kind that doesn't match the layer`() = assertReason(Reason.MALFORMED, GeoFixtureWriter.sample().apply { kind = 2 }.bytes())

    @Test
    fun `unknown property type`() {
        val bytes = GeoFixtureWriter.sample().apply {
            properties = listOf("name" to 1, "playable" to 3, "area_km2" to 4)
        }.bytes()
        assertReason(Reason.UNKNOWN_PROPERTY_TYPE, bytes)
    }

    @Test
    fun `trailing bytes`() = assertReason(Reason.TRAILING_BYTES, GeoFixtureWriter.sample().bytes() + byteArrayOf(0))

    @Test
    fun `every truncation is reported as a format error`() {
        val bytes = GeoFixtureWriter.sample().bytes()
        for (n in 0 until bytes.size) {
            try {
                GeoLayerReader.read(bytes.copyOf(n))
                fail("Prefix of $n bytes decoded")
            } catch (e: GeoFormatException) {
                // Expected: TRUNCATED, or BAD_MAGIC for the first 4 bytes.
                if (n >= 4) assertEquals("prefix $n: ${e.message}", Reason.TRUNCATED, e.reason)
            }
        }
    }

    @Test
    fun `feature count larger than the file`() =
        assertReason(Reason.TRUNCATED, GeoFixtureWriter.sample().apply { declaredFeatureCount = 1_000_000 }.bytes())

    @Test
    fun `negative feature count`() =
        assertReason(Reason.MALFORMED, GeoFixtureWriter.sample().apply { declaredFeatureCount = -1 }.bytes())

    @Test
    fun `varint longer than five bytes`() {
        // A point layer's last byte is the last feature's part count (0); replace it with six bytes.
        val good = GeoFixtureWriter(layerId = 8, kind = 1, properties = listOf("name" to 1)).apply {
            features += Feature("1", listOf("Peak"), intArrayOf(0, 0, 0, 0), 0 to 0, emptyList())
        }.bytes()
        assertEquals(0, good.last().toInt())
        GeoLayerReader.read(good)
        val continuation = 0x80.toByte()
        val bad = good.copyOf(good.size - 1) +
            byteArrayOf(continuation, continuation, continuation, continuation, continuation, 0x01)
        assertReason(Reason.MALFORMED, bad)
    }

    @Test
    fun `fifth varint byte with bits above 32 is malformed`() {
        val good = GeoFixtureWriter(layerId = 8, kind = 1, properties = listOf("name" to 1)).apply {
            features += Feature("1", listOf("Peak"), intArrayOf(0, 0, 0, 0), 0 to 0, emptyList())
        }.bytes()
        val ff = 0xFF.toByte()
        fun messageFor(fifth: Byte): String = try {
            GeoLayerReader.read(good.copyOf(good.size - 1) + byteArrayOf(ff, ff, ff, ff, fifth))
            "decoded"
        } catch (e: GeoFormatException) {
            "${e.reason}: ${e.message}"
        }
        // 0x1F and 0x10 in the 5th byte set bits above 31.
        assertEquals("MALFORMED: Varint exceeds 32 bits", messageFor(0x1F))
        assertEquals("MALFORMED: Varint exceeds 32 bits", messageFor(0x10))
        // 0x0F fits in 32 bits (-1), so the varint decodes and the part-count rule rejects it.
        assertEquals("MALFORMED: Point feature 1 has -1 parts", messageFor(0x0F))
    }

    @Test
    fun `duplicate feature id`() {
        val writer = GeoFixtureWriter.sample()
        writer.features += writer.features[0]
        assertReason(Reason.MALFORMED, writer.bytes())
    }

    @Test
    fun `polygon ring with two points`() {
        val writer = GeoFixtureWriter().apply {
            features += Feature("X", listOf("x", 1, 0), intArrayOf(0, 0, 10, 10), 5 to 5, listOf(Part(0, listOf(0 to 0, 10 to 10))))
        }
        assertReason(Reason.MALFORMED, writer.bytes())
    }

    @Test
    fun `polygon that starts with a hole`() {
        val writer = GeoFixtureWriter().apply {
            features += Feature("X", listOf("x", 1, 0), intArrayOf(0, 0, 10, 10), 5 to 5, listOf(Part(1, listOf(0 to 0, 10 to 0, 10 to 10))))
        }
        assertReason(Reason.MALFORMED, writer.bytes())
    }

    @Test
    fun `point layer with parts`() {
        val writer = GeoFixtureWriter(layerId = 3, kind = 1, properties = listOf("name" to 1)).apply {
            features += Feature("X", listOf("x"), intArrayOf(0, 0, 0, 0), 0 to 0, listOf(Part(0, listOf(0 to 0, 1 to 1))))
        }
        assertReason(Reason.MALFORMED, writer.bytes())
    }

    @Test
    fun `point layer without parts decodes`() {
        val writer = GeoFixtureWriter(layerId = 8, kind = 1, properties = listOf("name" to 1, "elevation_m" to 2)).apply {
            features += Feature("1", listOf("Peak", 8848), intArrayOf(10, 20, 10, 20), 10 to 20, emptyList())
        }
        val layer = GeoLayerReader.read(writer.bytes())
        assertEquals(GeoLayerId.GEO_POINTS_50M, layer.id)
        assertEquals(8848, layer.features.single().properties.int("elevation_m"))
        assertTrue(layer.features.single().parts.isEmpty())
    }

    @Test
    fun `line with one point`() {
        val writer = GeoFixtureWriter(layerId = 5, kind = 2, properties = emptyList()).apply {
            features += Feature("R", emptyList(), intArrayOf(0, 0, 0, 0), 0 to 0, listOf(Part(0, listOf(0 to 0))))
        }
        assertReason(Reason.MALFORMED, writer.bytes())
    }

    @Test
    fun `point outside the quantisation grid`() {
        val writer = GeoFixtureWriter().apply {
            features += Feature("X", listOf("x", 1, 0), intArrayOf(32000, 0, 32767, 10), 5 to 5,
                listOf(Part(0, listOf(32000 to 0, 33000 to 0, 32000 to 10))))
        }
        assertReason(Reason.MALFORMED, writer.bytes())
    }

    @Test
    fun `invalid utf-8 in a string`() {
        val bytes = GeoFixtureWriter.sample().apply { source = "ab" }.bytes()
        val at = indexOf(bytes, "ab".toByteArray())
        bytes[at] = 0xC3.toByte() // a lead byte followed by an ASCII byte
        assertReason(Reason.MALFORMED, bytes)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int =
        (0..haystack.size - needle.size).first { i -> needle.indices.all { haystack[i + it] == needle[it] } }
}
