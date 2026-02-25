package com.geoquiz.app.data

/**
 * Maps leaderboard types to Google Play Games leaderboard IDs.
 * Create these leaderboards in Play Console and replace the placeholder IDs.
 */
object PlayGamesLeaderboardIds {
    const val OVERALL = "CgkIm_C-q6cSEAIQJw"
    const val COUNTRIES = "CgkIm_C-q6cSEAIQKA"
    const val CAPITALS = "CgkIm_C-q6cSEAIQKQ"
    const val FLAGS = "CgkIm_C-q6cSEAIQKg"

    fun forMode(quizMode: String): String? = when (quizMode) {
        "countries" -> COUNTRIES
        "capitals" -> CAPITALS
        "flags" -> FLAGS
        else -> null
    }
}
