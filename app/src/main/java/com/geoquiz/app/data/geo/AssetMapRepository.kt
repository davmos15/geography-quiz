package com.geoquiz.app.data.geo

import android.content.res.AssetManager
import com.geoquiz.app.domain.map.GeoFormatException
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap

/** Reads raw asset bytes; faked in tests. */
fun interface GeoAssetSource {
    fun read(path: String): ByteArray
}

/** [GeoAssetSource] over the APK's assets. */
class AndroidGeoAssetSource(private val assets: AssetManager) : GeoAssetSource {
    override fun read(path: String): ByteArray = assets.open(path).use { it.readBytes() }
}

/**
 * [MapRepository] over the bundled `assets/geo/` files. Each layer is read and decoded on
 * [ioDispatcher] the first time it is asked for, then kept in memory (the whole set decodes
 * to a few MB). Concurrent requests for the same layer share one load; different layers load
 * in parallel. Failures are not cached.
 */
class AssetMapRepository(
    private val source: GeoAssetSource,
    private val ioDispatcher: CoroutineDispatcher,
) : MapRepository {

    private val cache = ConcurrentHashMap<GeoLayerId, GeoLayer>()
    private val locks = EnumMap<GeoLayerId, Mutex>(GeoLayerId::class.java).apply {
        GeoLayerId.entries.forEach { put(it, Mutex()) }
    }

    override fun cachedLayer(id: GeoLayerId): GeoLayer? = cache[id]

    override suspend fun layer(id: GeoLayerId): GeoLayer {
        cache[id]?.let { return it }
        return locks.getValue(id).withLock {
            cache[id] ?: withContext(ioDispatcher) { load(id) }.also { cache[id] = it }
        }
    }

    private fun load(id: GeoLayerId): GeoLayer {
        val layer = GeoLayerReader.read(source.read(id.assetPath))
        if (layer.id != id) {
            throw GeoFormatException(
                GeoFormatException.Reason.WRONG_LAYER,
                "${id.assetPath} holds layer ${layer.id}, expected $id"
            )
        }
        return layer
    }
}
