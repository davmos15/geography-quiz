package com.geoquiz.app.data.currencies

import android.content.res.AssetManager
import com.geoquiz.app.domain.mode.currencies.CurrencyData
import com.geoquiz.app.domain.mode.currencies.CurrencyEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads the raw asset bytes; faked in tests. Mirrors
 * `data/borderhop/AssetBorderGraphRepository.kt`'s `BorderGraphAssetSource`.
 */
fun interface CurrencyAssetSource {
    fun read(path: String): ByteArray
}

/** [CurrencyAssetSource] over the APK's assets. */
class AndroidCurrencyAssetSource(private val assets: AssetManager) : CurrencyAssetSource {
    override fun read(path: String): ByteArray = assets.open(path).use { it.readBytes() }
}

/** Only the fields this reads; the asset also carries attribution, licence and stats fields it ignores. */
@Serializable
private data class CurrencyAssetEntry(
    val code: String,
    val name: String,
    val symbol: String,
    val countries: List<String>,
    val shared: Boolean,
    val fact: String? = null,
    /** Countries that unofficially also use this currency (currently only Zimbabwe, D48). */
    val listedInBasketBy: List<String> = emptyList()
)

@Serializable
private data class CurrencyAsset(
    val currencies: List<CurrencyAssetEntry>,
    val primaryCurrency: Map<String, String?> = emptyMap()
)

/** Loads the bundled `assets/currencies/currencies.json` (task 7.1) as [CurrencyData]. */
interface CurrencyRepository {
    /** Loads (if needed) and returns every currency entry plus the primary-currency map, cached for the life of the process. */
    suspend fun currencies(): CurrencyData

    /**
     * The same data from [currencies]'s in-memory cache, synchronously, or null if [currencies]
     * has never been awaited. [com.geoquiz.app.domain.mode.currencies.CurrenciesChoiceGenerator]
     * (a plain [com.geoquiz.app.domain.mode.ChoiceQuestionGenerator], not suspend) relies on
     * this: by the time any choice question is built, a round has already loaded its item pool
     * via [com.geoquiz.app.domain.mode.currencies.CurrencyQuestionGenerator], which awaits
     * [currencies] first - the same warm-cache invariant
     * [com.geoquiz.app.data.borderhop.AssetBorderGraphRepository] and
     * [com.geoquiz.app.data.geo.AssetMapRepository] rely on elsewhere.
     */
    fun cachedOrNull(): CurrencyData?
}

/**
 * [CurrencyRepository] over the bundled asset (task 7.1): decoded once on [ioDispatcher] and kept
 * in memory for the life of the process, exactly like
 * [com.geoquiz.app.data.borderhop.AssetBorderGraphRepository] does for the border graph.
 * Concurrent callers before the first decode share one load.
 */
class AssetCurrencyRepository(
    private val source: CurrencyAssetSource,
    private val ioDispatcher: CoroutineDispatcher
) : CurrencyRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: CurrencyData? = null

    override suspend fun currencies(): CurrencyData {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(ioDispatcher) { load() }.also { cached = it }
        }
    }

    override fun cachedOrNull(): CurrencyData? = cached

    private fun load(): CurrencyData {
        val bytes = source.read(ASSET_PATH)
        val asset = json.decodeFromString<CurrencyAsset>(bytes.decodeToString())
        val entries = asset.currencies.map {
            CurrencyEntry(
                code = it.code,
                name = it.name,
                symbol = it.symbol,
                countries = it.countries,
                shared = it.shared,
                fact = it.fact,
                listedInBasketBy = it.listedInBasketBy
            )
        }
        return CurrencyData(entries, asset.primaryCurrency)
    }

    companion object {
        const val ASSET_PATH = "currencies/currencies.json"
    }
}
