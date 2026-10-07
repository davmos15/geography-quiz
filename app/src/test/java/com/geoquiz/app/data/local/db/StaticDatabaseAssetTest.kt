package com.geoquiz.app.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Checks the raw prebuilt asset (`assets/databases/static.db`) before Room touches it,
 * and the path where a newer asset replaces an older installed copy.
 */
@RunWith(RobolectricTestRunner::class)
class StaticDatabaseAssetTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `raw asset matches StaticDatabase VERSION and the exported schema`() {
        // Room rewrites user_version when it opens a file, so read a raw copy of the asset.
        val copy = File(context.cacheDir, "static-asset-copy.db")
        context.assets.open(StaticDatabase.ASSET_PATH).use { input ->
            copy.outputStream().use { input.copyTo(it) }
        }
        val schemaFile = File("schemas/${StaticDatabase::class.java.name}/${StaticDatabase.VERSION}.json")
        val expectedHash = Json.parseToJsonElement(schemaFile.readText()).jsonObject
            .getValue("database").jsonObject
            .getValue("identityHash").jsonPrimitive.content

        val db = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("PRAGMA user_version", null).use {
                it.moveToFirst()
                assertEquals(StaticDatabase.VERSION, it.getInt(0))
            }
            db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use {
                assertEquals("room_master_table should hold one identity row", 1, it.count)
                it.moveToFirst()
                assertEquals(expectedHash, it.getString(0))
            }
        } finally {
            db.close()
            copy.delete()
        }
    }

    @Test
    fun `older installed copy is replaced by the asset`() = runBlocking {
        val installed = context.getDatabasePath(StaticDatabase.NAME)
        installed.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(installed, null).apply {
            execSQL("CREATE TABLE stale_marker (id INTEGER PRIMARY KEY)")
            version = StaticDatabase.VERSION - 1
            close()
        }

        val database = DatabaseModule.provideStaticDatabase(context)
        try {
            assertEquals(197, database.countryDao().getCountryCount())
            val db = database.openHelper.readableDatabase
            assertEquals(StaticDatabase.VERSION, db.version)
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'stale_marker'").use {
                assertFalse("old copy should have been replaced", it.moveToFirst())
            }
        } finally {
            database.close()
        }
    }
}
