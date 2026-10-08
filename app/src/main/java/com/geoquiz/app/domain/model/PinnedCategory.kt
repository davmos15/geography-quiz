package com.geoquiz.app.domain.model

/**
 * A category the player pinned from its row on the category list (3.4c, D23), per mode:
 * the same category pinned in Countries and Capitals is two pins. One tap on the Play tab
 * starts it at the remembered default difficulty.
 */
data class PinnedCategory(
    val modeId: String,
    val categoryType: String,
    val categoryValue: String
) {
    /** `"modeId|categoryType|categoryValue"`, as stored. */
    val key: String get() = "$modeId$SEPARATOR$categoryType$SEPARATOR$categoryValue"

    companion object {
        private const val SEPARATOR = '|'

        /**
         * The pin for [modeId], [categoryType] and [categoryValue], or null when it can't be a
         * pin: an unknown (non-classic) mode, a category that no longer parses, a practice set,
         * or a category the mode doesn't offer.
         */
        fun validOrNull(modeId: String, categoryType: String, categoryValue: String): PinnedCategory? {
            val mode = QuizMode.entries.find { it.id == modeId } ?: return null
            val category = QuizCategory.fromRouteOrNull(categoryType, categoryValue) ?: return null
            if (category is QuizCategory.Practice || !category.isOfferedIn(mode)) return null
            return PinnedCategory(modeId, categoryType, categoryValue)
        }

        /** Parses a stored [key]; null when it is malformed or no longer a valid pin ([validOrNull]). */
        fun fromKeyOrNull(key: String): PinnedCategory? {
            val parts = key.split(SEPARATOR, limit = 3)
            if (parts.size != 3) return null
            return validOrNull(parts[0], parts[1], parts[2])
        }
    }
}
