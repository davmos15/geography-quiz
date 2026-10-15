package com.geoquiz.app.data.borderhop

import android.content.res.AssetManager
import com.geoquiz.app.domain.mode.borderhop.BorderGraph
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reads the raw asset bytes; faked in tests. Mirrors `data/geo/AssetMapRepository.kt`'s `GeoAssetSource`. */
fun interface BorderGraphAssetSource {
    fun read(path: String): ByteArray
}

/** [BorderGraphAssetSource] over the APK's assets. */
class AndroidBorderGraphAssetSource(private val assets: AssetManager) : BorderGraphAssetSource {
    override fun read(path: String): ByteArray = assets.open(path).use { it.readBytes() }
}

/** Only the field this reads; the asset also carries attribution, licence and stats fields it ignores. */
@Serializable
private data class BorderGraphAsset(val countries: Map<String, List<String>>)

/** Loads the bundled `assets/border_hop/borders.json` as a [BorderGraph]. */
interface BorderGraphRepository {
    suspend fun graph(): BorderGraph
}

/**
 * [BorderGraphRepository] over the bundled asset (task 6.1): decoded once on [ioDispatcher] and
 * kept in memory for the life of the process, exactly like [com.geoquiz.app.data.geo.AssetMapRepository]
 * does for the map layers. Concurrent callers before the first decode share one load.
 */
class AssetBorderGraphRepository(
    private val source: BorderGraphAssetSource,
    private val ioDispatcher: CoroutineDispatcher
) : BorderGraphRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: BorderGraph? = null

    override suspend fun graph(): BorderGraph {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(ioDispatcher) { load() }.also { cached = it }
        }
    }

    private fun load(): BorderGraph {
        val bytes = source.read(ASSET_PATH)
        val asset = json.decodeFromString<BorderGraphAsset>(bytes.decodeToString())
        return BorderGraph(asset.countries)
    }

    companion object {
        const val ASSET_PATH = "border_hop/borders.json"
    }
}
