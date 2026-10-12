package com.geoquiz.app.ui.map

import com.geoquiz.app.data.geo.GeoLayerReader
import com.geoquiz.app.domain.map.EqualEarthProjection
import com.geoquiz.app.domain.map.FeatureHitIndex
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.ProjectedLayer
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.map.TapZones
import com.geoquiz.app.domain.map.frame
import com.geoquiz.app.domain.map.planeBounds
import com.geoquiz.app.domain.map.resolveTap
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.GetRegionMembersUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Region presets (task 4.4) on the real data, pure JVM: membership from the app's country
 * data, framing without clipping on a phone and a tablet, culling, antimeridian contiguity
 * and hit testing in each preset.
 */
class RegionPresetTest {

    // ---- The app's 197 countries, as the static database has them ----

    private val countries: List<Country> by lazy {
        val source = File("../data/source")
        val extra = Json.parseToJsonElement(File(source, "alias_overrides.json").readText())
            .jsonObject.getValue("extraCountries").jsonArray.map { it.jsonPrimitive.content }.toSet()
        Json.parseToJsonElement(File(source, "countries.json").readText()).jsonArray.map { it.jsonObject }
            .filter { o -> o["unMember"]?.jsonPrimitive?.booleanOrNull == true || o.getValue("cca3").jsonPrimitive.content in extra }
            .map { o ->
                val name = o.getValue("name").jsonObject
                Country(
                    code = o.getValue("cca3").jsonPrimitive.content,
                    name = name.getValue("common").jsonPrimitive.content,
                    officialName = name.getValue("official").jsonPrimitive.content,
                    region = o.getValue("region").jsonPrimitive.content,
                    subregion = o["subregion"]?.jsonPrimitive?.content.orEmpty(),
                    nameLength = 0,
                    capital = "",
                )
            }
    }

    private val repository: CountryRepository by lazy { mockk { every { getAllCountries() } returns flowOf(countries) } }
    private val members: Map<RegionPreset, Set<String>> by lazy {
        val useCase = GetRegionMembersUseCase(repository)
        RegionPreset.entries.associateWith { runBlocking { useCase(it) } }
    }

    private fun read(id: GeoLayerId): GeoLayer = GeoLayerReader.read(File("src/main/assets/geo", id.fileName).readBytes())
    private val detail by lazy { read(GeoLayerId.ADMIN0_50M) }
    private val coarse by lazy { read(GeoLayerId.ADMIN0_110M) }
    private val tapLayer by lazy { read(GeoLayerId.TAP_ZONES) }

    @Test
    fun `members come from the country data`() {
        assertEquals(197, countries.size)
        val counts = members.mapValues { it.value.size }
        assertEquals(
            mapOf(
                RegionPreset.WORLD to 197, RegionPreset.AFRICA to 54, RegionPreset.ASIA to 48, RegionPreset.EUROPE to 46,
                RegionPreset.NORTH_AMERICA to 23, RegionPreset.OCEANIA to 14, RegionPreset.SOUTH_AMERICA to 12,
            ),
            counts,
        )
        // Continents partition the 197.
        assertEquals(197, counts.filterKeys { it != RegionPreset.WORLD }.values.sum())
        // Edge cases, as the data has them.
        assertTrue("RUS" in members.getValue(RegionPreset.EUROPE))
        assertTrue("TUR" in members.getValue(RegionPreset.ASIA))
        assertTrue("CYP" in members.getValue(RegionPreset.EUROPE))
        assertTrue("GEO" in members.getValue(RegionPreset.ASIA))
        assertTrue("EGY" in members.getValue(RegionPreset.AFRICA))
        assertTrue("PAN" in members.getValue(RegionPreset.NORTH_AMERICA))
        assertTrue("MEX" in members.getValue(RegionPreset.NORTH_AMERICA))
        assertTrue("TTO" in members.getValue(RegionPreset.NORTH_AMERICA))
        assertTrue("PNG" in members.getValue(RegionPreset.OCEANIA))
        assertTrue("TLS" in members.getValue(RegionPreset.ASIA))
        assertEquals(RegionPreset.OCEANIA, RegionPreset.fromId("oceania"))
    }

    /** A viewport showing a preset at zoom 1, like MapCanvas does. */
    private fun fitted(preset: RegionPreset, widthDp: Int, heightDp: Int, density: Float): Pair<MapViewState, PlaneRect> {
        val frame = if (preset == RegionPreset.WORLD) ProjectedLayer.build(detail, preset.projection).bounds
        else requireNotNull(preset.frame(detail, members.getValue(preset))) { "no frame for $preset" }
        val state = MapViewState().apply {
            updateViewport(widthDp * density, heightDp * density)
            updateContent(frame, preset.projection, preset.panMarginFraction)
        }
        assertEquals(1f, state.zoom)
        return state to state.visibleRect()!!
    }

    @Test
    fun `every member is framed without clipping on a phone and a tablet`() {
        val viewports = listOf(Triple(360, 640, 2.75f), Triple(900, 600, 2f))
        val problems = mutableListOf<String>()
        for (preset in RegionPreset.entries) for ((w, h, d) in viewports) {
            val (_, visible) = fitted(preset, w, h, d)
            val eps = visible.width * 1e-4f
            for (code in members.getValue(preset)) {
                val feature = detail.feature(code) ?: run { problems += "$preset $code missing"; continue }
                val bounds = if (preset == RegionPreset.EUROPE && code == "RUS") {
                    // The data puts all of Russia in Europe; the frame shows it to the Urals.
                    europeanRussiaBounds(preset)
                } else {
                    preset.projection.planeBounds(feature, includeRemote = false)
                }
                val inside = bounds.minX >= visible.minX - eps && bounds.maxX <= visible.maxX + eps &&
                    bounds.minY >= visible.minY - eps && bounds.maxY <= visible.maxY + eps
                if (!inside) problems += "$preset ${w}x$h $code $bounds not in $visible"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun europeanRussiaBounds(preset: RegionPreset): PlaneRect {
        val rus = detail.feature("RUS")!!
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (part in rus.parts) {
            if (part.isRemote) continue
            for (i in 0 until part.size) {
                if (part.lon[i] > 60f || part.lon[i] < -32f) continue
                val p = preset.projection.project(part.lon[i].toDouble(), part.lat[i].toDouble())
                minX = minOf(minX, p.x.toFloat()); maxX = maxOf(maxX, p.x.toFloat())
                minY = minOf(minY, -p.y.toFloat()); maxY = maxOf(maxY, -p.y.toFloat())
            }
        }
        return PlaneRect(minX, minY, maxX, maxY)
    }

    @Test
    fun `every drawn coordinate is finite in every preset`() {
        val layers = listOf(GeoLayerId.ADMIN0_110M, GeoLayerId.ADMIN0_50M, GeoLayerId.LAKES_50M, GeoLayerId.RIVERS_50M,
            GeoLayerId.ADMIN1_50M, GeoLayerId.TAP_ZONES).map { read(it) }
        for (preset in RegionPreset.entries) for (layer in layers) {
            val projected = ProjectedLayer.build(layer, preset.projection)
            for (f in projected.features) {
                if (f.isCulled) continue
                assertTrue("$preset ${layer.id} ${f.id} bounds", f.bounds.run { minX.isFinite() && maxX.isFinite() && minY.isFinite() && maxY.isFinite() })
                for (ring in f.rings) assertTrue("$preset ${layer.id} ${f.id}", ring.all { it.isFinite() })
            }
            assertTrue("$preset ${layer.id} layer bounds", projected.bounds.run { minX.isFinite() && maxY.isFinite() })
        }
    }

    @Test
    fun `antipodal land is culled`() {
        fun culled(preset: RegionPreset) = ProjectedLayer.build(detail, preset.projection).features.filter { it.isCulled }.map { it.id }.toSet()
        assertTrue(culled(RegionPreset.ASIA).containsAll(listOf("BRA", "ARG", "CHL", "URY")))
        assertTrue(culled(RegionPreset.EUROPE).containsAll(listOf("AUS", "NZL")))
        assertTrue(culled(RegionPreset.OCEANIA).containsAll(listOf("ESP", "PRT", "MAR")))
        assertTrue(culled(RegionPreset.WORLD).isEmpty())
        // Culled features are out of the layer bounds, the hit index and the zones.
        val asia = ProjectedLayer.build(detail, RegionPreset.ASIA.projection)
        val bra = asia.features.first { it.id == "BRA" }
        assertTrue(bra.rings.isEmpty() && bra.bounds.isEmpty)
        assertEquals(-1, TapZones.build(asia, null).indexOf("BRA"))
        // Large features that straddle the 100 degree limit keep their visible parts.
        val rus = asia.features.first { it.id == "RUS" }
        assertFalse(rus.isCulled)
    }

    @Test
    fun `features across the antimeridian stay in one piece`() {
        val oceania = ProjectedLayer.build(detail, RegionPreset.OCEANIA.projection)
        val world = ProjectedLayer.build(detail, EqualEarthProjection)
        fun width(layer: ProjectedLayer, id: String) = layer.features.first { it.id == id }.bounds.width
        // Fiji straddles 180°: split across the whole Equal Earth map, compact in Oceania.
        assertTrue(width(world, "FJI") > 4f)
        assertTrue("Fiji ${width(oceania, "FJI")}", width(oceania, "FJI") < 0.15f)
        // New Zealand with the Chatham Islands (176°W): about 25 degrees across, not split.
        assertTrue("NZL ${width(oceania, "NZL")}", width(oceania, "NZL") < 0.6f)
        assertTrue(longestSegment(oceania, "NZL") < 0.2f)
        assertTrue(width(world, "NZL") > 4f)
        // Russia, as context in Asia: Chukotka joins the mainland, no ring jumps across the map.
        val asia = ProjectedLayer.build(detail, RegionPreset.ASIA.projection)
        assertTrue(width(asia, "RUS") < 2.5f)
        assertTrue("longest RUS segment in Asia", longestSegment(asia, "RUS") < 0.2f)
        // The USA with the Aleutians across 180° in North America.
        val na = ProjectedLayer.build(detail, RegionPreset.NORTH_AMERICA.projection)
        // (Width, not segment length: the 49th-parallel border is a few very long straight edges.)
        assertTrue("USA ${width(na, "USA")}", width(na, "USA") < 2f)
        assertTrue(width(world, "USA") > 4f)
        assertTrue(longestSegment(world, "RUS") < 0.2f) // in Equal Earth it is split at ±180°, not streaked
    }

    private fun longestSegment(layer: ProjectedLayer, id: String): Float {
        var longest = 0f
        for (ring in layer.features.first { it.id == id }.rings) {
            val n = ring.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                val dx = ring[2 * j] - ring[2 * i]
                val dy = ring[2 * j + 1] - ring[2 * i + 1]
                longest = maxOf(longest, kotlin.math.sqrt(dx * dx + dy * dy))
            }
        }
        return longest
    }

    @Test
    fun `hit testing works in each preset`() {
        fun tap(preset: RegionPreset, id: String, layer: GeoLayer = detail): String? {
            val projected = ProjectedLayer.build(layer, preset.projection)
            val tapProjected = ProjectedLayer.build(tapLayer, preset.projection)
            val m = members.getValue(preset)
            val index = FeatureHitIndex(projected)
            val zones = TapZones.build(projected, tapProjected) { it in m }
            val (state, _) = fitted(preset, 360, 640, 2.75f)
            val metrics = ZoneMetrics(state.scale, 2.75f)
            val z = zones.indexOf(id)
            val f = projected.features.first { it.id == id }
            val (x, y) = if (z >= 0 && zones.visible(z, metrics.listedMax, metrics.otherMax)) zones.centreX[z] to zones.centreY[z] else f.labelX to f.labelY
            return resolveTap(index, zones, x, y, metrics.radius, metrics.listedMax, metrics.otherMax, metrics.tie)?.featureId
        }
        assertEquals("FJI", tap(RegionPreset.OCEANIA, "FJI"))
        assertEquals("TUV", tap(RegionPreset.OCEANIA, "TUV"))
        assertEquals("AUS", tap(RegionPreset.OCEANIA, "AUS"))
        assertEquals("NGA", tap(RegionPreset.AFRICA, "NGA"))
        assertEquals("LSO", tap(RegionPreset.AFRICA, "LSO"))
        assertEquals("VAT", tap(RegionPreset.EUROPE, "VAT"))
        assertEquals("DEU", tap(RegionPreset.EUROPE, "DEU"))
        assertEquals("JPN", tap(RegionPreset.ASIA, "JPN"))
        assertEquals("KNA", tap(RegionPreset.NORTH_AMERICA, "KNA"))
        assertEquals("ECU", tap(RegionPreset.SOUTH_AMERICA, "ECU"))
        assertEquals("FJI", tap(RegionPreset.OCEANIA, "FJI", coarse))
    }

    @Test
    fun `every member is tappable in its preset on a phone`() {
        val problems = mutableListOf<String>()
        for (preset in RegionPreset.entries) {
            val m = members.getValue(preset)
            val (state, _) = fitted(preset, 360, 640, 2.75f)
            val level = resolveMapLevel(MapLevelPreference.AUTO, state.scale / 2.75f, true, true)
            val layer = if (level == MapLevel.DETAIL) detail else coarse
            val projected = ProjectedLayer.build(layer, preset.projection)
            val index = FeatureHitIndex(projected)
            val zones = TapZones.build(projected, ProjectedLayer.build(tapLayer, preset.projection)) { it in m }
            val metrics = ZoneMetrics(state.scale, 2.75f)
            fun hit(x: Float, y: Float) = resolveTap(index, zones, x, y, metrics.radius, metrics.listedMax, metrics.otherMax, metrics.tie)?.featureId
            for (code in m) {
                val fi = projected.features.indexOfFirst { it.id == code }
                val f = projected.features[fi]
                val z = zones.indexOf(code)
                val ok = (z >= 0 && zones.visible(z, metrics.listedMax, metrics.otherMax) && hit(zones.centreX[z], zones.centreY[z]) == code) ||
                    hit(f.labelX, f.labelY) == code ||
                    (1 until 30).any { i -> (1 until 30).any { j ->
                        val x = f.bounds.minX + f.bounds.width * i / 30
                        val y = f.bounds.minY + f.bounds.height * j / 30
                        index.contains(fi, x, y) && hit(x, y) == code
                    } }
                if (!ok) problems += "$preset ($level) $code"
            }
        }
        assertTrue(problems.joinToString(), problems.isEmpty())
    }

    @Test
    fun `continent projections are azimuthal and the world is Equal Earth`() {
        assertEquals(EqualEarthProjection, RegionPreset.WORLD.projection)
        RegionPreset.entries.filter { it != RegionPreset.WORLD }.forEach {
            val p = it.projection as LambertAzimuthalEqualAreaProjection
            assertEquals(LambertAzimuthalEqualAreaProjection.DEFAULT_MAX_DISTANCE_DEG, p.maxDistanceDeg, 0.0)
        }
        // Each continent zooms in far enough for 50m geometry on a phone.
        for (preset in RegionPreset.entries.filter { it != RegionPreset.WORLD }) {
            val (state, _) = fitted(preset, 360, 640, 2.75f)
            println("$preset: %.0f dp per unit at zoom 1 on a phone".format(state.scale / 2.75f))
        }
    }
}
