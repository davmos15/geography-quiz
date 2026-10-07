package com.geoquiz.app.ui.components

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that every country the app shows has a bundled flag SVG.
 * Gradle runs unit tests with the module directory (app/) as the working directory.
 */
class FlagAssetsPresentTest {

    private val assetsDir = File("src/main/assets")
    private val sourceDir = File("../data/source")

    // Same filter as tools/data/build_static_db.py: UN members plus extraCountries.
    private val extraCountries: Set<String> by lazy {
        Json.parseToJsonElement(File(sourceDir, "alias_overrides.json").readText())
            .jsonObject.getValue("extraCountries").jsonArray
            .map { it.jsonPrimitive.content }.toSet()
    }

    private fun usedCca3Codes(): List<String> {
        val json = Json.parseToJsonElement(File(sourceDir, "countries.json").readText())
        return json.jsonArray.map { it.jsonObject }
            .filter { obj ->
                val unMember = obj["unMember"]?.jsonPrimitive?.booleanOrNull == true
                unMember || obj.getValue("cca3").jsonPrimitive.content in extraCountries
            }
            .map { it.getValue("cca3").jsonPrimitive.content }
    }

    @Test
    fun `app uses 197 countries`() {
        assertEquals(197, usedCca3Codes().size)
    }

    @Test
    fun `every used country has a flag svg asset`() {
        val missing = usedCca3Codes().filterNot { File(assetsDir, flagAssetPath(it)).isFile }
        assertTrue("Missing flag SVGs for: $missing", missing.isEmpty())
    }

    @Test
    fun `flag-icons licence is bundled with the flags`() {
        assertTrue(File(assetsDir, "flags/LICENSE").isFile)
    }
}
