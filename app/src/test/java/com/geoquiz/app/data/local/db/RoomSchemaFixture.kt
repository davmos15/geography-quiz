package com.geoquiz.app.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Creates a database file exactly as an older app version left it, from the schema
 * Room exported to `app/schemas/<database class>/<version>.json`.
 *
 * Stands in for Room's MigrationTestHelper, which only reads schemas from APK assets;
 * AGP does not merge test assets into Robolectric (JVM) unit tests, and adding the
 * schemas to main or debug assets would ship them. Migration correctness is still
 * checked by Room itself: opening the file with the production builder runs the
 * migrations and then validates the result against the current schema.
 */
object RoomSchemaFixture {

    /** Gradle runs unit tests with the module directory (app/) as the working directory. */
    private val schemasDir = File("schemas")

    fun createDatabase(context: Context, name: String, databaseClass: String, version: Int): SQLiteDatabase {
        val schemaFile = File(schemasDir, "$databaseClass/$version.json")
        require(schemaFile.isFile) { "Missing exported schema $schemaFile" }
        val database = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        check(database.getValue("version").jsonPrimitive.int == version)

        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        for (entity in database.getValue("entities").jsonArray.map { it.jsonObject }) {
            val table = entity.getValue("tableName").jsonPrimitive.content
            db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray?.forEach { index ->
                db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        database["setupQueries"]?.jsonArray?.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.version = version
        return db
    }
}
