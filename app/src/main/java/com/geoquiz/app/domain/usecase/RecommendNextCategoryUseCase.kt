package com.geoquiz.app.domain.usecase

import javax.inject.Inject

/**
 * A category that can be recommended: [key] is `"categoryType|categoryValue"` (as in
 * `QuizHistoryRepository.categoryKey`) and [groupId] the category group it is listed under.
 */
data class RecommendationCandidate(
    val groupId: String,
    val key: String
)

/**
 * Picks the "Recommended next" category on the Play tab for one classic mode (decision D25).
 *
 * Inputs:
 * - `candidates`: the mode's concrete category options in display order (group order as on Play,
 *   then option order within the group as on the category list).
 * - `stars`: mastery stars per candidate key ([MasteryStars]); a missing key means 0 stars.
 * - `recentKeys`: the mode's recorded categories, most recently finished first.
 * - `excludedKey`: the category in the "Continue" card (only when it is in this mode), or null.
 *
 * Rule, in order:
 * - (a) Find the most recent recorded quiz whose category is still a candidate. If there is one,
 *   recommend the first candidate after it in the same group with fewer than 2 stars.
 * - (b) Otherwise, or if none is left in that group, recommend the first candidate with 0 stars
 *   (never played, or played without reaching 1 star).
 * - (c) If every candidate has at least 1 star, recommend the one with the fewest stars; ties go
 *   to the first in display order.
 * - (d) If all have 3 stars, recommend nothing.
 *
 * The excluded category is never recommended at any step (it can still be the anchor in (a)).
 * Pure: no I/O, so the rule is unit-tested exhaustively.
 */
class RecommendNextCategoryUseCase @Inject constructor() {

    operator fun invoke(
        candidates: List<RecommendationCandidate>,
        stars: Map<String, Int>,
        recentKeys: List<String>,
        excludedKey: String? = null
    ): RecommendationCandidate? {
        fun starsOf(candidate: RecommendationCandidate) = stars[candidate.key] ?: 0
        val eligible = candidates.filter { it.key != excludedKey }

        // (a) Continue in the group of the most recently played candidate.
        val candidateIndex = candidates.withIndex().associate { (index, candidate) -> candidate.key to index }
        val anchorIndex = recentKeys.firstNotNullOfOrNull { candidateIndex[it] }
        if (anchorIndex != null) {
            val group = candidates[anchorIndex].groupId
            candidates.drop(anchorIndex + 1)
                .takeWhile { it.groupId == group }
                .firstOrNull { it.key != excludedKey && starsOf(it) < 2 }
                ?.let { return it }
        }

        // (b) The first category with no stars yet.
        eligible.firstOrNull { starsOf(it) == 0 }?.let { return it }

        // (c) The fewest stars, first in display order; (d) nothing once all have 3 stars.
        return eligible.minByOrNull { starsOf(it) }?.takeIf { starsOf(it) < MasteryStars.MAX }
    }
}
