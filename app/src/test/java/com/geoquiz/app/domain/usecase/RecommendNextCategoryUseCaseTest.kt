package com.geoquiz.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "Recommended next" rule (D25), branch by branch. */
class RecommendNextCategoryUseCaseTest {

    private val recommend = RecommendNextCategoryUseCase()

    // Two groups in display order: regions (3 options) then letters (3 options).
    private val africa = RecommendationCandidate("regions", "region|Africa")
    private val asia = RecommendationCandidate("regions", "region|Asia")
    private val europe = RecommendationCandidate("regions", "region|Europe")
    private val letterA = RecommendationCandidate("letters", "startletter|A")
    private val letterB = RecommendationCandidate("letters", "startletter|B")
    private val letterC = RecommendationCandidate("letters", "startletter|C")
    private val candidates = listOf(africa, asia, europe, letterA, letterB, letterC)

    private fun pick(
        stars: Map<RecommendationCandidate, Int> = emptyMap(),
        recent: List<String> = emptyList(),
        excluded: RecommendationCandidate? = null,
        list: List<RecommendationCandidate> = candidates
    ) = recommend(list, stars.mapKeys { it.key.key }, recent, excluded?.key)

    private fun allStars(value: Int) = candidates.associateWith { value }

    // (a) Next in the group of the most recently played candidate

    @Test
    fun `a - recommends the next category in the group of the last one played`() {
        assertEquals(asia, pick(stars = mapOf(africa to 1), recent = listOf(africa.key)))
    }

    @Test
    fun `a - skips categories in that group with 2 or more stars`() {
        val stars = mapOf(africa to 1, asia to 2, europe to 1)
        assertEquals(europe, pick(stars = stars, recent = listOf(africa.key)))
    }

    @Test
    fun `a - a category with 1 star after the last one played is still recommended`() {
        val stars = allStars(3) + mapOf(letterC to 1)
        assertEquals(letterC, pick(stars = stars, recent = listOf(letterA.key)))
    }

    @Test
    fun `a - uses the most recent history entry that is still a candidate`() {
        // "all" and a category that no longer exists are not candidates, so letter A is the anchor.
        val recent = listOf("all|_", "region|Atlantis", letterA.key, africa.key)
        assertEquals(letterB, pick(stars = mapOf(letterA to 1, africa to 1), recent = recent))
    }

    @Test
    fun `a - never looks before the last one played`() {
        // Africa (0 stars) is earlier in the group; (a) only looks after Asia.
        assertEquals(europe, pick(stars = mapOf(asia to 1), recent = listOf(asia.key)))
    }

    @Test
    fun `a - stops at the group boundary`() {
        // Europe is last in regions: letter A (next group, 0 stars) comes from (b), not (a),
        // so the first 0-star candidate overall wins.
        val stars = mapOf(africa to 1, asia to 1, europe to 1)
        assertEquals(letterA, pick(stars = stars, recent = listOf(europe.key)))
        // With Africa unplayed, (b) picks Africa rather than crossing into letters.
        assertEquals(africa, pick(stars = mapOf(asia to 1, europe to 1), recent = listOf(europe.key)))
    }

    @Test
    fun `a - falls back when the rest of the group is mastered`() {
        val stars = mapOf(africa to 1, asia to 2, europe to 3)
        assertEquals(letterA, pick(stars = stars, recent = listOf(africa.key)))
    }

    // (b) First never-played (0-star) candidate

    @Test
    fun `b - with no history recommends the first category`() {
        assertEquals(africa, pick())
    }

    @Test
    fun `b - recommends the first category with no stars in display order`() {
        val stars = mapOf(africa to 3, asia to 1, europe to 2, letterA to 1)
        assertEquals(letterB, pick(stars = stars))
    }

    @Test
    fun `b - a category played without earning a star counts as 0 stars`() {
        val stars = mapOf(africa to 0, asia to 1, europe to 1)
        assertEquals(africa, pick(stars = stars))
        // Also after (a) finds nothing: Europe is last in its group.
        assertEquals(africa, pick(stars = stars, recent = listOf(europe.key, africa.key)))
    }

    // (c) Fewest stars, ties in display order

    @Test
    fun `c - when every category is played recommends the fewest stars`() {
        val stars = mapOf(africa to 3, asia to 2, europe to 3, letterA to 1, letterB to 2, letterC to 3)
        assertEquals(letterA, pick(stars = stars))
    }

    @Test
    fun `c - ties go to the first in display order`() {
        val stars = mapOf(africa to 3, asia to 2, europe to 3, letterA to 3, letterB to 2, letterC to 3)
        assertEquals(asia, pick(stars = stars))
    }

    @Test
    fun `c - applies after (a) finds nothing left in the group`() {
        val stars = mapOf(africa to 2, asia to 3, europe to 2, letterA to 2, letterB to 3, letterC to 2)
        assertEquals(africa, pick(stars = stars, recent = listOf(letterA.key)))
    }

    // (d) Everything mastered

    @Test
    fun `d - no recommendation when every category has 3 stars`() {
        assertNull(pick(stars = allStars(3)))
        assertNull(pick(stars = allStars(3), recent = listOf(asia.key)))
    }

    @Test
    fun `no candidates means no recommendation`() {
        assertNull(pick(list = emptyList()))
    }

    // Never the category in the Continue card

    @Test
    fun `the Continue category is skipped in (a)`() {
        val stars = mapOf(africa to 1)
        assertEquals(europe, pick(stars = stars, recent = listOf(africa.key), excluded = asia))
    }

    @Test
    fun `the Continue category is skipped in (b)`() {
        assertEquals(asia, pick(excluded = africa))
    }

    @Test
    fun `the Continue category is skipped in (c)`() {
        val stars = allStars(3) + mapOf(letterA to 1, letterB to 2)
        assertEquals(letterB, pick(stars = stars, excluded = letterA))
    }

    @Test
    fun `the Continue category can still be the anchor of (a)`() {
        assertEquals(asia, pick(stars = mapOf(africa to 1), recent = listOf(africa.key), excluded = africa))
    }

    @Test
    fun `no recommendation when the only category left is the Continue one`() {
        val stars = allStars(3) + mapOf(europe to 2)
        assertNull(pick(stars = stars, excluded = europe))
        assertNull(pick(list = listOf(africa), excluded = africa))
    }
}
