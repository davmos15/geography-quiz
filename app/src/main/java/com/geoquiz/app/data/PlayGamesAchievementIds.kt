package com.geoquiz.app.data

import com.geoquiz.app.domain.model.Achievement

/**
 * Maps local Achievement enum entries to Google Play Games achievement IDs.
 */
object PlayGamesAchievementIds {
    private val mapping = mapOf(
        // Original
        Achievement.FIRST_STEPS to "CgkIm_C-q6cSEAIQBQ",
        Achievement.WORLD_TRAVELER to "CgkIm_C-q6cSEAIQIQ",
        Achievement.PERFECTIONIST to "CgkIm_C-q6cSEAIQGQ",
        Achievement.SPEED_DEMON to "CgkIm_C-q6cSEAIQAw",
        Achievement.REGION_MASTER to "CgkIm_C-q6cSEAIQDg",
        Achievement.ALPHABET_SOUP to "CgkIm_C-q6cSEAIQGg",
        Achievement.CENTURY_CLUB to "CgkIm_C-q6cSEAIQDA",
        Achievement.HALF_WAY_THERE to "CgkIm_C-q6cSEAIQHQ",
        Achievement.GEOGRAPHY_BUFF to "CgkIm_C-q6cSEAIQIw",
        Achievement.EXPLORER to "CgkIm_C-q6cSEAIQJA",
        // New Bronze
        Achievement.QUICK_STUDY to "CgkIm_C-q6cSEAIQGA",
        Achievement.ISLAND_HOPPER to "CgkIm_C-q6cSEAIQCg",
        Achievement.PATTERN_FINDER to "CgkIm_C-q6cSEAIQBA",
        // New Silver
        Achievement.WORLD_SCHOLAR to "CgkIm_C-q6cSEAIQEQ",
        Achievement.LENGTH_MASTER to "CgkIm_C-q6cSEAIQFA",
        Achievement.VOWEL_HUNTER to "CgkIm_C-q6cSEAIQIg",
        Achievement.CONTINENTAL to "CgkIm_C-q6cSEAIQIA",
        Achievement.LETTER_COLLECTOR to "CgkIm_C-q6cSEAIQEg",
        // New Gold
        Achievement.ULTIMATE_GEOGRAPHER to "CgkIm_C-q6cSEAIQEA",
        Achievement.SPEED_MASTER to "CgkIm_C-q6cSEAIQCw",
        Achievement.PATTERN_MASTER to "CgkIm_C-q6cSEAIQHg",
        Achievement.SUBREGION_EXPLORER to "CgkIm_C-q6cSEAIQEw",
        // Capitals
        Achievement.CAPITAL_BEGINNER to "CgkIm_C-q6cSEAIQGw",
        Achievement.CAPITAL_EXPERT to "CgkIm_C-q6cSEAIQFg",
        Achievement.WORLD_CAPITALS to "CgkIm_C-q6cSEAIQDQ",
        Achievement.CAPITAL_SPEED_RUN to "CgkIm_C-q6cSEAIQFQ",
        Achievement.CAPITAL_SCHOLAR to "CgkIm_C-q6cSEAIQCA",
        Achievement.CAPITAL_MASTER to "CgkIm_C-q6cSEAIQJQ",
        // Flags
        Achievement.FLAG_SPOTTER to "CgkIm_C-q6cSEAIQBg",
        Achievement.COLOR_EXPERT to "CgkIm_C-q6cSEAIQHA",
        Achievement.RAINBOW to "CgkIm_C-q6cSEAIQDw",
        Achievement.FLAG_PERFECTIONIST to "CgkIm_C-q6cSEAIQAA",
        Achievement.VEXILLOLOGIST to "CgkIm_C-q6cSEAIQAQ",
        Achievement.FLAG_MASTER to "CgkIm_C-q6cSEAIQCQ",
        // Incorrect guesses & hard mode
        Achievement.FLAWLESS to "CgkIm_C-q6cSEAIQBw",
        Achievement.SHARP_MIND to "CgkIm_C-q6cSEAIQHw",
        Achievement.SURVIVOR to "CgkIm_C-q6cSEAIQFw",
        Achievement.NERVES_OF_STEEL to "CgkIm_C-q6cSEAIQAg",
    )

    fun getPlayGamesId(achievement: Achievement): String? = mapping[achievement]
}
