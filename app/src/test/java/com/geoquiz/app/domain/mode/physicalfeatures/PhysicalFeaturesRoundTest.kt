package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoLayerId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalFeaturesRoundTest {

    private fun feature(id: String) = PhysicalFeature(
        id = id,
        name = id,
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

    @Test
    fun `fewer candidates than ROUND_SIZE returns every one of them`() {
        val candidates = (1..5).map { feature("r$it") }
        val round = PhysicalFeaturesRound.items(candidates, Random(1))
        assertEquals(5, round.size)
        assertEquals(candidates.toSet(), round.toSet())
    }

    @Test
    fun `more candidates than ROUND_SIZE is capped at ROUND_SIZE, all distinct`() {
        val candidates = (1..41).map { feature("r$it") }
        val round = PhysicalFeaturesRound.items(candidates, Random(1))
        assertEquals(PhysicalFeaturesRound.ROUND_SIZE, round.size)
        assertEquals(round.size, round.distinctBy { it.id }.size)
        assertTrue(round.all { it in candidates })
    }

    @Test
    fun `no candidates returns an empty round`() {
        assertEquals(emptyList<PhysicalFeature>(), PhysicalFeaturesRound.items(emptyList(), Random(1)))
    }

    @Test
    fun `different seeds can produce a different order`() {
        val candidates = (1..41).map { feature("r$it") }
        val a = PhysicalFeaturesRound.items(candidates, Random(1))
        val b = PhysicalFeaturesRound.items(candidates, Random(2))
        assertTrue(a != b)
    }
}
