package com.geoquiz.app.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.repository.CountryRepositoryImpl
import com.geoquiz.app.di.DatabaseModule
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Opens the real prebuilt asset (`assets/databases/static.db`) through the production
 * Room builder in [DatabaseModule], so a stale or malformed asset fails here.
 */
@RunWith(RobolectricTestRunner::class)
class StaticDatabaseTest {

    private lateinit var database: StaticDatabase
    private lateinit var repository: CountryRepositoryImpl
    private val normalize = NormalizeInputUseCase()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = DatabaseModule.provideStaticDatabase(context)
        repository = CountryRepositoryImpl(
            countryDao = database.countryDao(),
            capitalAliasDao = database.capitalAliasDao(),
            normalizeInput = normalize
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `asset holds the 197 countries`() = runBlocking {
        assertEquals(197, repository.getCountryCount())
        val countries = repository.getAllCountries().first()
        assertEquals(197, countries.size)
        assertEquals(197, countries.map { it.code }.toSet().size)
        // getAllCountries is ordered by name.
        assertEquals(countries.map { it.name }.sorted(), countries.map { it.name })
    }

    @Test
    fun `asset version matches StaticDatabase VERSION`() {
        database.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            assertEquals(StaticDatabase.VERSION, cursor.getInt(0))
        }
    }

    @Test
    fun `country answers resolve through aliases`() = runBlocking {
        assertEquals("GBR", repository.findCountryByAnswer("uk")?.code)
        assertEquals("GBR", repository.findCountryByAnswer("Great Britain")?.code)
        assertEquals("CIV", repository.findCountryByAnswer("ivory coast")?.code)
        assertEquals("CIV", repository.findCountryByAnswer("cote divoire")?.code)
        assertEquals("CIV", repository.findCountryByAnswer("Côte d'Ivoire")?.code)
        assertEquals("USA", repository.findCountryByAnswer("USA")?.code)
        assertEquals("UNK", repository.findCountryByAnswer("Kosovo")?.code)
        assertEquals("VAT", repository.findCountryByAnswer("holy see")?.code)
        // Short codes are dropped unless whitelisted for that country.
        assertNull(repository.findCountryByAnswer("FR"))
        assertNull(repository.findCountryByAnswer("Atlantis"))
        assertNull(repository.findCountryByAnswer("   "))
    }

    @Test
    fun `capital answers resolve through capital aliases`() = runBlocking {
        assertEquals("MYS", repository.findCountryByCapitalAnswer("kl")?.code)
        assertEquals("FRA", repository.findCountryByCapitalAnswer("Paris")?.code)
        assertEquals("ZAF", repository.findCountryByCapitalAnswer("Cape Town")?.code)
        assertEquals("COL", repository.findCountryByCapitalAnswer("bogota")?.code)
        assertNull(repository.findCountryByCapitalAnswer("Atlantis"))
    }

    @Test
    fun `country fields are populated`() = runBlocking {
        val countries = repository.getAllCountries().first().associateBy { it.code }
        val france = countries.getValue("FRA")
        assertEquals("France", france.name)
        assertEquals("French Republic", france.officialName)
        assertEquals("Europe", france.region)
        assertEquals("Western Europe", france.subregion)
        assertEquals("Paris", france.capital)
        assertEquals(6, france.nameLength)
        assertTrue(countries.values.all { it.nameLength == it.name.length })
    }

    @Test
    fun `every stored normalised alias matches the Kotlin normaliser`() {
        // Guards against drift between tools/data/export_aliases.py and NormalizeInputUseCase.
        for (table in listOf("aliases", "capital_aliases")) {
            val mismatches = mutableListOf<String>()
            var rows = 0
            database.openHelper.readableDatabase
                .query("SELECT alias, normalizedAlias FROM $table").use { cursor ->
                    while (cursor.moveToNext()) {
                        rows++
                        val alias = cursor.getString(0)
                        val stored = cursor.getString(1)
                        val expected = normalize(alias)
                        if (stored != expected) mismatches += "$alias: '$stored' != '$expected'"
                    }
                }
            assertTrue("$table is empty", rows > 0)
            assertTrue("$table mismatches: $mismatches", mismatches.isEmpty())
        }
    }

    @Test
    fun `flag colours and elements are present`() = runBlocking {
        val colours = database.flagColorDao().getAllMappings()
        assertEquals(197, colours.map { it.countryCca3 }.toSet().size)
        assertTrue("red" in database.flagColorDao().getAllColors())
        assertEquals(
            setOf("blue", "red", "white"),
            database.flagColorDao().getColorsForCountry("FRA").toSet()
        )
        val elements = database.flagElementDao().getAllMappings()
        assertTrue(elements.isNotEmpty())
        assertTrue(database.flagElementDao().getAllElements().isNotEmpty())
    }
}
