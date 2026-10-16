package com.geoquiz.app.domain.mode.flagspeed

/**
 * How visible the difference between a lookalike set's flags actually is in the app, at the
 * app's fixed 4:3 flag size (`data/flag_lookalikes.json`'s own `notes` field): [CLEAR] an emblem,
 * star or layout differs; [SUBTLE] only a shade or band width differs; [NEAR_IDENTICAL]
 * practically the same picture in the app, even though the real flags' proportions differ.
 * [NEAR_IDENTICAL] sets are never drawn as a question at any tier (D52) - the data file's own
 * note is why: it would not be a fair question at this app's flag size.
 */
enum class FlagLookalikeVisibility(val key: String) {
    CLEAR("clear"),
    SUBTLE("subtle"),
    NEAR_IDENTICAL("near_identical");

    companion object {
        /**
         * [key] mapped back to its enum value; an unrecognised key reads as [NEAR_IDENTICAL]
         * (task 7.3 fix round, item 6) - never drawn as a question at any tier - rather than
         * [CLEAR] (never excluded), so a malformed or future `in_app` value fails safe instead of
         * silently becoming askable.
         */
        fun fromKey(key: String): FlagLookalikeVisibility = entries.find { it.key == key } ?: NEAR_IDENTICAL
    }
}

/**
 * One group of flags that look alike (`data/flag_lookalikes.json`, task 7.3). [countries] are
 * cca3 codes, 2 to 4 per set. [tier] (1 = `clear`/easiest to tell apart, 3 = `subtle`/hardest -
 * matching the data file's own `in_app` convention and `docs/DATA_PREP_NOTES.md` §7.3; corrected
 * 2026-10-16 code review - this KDoc previously had it backwards) decides which difficulty draws
 * from it ([FlagSpeedRoundDrawer]). [explanation] (at most 320 characters) is shown on the
 * post-round review for a set the player actually encountered; [cues] maps a member's cca3 to a
 * short per-country description, kept for a possible future hint but not shown by this mode yet.
 */
data class FlagLookalikeSet(
    val id: String,
    val countries: List<String>,
    val tier: Int,
    val visibility: FlagLookalikeVisibility,
    val explanation: String,
    val cues: Map<String, String>
)
