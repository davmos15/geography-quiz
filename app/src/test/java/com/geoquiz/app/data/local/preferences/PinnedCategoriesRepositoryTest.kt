package com.geoquiz.app.data.local.preferences

import app.cash.turbine.test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.geoquiz.app.domain.model.PinnedCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Pinned categories (3.4c, D23) in the settings DataStore. */
class PinnedCategoriesRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: PinnedCategoriesRepository

    private val storedKey = stringPreferencesKey("pinned_categories")

    private val africa = PinnedCategory("countries", "region", "Africa")
    private val capitalsA = PinnedCategory("capitals", "startletter", "A")
    private val red = PinnedCategory("flags", "flagcolor", "red")

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "settings.preferences_pb")
        }
        repository = PinnedCategoriesRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun pin(pin: PinnedCategory, pinned: Boolean = true) =
        repository.setPinned(pin.modeId, pin.categoryType, pin.categoryValue, pinned)

    private suspend fun pins() = repository.pinnedCategories.first()

    @Test
    fun `no pins on a fresh install`() = runTest {
        assertEquals(emptyList<PinnedCategory>(), pins())
    }

    @Test
    fun `pins are kept in the order pinned, newest last, across modes`() = runTest {
        pin(red)
        pin(africa)
        pin(capitalsA)

        assertEquals(listOf(red, africa, capitalsA), pins())
    }

    @Test
    fun `the same category in two modes is two pins`() = runTest {
        val africaCapitals = africa.copy(modeId = "capitals")
        pin(africa)
        pin(africaCapitals)

        assertEquals(listOf(africa, africaCapitals), pins())
    }

    @Test
    fun `pinning again is ignored and keeps the original position`() = runTest {
        pin(africa)
        pin(red)
        pin(africa)

        assertEquals(listOf(africa, red), pins())
    }

    @Test
    fun `unpinning removes only that pin and keeps the order`() = runTest {
        pin(africa)
        pin(capitalsA)
        pin(red)

        pin(capitalsA, pinned = false)
        assertEquals(listOf(africa, red), pins())

        // Unpinning something that isn't pinned changes nothing
        pin(capitalsA, pinned = false)
        assertEquals(listOf(africa, red), pins())

        pin(africa, pinned = false)
        pin(red, pinned = false)
        assertEquals(emptyList<PinnedCategory>(), pins())
        assertNull(dataStore.data.first()[storedKey])
    }

    @Test
    fun `invalid pins are never stored`() = runTest {
        repository.setPinned("countries", "practice", "FRA,DEU", pinned = true) // a practice set
        repository.setPinned("countries", "flagcolor", "red", pinned = true) // not offered in Countries
        repository.setPinned("countries", "capitalmatches", "_", pinned = true) // Capitals only
        repository.setPinned("silhouettes", "region", "Africa", pinned = true) // not a classic mode
        repository.setPinned("countries", "nonsense", "x", pinned = true) // unknown type
        repository.setPinned("countries", "startletter", "AB", pinned = true) // malformed value

        assertEquals(emptyList<PinnedCategory>(), pins())
        assertNull(dataStore.data.first()[storedKey])
    }

    @Test
    fun `stored entries that are invalid or duplicated are dropped`() = runTest {
        dataStore.edit {
            it[storedKey] = """[
                "countries|region|Africa",
                "countries|practice|FRA,DEU",
                "countries|flagcolor|red",
                "gone|region|Africa",
                "no separators",
                "countries|region",
                "countries|region|Africa",
                "flags|flagcolor|red"
            ]"""
        }

        assertEquals(listOf(africa, red), pins())

        // The next write cleans the stored value
        pin(capitalsA)
        assertEquals(
            """["countries|region|Africa","flags|flagcolor|red","capitals|startletter|A"]""",
            dataStore.data.first()[storedKey]
        )
    }

    @Test
    fun `a corrupt stored value reads as no pins and is replaced on the next pin`() = runTest {
        dataStore.edit { it[storedKey] = "not json" }

        assertEquals(emptyList<PinnedCategory>(), pins())

        pin(africa)
        assertEquals(listOf(africa), pins())
    }

    @Test
    fun `pins survive a restart`() = runTest {
        pin(africa)
        pin(red)

        // A new repository over the same store, as after the app restarts
        assertEquals(listOf(africa, red), PinnedCategoriesRepository(dataStore).pinnedCategories.first())
    }

    @Test
    fun `pins emit live and other settings are untouched`() = runTest {
        val settings = SettingsRepository(dataStore)
        settings.setPlayerName("Sam")

        repository.pinnedCategories.test {
            assertEquals(emptyList<PinnedCategory>(), awaitItem())
            pin(africa)
            assertEquals(listOf(africa), awaitItem())
            pin(africa, pinned = false)
            assertEquals(emptyList<PinnedCategory>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("Sam", settings.playerName.first())
    }

    @Test
    fun `keys round-trip through fromKeyOrNull`() {
        for (pin in listOf(africa, capitalsA, red, PinnedCategory("flags", "flagcombo", "red+white"))) {
            assertEquals(pin, PinnedCategory.fromKeyOrNull(pin.key))
        }
    }
}
