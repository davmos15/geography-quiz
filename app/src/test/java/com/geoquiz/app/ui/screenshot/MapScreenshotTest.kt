package com.geoquiz.app.ui.screenshot

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.geo.AndroidGeoAssetSource
import com.geoquiz.app.data.geo.AssetMapRepository
import com.geoquiz.app.domain.map.EqualEarthProjection
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.OutlineGeometry
import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.map.frame
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.ui.map.CountryOutline
import com.geoquiz.app.ui.map.CountryOutlinePathCache
import com.geoquiz.app.ui.map.MapCanvas
import com.geoquiz.app.ui.map.MapLayerPaths
import com.geoquiz.app.ui.map.MapListAlternative
import com.geoquiz.app.ui.map.MapScene
import com.geoquiz.app.ui.map.MapViewState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshot tests for the map engine (Phase 4): the world and region canvases
 * ([MapCanvas]/[MapScene], tasks 4.3–4.4), country silhouettes ([CountryOutline], task 4.5)
 * and the TalkBack list alternative ([MapListAlternative], task 4.6), light, dark and (list
 * only, the one screen with running text) 200% font scale.
 *
 * Determinism: every [MapScene] is built synchronously from the real bundled layers (the same
 * technique as `MapCanvasUiTest.worldScene()`/`renderFrame()`: [MapLayerPaths.build] is pure
 * CPU work, not the process-wide async path cache [com.geoquiz.app.ui.map.MapPathCache] that
 * production screens use), so there is no coroutine to await and no risk of a blank first
 * frame. Region membership and names come from the same `data/source/countries.json` reader as
 * `RegionPresetTest`. [MapViewState] starts unset and is fitted by [MapCanvas] itself on the
 * first layout pass (its `SideEffect` calls `updateContent`); since this is the view's first
 * content there is no camera reset to animate, so no frame clock advance is needed either.
 * No gesture gets replayed, so nothing pans or zooms away from the fitted frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class MapScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mapRepository = AssetMapRepository(AndroidGeoAssetSource(context.assets), Dispatchers.IO)
    private fun layer(id: GeoLayerId): GeoLayer = runBlocking { mapRepository.layer(id) }

    private val coarse: GeoLayer by lazy { layer(GeoLayerId.ADMIN0_110M) }
    private val detail: GeoLayer by lazy { layer(GeoLayerId.ADMIN0_50M) }
    private val tapZonesLayer: GeoLayer by lazy { layer(GeoLayerId.TAP_ZONES) }

    /** The app's 197 countries, loaded the same way as `RegionPresetTest`. */
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

    private fun membersOf(preset: RegionPreset): Set<String> = countries.filter { preset.includes(it) }.map { it.code }.toSet()

    /** A full-bleed [MapCanvas] over [scene], tap zones on (the production default). */
    private fun mapContent(scene: MapScene, featureStates: Map<String, MapFeatureState>): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            MapCanvas(
                scene = scene,
                state = MapViewState(),
                modifier = Modifier.fillMaxSize(),
                featureStates = featureStates,
            )
        }
    }

    // ---- World (Equal Earth): a few states of each kind and tap zones visible ----

    private val worldStates = mapOf(
        "AUS" to MapFeatureState.Found,
        "BRA" to MapFeatureState.Found,
        "CAN" to MapFeatureState.Wrong,
        "IND" to MapFeatureState.Highlighted,
        "EGY" to MapFeatureState.Start,
        "RUS" to MapFeatureState.End,
    )

    private fun worldScene(): MapScene = MapScene(
        EqualEarthProjection,
        coarse = MapLayerPaths.build(coarse, EqualEarthProjection),
        detail = null,
        tapZones = MapLayerPaths.build(tapZonesLayer, EqualEarthProjection),
    )

    @Test
    fun map_world_light() = compose.captureScreen("map_world_light", mapContent(worldScene(), worldStates))

    @Test
    @Config(qualifiers = "+night")
    fun map_world_dark() = compose.captureScreen("map_world_dark", mapContent(worldScene(), worldStates))

    // ---- Regions: fitted presets (task 4.4), a couple of states each ----

    private fun regionScene(preset: RegionPreset): MapScene {
        val members = membersOf(preset)
        val frame = requireNotNull(preset.frame(detail, members)) { "no frame for $preset" }
        return MapScene(
            preset.projection,
            coarse = null,
            detail = MapLayerPaths.build(detail, preset.projection),
            tapZones = MapLayerPaths.build(tapZonesLayer, preset.projection),
            bounds = frame,
            members = members,
            panMarginFraction = preset.panMarginFraction,
        )
    }

    private val regionStates: Map<RegionPreset, Map<String, MapFeatureState>> = mapOf(
        RegionPreset.AFRICA to mapOf("ZAF" to MapFeatureState.Found, "EGY" to MapFeatureState.Wrong),
        RegionPreset.ASIA to mapOf("JPN" to MapFeatureState.Found, "IND" to MapFeatureState.Wrong),
        RegionPreset.EUROPE to mapOf("FRA" to MapFeatureState.Found, "DEU" to MapFeatureState.Wrong),
        RegionPreset.NORTH_AMERICA to mapOf("USA" to MapFeatureState.Found, "MEX" to MapFeatureState.Wrong),
        RegionPreset.OCEANIA to mapOf("AUS" to MapFeatureState.Found, "FJI" to MapFeatureState.Highlighted),
        RegionPreset.SOUTH_AMERICA to mapOf("BRA" to MapFeatureState.Found, "ARG" to MapFeatureState.Wrong),
    )

    private fun regionTest(name: String, preset: RegionPreset) =
        compose.captureScreen(name, mapContent(regionScene(preset), regionStates.getValue(preset)))

    @Test fun map_region_africa() = regionTest("map_region_africa", RegionPreset.AFRICA)

    @Test fun map_region_asia() = regionTest("map_region_asia", RegionPreset.ASIA)

    @Test fun map_region_europe() = regionTest("map_region_europe", RegionPreset.EUROPE)

    @Test fun map_region_north_america() = regionTest("map_region_north_america", RegionPreset.NORTH_AMERICA)

    @Test fun map_region_oceania() = regionTest("map_region_oceania", RegionPreset.OCEANIA)

    @Test fun map_region_south_america() = regionTest("map_region_south_america", RegionPreset.SOUTH_AMERICA)

    @Test
    @Config(qualifiers = "+night")
    fun map_region_europe_dark() = regionTest("map_region_europe_dark", RegionPreset.EUROPE)

    // ---- Outlines (task 4.5): a 2x3 grid of silhouettes, neighbours on ----

    private val outlineCodes = listOf("FRA", "CHL", "JPN", "RUS", "FJI", "ITA")

    private fun outlineGrid(): @Composable () -> Unit {
        val outlines: List<OutlineModel> = outlineCodes.map { code ->
            requireNotNull(OutlineGeometry.build(detail, code)) { "no outline for $code" }
        }
        // Build every outline's paths before composing: they are built on Dispatchers.Default,
        // which Compose's idle wait doesn't track, so a slow one (RUS) could otherwise miss the capture.
        runBlocking { outlines.forEach { CountryOutlinePathCache.Shared.paths(it, showNeighbours = true).await() } }
        return {
            Column(Modifier.fillMaxSize()) {
                outlines.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        row.forEach { outline ->
                            Box(Modifier.weight(1f).fillMaxSize().padding(8.dp)) {
                                CountryOutline(outline = outline, modifier = Modifier.fillMaxSize(), showNeighbours = true)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun map_outline_light() = compose.captureScreen("map_outline_light", outlineGrid())

    @Test
    @Config(qualifiers = "+night")
    fun map_outline_dark() = compose.captureScreen("map_outline_dark", outlineGrid())

    // ---- List alternative (task 4.6): Europe, some states and the search field ----

    private val listStates = mapOf(
        "FRA" to MapFeatureState.Found,
        "DEU" to MapFeatureState.Wrong,
        "ESP" to MapFeatureState.Highlighted,
    )

    private fun listContent(): @Composable () -> Unit {
        val europe = countries.filter { RegionPreset.EUROPE.includes(it) }
        return {
            MapListAlternative(
                countries = europe,
                featureStates = listStates,
                onSelect = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test fun map_list_light() = compose.captureScreen("map_list_light", listContent())

    @Test
    @Config(qualifiers = "+night")
    fun map_list_dark() = compose.captureScreen("map_list_dark", listContent())

    @Test
    @Config(fontScale = 2f)
    fun map_list_font200() = compose.captureScreen("map_list_font200", listContent())

    // ---- Tablet (3.7-style wide layout): Europe preset ----

    @Test
    @Config(qualifiers = TABLET)
    fun map_tablet() = compose.captureScreen(
        "map_tablet",
        mapContent(regionScene(RegionPreset.EUROPE), regionStates.getValue(RegionPreset.EUROPE))
    )

    companion object {
        const val TABLET = "w900dp-h600dp-xhdpi"
    }
}
