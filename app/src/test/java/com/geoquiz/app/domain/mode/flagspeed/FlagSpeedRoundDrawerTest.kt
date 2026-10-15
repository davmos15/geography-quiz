package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.domain.model.Difficulty
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The set-draw logic for a flag speed round (task 7.3, D52). */
class FlagSpeedRoundDrawerTest {

    private fun set(id: String, countries: List<String>, tier: Int, visibility: FlagLookalikeVisibility = FlagLookalikeVisibility.CLEAR) =
        FlagLookalikeSet(id = id, countries = countries, tier = tier, visibility = visibility, explanation = "explanation for $id", cues = emptyMap())

    private val tier1 = set("t1", listOf("AAA", "BBB"), tier = 1)
    private val tier2 = set("t2", listOf("CCC", "DDD"), tier = 2)
    private val tier3 = set("t3", listOf("EEE", "FFF"), tier = 3)
    private val nearIdenticalTier3 = set("near", listOf("GGG", "HHH"), tier = 3, visibility = FlagLookalikeVisibility.NEAR_IDENTICAL)
    private val allSets = listOf(tier1, tier2, tier3, nearIdenticalTier3)

    // Pool tiers

    @Test
    fun `Easy pool is tier 3 only, excluding near-identical sets`() {
        val pool = FlagSpeedRoundDrawer.poolFor(allSets, Difficulty.EASY)
        assertEquals(listOf(tier3), pool)
    }

    @Test
    fun `Normal pool is tiers 2 and 3, excluding near-identical sets`() {
        val pool = FlagSpeedRoundDrawer.poolFor(allSets, Difficulty.NORMAL)
        assertEquals(setOf(tier2, tier3), pool.toSet())
        assertEquals(2, pool.size)
    }

    @Test
    fun `Hard pool is tiers 1, 2 and 3, excluding near-identical sets`() {
        val pool = FlagSpeedRoundDrawer.poolFor(allSets, Difficulty.HARD)
        assertEquals(setOf(tier1, tier2, tier3), pool.toSet())
        assertEquals(3, pool.size)
    }

    @Test
    fun `a near-identical set is never in the draw pool at any tier`() {
        for (difficulty in Difficulty.entries) {
            val pool = FlagSpeedRoundDrawer.poolFor(allSets, difficulty)
            assertTrue(pool.none { it.visibility == FlagLookalikeVisibility.NEAR_IDENTICAL })
        }
    }

    @Test
    fun `a near-identical set is excluded even if it were the only set at its tier`() {
        val onlyNearIdentical = listOf(nearIdenticalTier3)
        val pool = FlagSpeedRoundDrawer.poolFor(onlyNearIdentical, Difficulty.EASY)
        assertTrue(pool.isEmpty())
    }

    // next()

    @Test
    fun `next returns null for an empty pool`() {
        assertNull(FlagSpeedRoundDrawer.next(emptyList(), null, Random(0)))
    }

    @Test
    fun `next always draws a target from a set actually in the pool`() {
        val pool = listOf(tier1, tier2, tier3)
        for (seed in 0 until 50) {
            val draw = FlagSpeedRoundDrawer.next(pool, null, Random(seed))
            assertNotNull(draw)
            val drawnSet = pool.first { it.id == draw!!.setId }
            assertTrue(draw!!.targetCode in drawnSet.countries)
        }
    }

    @Test
    fun `next never repeats the exact same set and target as the previous draw, when an alternative exists`() {
        val pool = listOf(tier1, tier2, tier3)
        val previous = FlagSpeedDraw(tier1.id, "AAA")
        for (seed in 0 until 200) {
            val draw = FlagSpeedRoundDrawer.next(pool, previous, Random(seed))
            assertNotNull(draw)
            assertTrue("draw $draw repeated the previous pair", draw != previous)
        }
    }

    @Test
    fun `next can draw a different target from the same set as the previous draw`() {
        // A single two-country set: the only way to avoid repeating the exact pair is picking the
        // other country in the same set, never a different set (there is none).
        val pool = listOf(tier1)
        val previous = FlagSpeedDraw(tier1.id, "AAA")
        var sawOtherTarget = false
        for (seed in 0 until 50) {
            val draw = FlagSpeedRoundDrawer.next(pool, previous, Random(seed))
            assertNotNull(draw)
            assertEquals(tier1.id, draw!!.setId)
            if (draw.targetCode == "BBB") sawOtherTarget = true
        }
        assertTrue("expected to draw the other target at least once over 50 seeds", sawOtherTarget)
    }

    @Test
    fun `the same pair can repeat later, just not immediately`() {
        val pool = listOf(tier1, tier2, tier3)
        var previous: FlagSpeedDraw? = null
        val random = Random(42)
        var sawRepeatLater = false
        val history = mutableListOf<FlagSpeedDraw>()
        repeat(100) {
            val draw = FlagSpeedRoundDrawer.next(pool, previous, random) ?: return@repeat
            if (draw in history) sawRepeatLater = true
            history += draw
            previous = draw
        }
        // Over 100 draws across only 6 possible pairs, some pair must repeat somewhere.
        assertTrue(sawRepeatLater)
        // And no two consecutive draws are identical.
        for (i in 1 until history.size) {
            assertTrue(history[i] != history[i - 1])
        }
    }
}
