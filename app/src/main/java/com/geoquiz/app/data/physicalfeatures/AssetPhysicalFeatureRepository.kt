package com.geoquiz.app.data.physicalfeatures

import android.content.res.AssetManager
import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeature
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reads the raw asset bytes; faked in tests. Mirrors `data/borderhop/AssetBorderGraphRepository.kt`. */
fun interface PhysicalFeatureAssetSource {
    fun read(path: String): ByteArray
}

/** [PhysicalFeatureAssetSource] over the APK's assets. */
class AndroidPhysicalFeatureAssetSource(private val assets: AssetManager) : PhysicalFeatureAssetSource {
    override fun read(path: String): ByteArray = assets.open(path).use { it.readBytes() }
}

/** Only the fields this reads; the asset also carries `wikidata`, which is kept for a possible
 * future Credits/fact-check link but not used by the game logic yet. */
@Serializable
private data class PhysicalFeatureDto(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val type: String,
    val layer: String,
    val neIds: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val continent: String,
    val bbox: List<Double>,
    val tier: Int,
    val wikidata: String = ""
)

@Serializable
private data class PhysicalFeaturesAsset(val features: List<PhysicalFeatureDto>)

/** Loads the bundled `assets/physical_features/physical_features.json` (task 7.2, D47: public
 * domain/CC0, a byte-identical copy of `data/physical_features.json`). */
interface PhysicalFeatureRepository {
    suspend fun features(): List<PhysicalFeature>
}

/**
 * [PhysicalFeatureRepository] over the bundled asset (task 7.2): decoded once on [ioDispatcher]
 * and kept in memory for the life of the process, the same pattern as
 * [com.geoquiz.app.data.borderhop.AssetBorderGraphRepository] and `data/geo/AssetMapRepository.kt`.
 * Concurrent callers before the first decode share one load.
 *
 * A record whose `type` or `layer` string is unrecognised, or whose `bbox` is not exactly 4
 * numbers, is dropped rather than failing the whole file: this asset is reviewed and
 * licence-audited by hand (task 7.2 brief), not an arbitrary download, so a byte-strict decode
 * like `GeoLayerReader`'s would be overkill; [AssetPhysicalFeaturesDataTest] (test-only) checks
 * every one of the 101 bundled records actually decodes, so a typo here never silently drops one
 * from the game.
 */
class AssetPhysicalFeatureRepository(
    private val source: PhysicalFeatureAssetSource,
    private val ioDispatcher: CoroutineDispatcher
) : PhysicalFeatureRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: List<PhysicalFeature>? = null

    override suspend fun features(): List<PhysicalFeature> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(ioDispatcher) { load() }.also { cached = it }
        }
    }

    private fun load(): List<PhysicalFeature> {
        val bytes = source.read(ASSET_PATH)
        val asset = json.decodeFromString<PhysicalFeaturesAsset>(bytes.decodeToString())
        return asset.features.mapNotNull { it.toDomainOrNull() }
    }

    private fun PhysicalFeatureDto.toDomainOrNull(): PhysicalFeature? {
        val domainType = PhysicalFeatureType.fromJsonValue(type) ?: return null
        val layerId = layerIdOf(layer) ?: return null
        if (bbox.size != 4) return null
        return PhysicalFeature(
            id = id,
            name = name,
            aliases = aliases,
            type = domainType,
            layerId = layerId,
            neIds = neIds,
            countries = countries,
            continent = continent,
            bbox = GeoBounds(minLon = bbox[0], minLat = bbox[1], maxLon = bbox[2], maxLat = bbox[3]),
            tier = tier,
            wikidata = wikidata
        )
    }

    companion object {
        const val ASSET_PATH = "physical_features/physical_features.json"

        /** `data/physical_features.json`'s `layer` field names, mapped to the real layer id
         * (`data/geo/FORMAT.md`). */
        fun layerIdOf(layer: String): GeoLayerId? = when (layer) {
            "rivers_50m" -> GeoLayerId.RIVERS_50M
            "lakes_50m" -> GeoLayerId.LAKES_50M
            "regions_50m" -> GeoLayerId.REGIONS_50M
            "geo_points_50m" -> GeoLayerId.GEO_POINTS_50M
            else -> null
        }
    }
}
