package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.model.QuizCategory

/**
 * Maps a round's [UsCaRegionGroup]/DC choice to a [QuizCategory] (task 6.2 review). This mode has
 * no classic category filter (D43 review: "just US or Canada"), so a normal round uses
 * [QuizCategory.FixedRegion]: its `label` field already doubles as both the route value
 * ([QuizCategory.valueKey]) and the display name ([QuizCategory.displayName]) (there are only
 * three possible values, so the label itself is a perfectly good, reversible route value - see
 * [groupAndDcFromLabel]). Code review (task 6.2): an earlier version of this reused
 * [QuizCategory.ByRegion], but that type is pattern-matched elsewhere (other modes' `offers()`,
 * [com.geoquiz.app.data.local.preferences.AchievementRepository]'s region tracking,
 * [com.geoquiz.app.ui.results.AnswerReviewScreen]'s "In {region}" text) in ways a fake region
 * label like "US states" would silently and incorrectly match; [QuizCategory.FixedRegion] exists
 * so this can never happen (its `isOfferedIn` is always false, same reasoning as
 * [QuizCategory.MapRegion]). [QuizCategory.typeKey] for this is always `"fixed_region"`; a
 * Practice round (built from a finished round's misses, independent of this encoding) still uses
 * [QuizCategory.Practice] exactly as every other mode does.
 */
object UsCaRegionsCategory {
    const val LABEL_US_NO_DC = "US states"
    const val LABEL_US_WITH_DC = "US states and DC"
    const val LABEL_CA = "Canadian provinces and territories"

    fun toQuizCategory(group: UsCaRegionGroup, includeDc: Boolean): QuizCategory = QuizCategory.FixedRegion(
        when (group) {
            UsCaRegionGroup.US_STATES -> if (includeDc) LABEL_US_WITH_DC else LABEL_US_NO_DC
            UsCaRegionGroup.CANADIAN_PROVINCES -> LABEL_CA
        }
    )

    /**
     * Reverses [toQuizCategory]'s label. An unknown or missing label falls back to US states
     * without DC, the same safe-default pattern every other category's route parsing uses
     * (`QuizCategory.fromRoute`'s catch-all).
     */
    fun groupAndDcFromLabel(label: String?): Pair<UsCaRegionGroup, Boolean> = when (label) {
        LABEL_US_WITH_DC -> UsCaRegionGroup.US_STATES to true
        LABEL_CA -> UsCaRegionGroup.CANADIAN_PROVINCES to false
        else -> UsCaRegionGroup.US_STATES to false
    }
}
