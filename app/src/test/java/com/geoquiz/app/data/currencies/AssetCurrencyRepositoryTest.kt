package com.geoquiz.app.data.currencies

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetCurrencyRepositoryTest {

    /** Serves the committed asset from disk and counts reads, like `AssetBorderGraphRepositoryTest`. */
    private class FakeAssetSource : CurrencyAssetSource {
        val reads = AtomicInteger()
        override fun read(path: String): ByteArray {
            reads.incrementAndGet()
            return File("src/main/assets/$path").readBytes()
        }
    }

    @Test
    fun `cachedOrNull is null before the first load`() {
        val repo = AssetCurrencyRepository(FakeAssetSource(), StandardTestDispatcher())
        assertNull(repo.cachedOrNull())
    }

    @Test
    fun `loads and parses the bundled currencies json, caching the result`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetCurrencyRepository(source, StandardTestDispatcher(testScheduler))

        val first = repo.currencies()
        val second = repo.currencies()

        assertSame(first, second)
        assertEquals(1, source.reads.get())
        assertEquals(146, first.entries.size)
        assertEquals(197, first.primaryCurrency.size)
        assertSame(first, repo.cachedOrNull())
    }

    @Test
    fun `concurrent requests share a single read`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetCurrencyRepository(source, StandardTestDispatcher(testScheduler))

        val results = List(5) { async { repo.currencies() } }.awaitAll()

        assertEquals(1, source.reads.get())
        assertTrue(results.all { it === results[0] })
    }

    @Test
    fun `D48's named edge cases are present in the real bundled data`() = runTest {
        val repo = AssetCurrencyRepository(FakeAssetSource(), StandardTestDispatcher(testScheduler))
        val data = repo.currencies()
        val byCode = data.entries.associateBy { it.code }

        // Panama's own currency, not USD.
        assertEquals(listOf("PAN"), byCode["PAB"]?.countries)
        assertFalse(byCode["PAB"]!!.shared)

        // Zimbabwe's primary currency in this data is USD, which is shared.
        assertEquals("USD", data.primaryCurrency["ZWE"])
        assertTrue(byCode["USD"]!!.shared)

        // Palestine has no official primary currency.
        assertNull(data.primaryCurrency["PSE"])

        // Sierra Leone: SLL, not SLE (mledoze has no SLE entry).
        assertEquals(listOf("SLE"), byCode["SLL"]?.countries)
        assertNotNull(byCode.keys.find { it == "SLL" })
        assertTrue("SLE" !in byCode.keys)
    }

    @Test
    fun `exactly 13 currency codes are shared`() = runTest {
        val repo = AssetCurrencyRepository(FakeAssetSource(), StandardTestDispatcher(testScheduler))
        val data = repo.currencies()

        assertEquals(13, data.entries.count { it.shared })
    }

    @Test
    fun `listedInBasketBy is parsed - Zimbabwe's unofficial basket currencies (review item 3)`() = runTest {
        val repo = AssetCurrencyRepository(FakeAssetSource(), StandardTestDispatcher(testScheduler))
        val data = repo.currencies()
        val byCode = data.entries.associateBy { it.code }

        assertEquals(listOf("ZWE"), byCode["ZAR"]?.listedInBasketBy)
        assertEquals(listOf("ZWE"), byCode["EUR"]?.listedInBasketBy)
        // A currency with no basket listing parses to an empty list, not null/crashing.
        assertEquals(emptyList<String>(), byCode["USD"]?.listedInBasketBy)
    }
}
