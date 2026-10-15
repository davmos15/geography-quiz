package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.domain.model.Difficulty
import kotlin.random.Random

/** One drawn question: [setId] is the lookalike set and [targetCode] the member asked about. */
data class FlagSpeedDraw(val setId: String, val targetCode: String)

/**
 * Picks which lookalike set and target country the next flag speed question asks about (task
 * 7.3, D52): there is no fixed quiz set in this mode, so a question is drawn fresh, live, for as
 * long as the 60-second round runs.
 *
 * [poolFor] is the tier's draw pool: Easy = tier 3 only, Normal = tiers 2-3, Hard = tiers 1-3
 * (D52), always excluding [FlagLookalikeVisibility.NEAR_IDENTICAL] sets at every tier regardless
 * of their tier number - the data file's own note is that these are not a fair question at this
 * app's flag size.
 *
 * [next] avoids repeating the exact same (set, target) pair as [previous] back to back: "a set
 * should not repeat with the same target twice in a row". A different target from the same set
 * immediately after, or the identical pair again later in the round, is fine and expected - 60
 * seconds asks for more questions than the 24 sets in the data file can give without any repeat.
 */
object FlagSpeedRoundDrawer {

    /** Lookalike-set tiers in the draw pool for [difficulty] (D52). */
    fun tiersFor(difficulty: Difficulty): Set<Int> = when (difficulty) {
        Difficulty.EASY -> setOf(3)
        Difficulty.NORMAL -> setOf(2, 3)
        Difficulty.HARD -> setOf(1, 2, 3)
    }

    /** [sets] filtered to [difficulty]'s tiers, with near-identical sets always excluded. */
    fun poolFor(sets: List<FlagLookalikeSet>, difficulty: Difficulty): List<FlagLookalikeSet> {
        val tiers = tiersFor(difficulty)
        return sets.filter { it.visibility != FlagLookalikeVisibility.NEAR_IDENTICAL && it.tier in tiers }
    }

    /**
     * The next draw from [pool], or null only if [pool] is empty (no usable sets for this tier -
     * should not happen with the real data file, but the caller shows an "unavailable" state
     * rather than crash if it ever does).
     */
    fun next(pool: List<FlagLookalikeSet>, previous: FlagSpeedDraw?, random: Random): FlagSpeedDraw? {
        if (pool.isEmpty()) return null
        var draw = drawOnce(pool, random)
        var attempts = 1
        while (draw == previous && attempts < MAX_ATTEMPTS) {
            draw = drawOnce(pool, random)
            attempts++
        }
        return draw
    }

    private fun drawOnce(pool: List<FlagLookalikeSet>, random: Random): FlagSpeedDraw {
        val set = pool[random.nextInt(pool.size)]
        val target = set.countries[random.nextInt(set.countries.size)]
        return FlagSpeedDraw(set.id, target)
    }

    /**
     * Re-rolls to dodge an exact repeat of [previous]. Every tier's real pool has more than one
     * possible (set, target) pair (the smallest pool, Easy, has three sets totalling ten
     * countries), so this cap is a safety net against an unusual test fixture with only one
     * pair, not a real limit in play.
     */
    private const val MAX_ATTEMPTS = 20
}
