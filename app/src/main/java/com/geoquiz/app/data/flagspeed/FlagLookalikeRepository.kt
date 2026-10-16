package com.geoquiz.app.data.flagspeed

import android.content.res.AssetManager
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeSet
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeVisibility
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reads the raw asset bytes; faked in tests. Mirrors `data/borderhop/AssetBorderGraphRepository.kt`. */
fun interface FlagLookalikeAssetSource {
    fun read(path: String): ByteArray
}

/** [FlagLookalikeAssetSource] over the APK's assets. */
class AndroidFlagLookalikeAssetSource(private val assets: AssetManager) : FlagLookalikeAssetSource {
    override fun read(path: String): ByteArray = assets.open(path).use { it.readBytes() }
}

/** Only the fields this reads; the asset also carries a top-level `version` and `notes`. */
@Serializable
private data class FlagLookalikeSetAsset(
    val id: String,
    val countries: List<String>,
    val tier: Int,
    @SerialName("in_app") val inApp: String,
    val explanation: String,
    val cues: Map<String, String> = emptyMap()
)

@Serializable
private data class FlagLookalikesAsset(val sets: List<FlagLookalikeSetAsset>)

/** Loads the bundled `assets/flag_speed/flag_lookalikes.json` as a list of [FlagLookalikeSet]. */
interface FlagLookalikeRepository {
    suspend fun sets(): List<FlagLookalikeSet>
}

/**
 * [FlagLookalikeRepository] over the bundled asset (task 7.3): decoded once on [ioDispatcher] and
 * kept in memory for the life of the process, exactly like
 * [com.geoquiz.app.data.borderhop.AssetBorderGraphRepository] does for Border hop's graph.
 * Concurrent callers before the first decode share one load.
 */
class AssetFlagLookalikeRepository(
    private val source: FlagLookalikeAssetSource,
    private val ioDispatcher: CoroutineDispatcher
) : FlagLookalikeRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: List<FlagLookalikeSet>? = null

    override suspend fun sets(): List<FlagLookalikeSet> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(ioDispatcher) { load() }.also { cached = it }
        }
    }

    private fun load(): List<FlagLookalikeSet> {
        val bytes = source.read(ASSET_PATH)
        val asset = json.decodeFromString<FlagLookalikesAsset>(bytes.decodeToString())
        return asset.sets.map { raw ->
            FlagLookalikeSet(
                id = raw.id,
                countries = raw.countries,
                tier = raw.tier,
                visibility = FlagLookalikeVisibility.fromKey(raw.inApp),
                explanation = raw.explanation,
                cues = raw.cues
            )
        }
    }

    companion object {
        const val ASSET_PATH = "flag_speed/flag_lookalikes.json"
    }
}
