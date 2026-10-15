package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT
import com.geoquiz.app.domain.mode.ChoicePrompt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalFeatureChoiceBuilderTest {

    private fun feature(id: String) = PhysicalFeature(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        aliases = emptyList(),
        type = PhysicalFeatureType.RIVER,
        layerId = GeoLayerId.RIVERS_50M,
        neIds = listOf("1"),
        countries = emptyList(),
        continent = "Asia",
        bbox = GeoBounds(0.0, 0.0, 1.0, 1.0),
        tier = 1,
        wikidata = ""
    )

    private val target = feature("nile")
    private val pool = listOf(target) + (1..10).map { feature("river$it") }

    @Test
    fun `the question has 4 options including the target, when the pool allows it`() {
        val question = PhysicalFeatureChoiceBuilder.question(target, pool, Random(1))
        assertEquals(CHOICE_OPTION_COUNT, question.options.size)
        assertEquals(target.id, question.targetCode)
        assertTrue(question.options.any { it.code == target.id && it.label == target.name })
        assertEquals(question.options.size, question.options.distinctBy { it.code }.size)
    }

    @Test
    fun `the prompt names the target feature`() {
        val question = PhysicalFeatureChoiceBuilder.question(target, pool, Random(1))
        assertEquals(ChoicePrompt.FeatureOf(target.name), question.prompt)
    }

    @Test
    fun `fewer than 4 candidates still includes the target with no duplicates`() {
        val smallPool = listOf(target, feature("amazon"))
        val question = PhysicalFeatureChoiceBuilder.question(target, smallPool, Random(1))
        assertEquals(2, question.options.size)
        assertEquals(target.id, question.correctOption.code)
    }

    @Test
    fun `the target never appears twice even if the pool contains it more than once`() {
        val duplicatePool = pool + target
        val question = PhysicalFeatureChoiceBuilder.question(target, duplicatePool, Random(1))
        assertEquals(1, question.options.count { it.code == target.id })
    }
}
