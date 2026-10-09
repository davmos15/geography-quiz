package com.geoquiz.app.domain.model

/**
 * How hard a quiz is. Each game mode lists the tiers it supports
 * ([com.geoquiz.app.domain.mode.GameModeSpec.supportedDifficulties]).
 *
 * - [EASY]: multiple choice, 4 options (task 3.2b). Recorded in history, stats and mastery
 *   stars, but unlocks no achievements and submits no leaderboard scores (D16).
 * - [NORMAL]: type the answers; typos are forgiven; no strike limit.
 * - [HARD]: type the answers exactly, 3 strikes, count-up timer always shown (D15). No time limit.
 *
 * [id] is persisted (settings, Room history and resume save, last result, routes), so never
 * change it.
 */
enum class Difficulty(
    val id: String,
    /** Answers one typo away from a name are accepted. */
    val allowsTypos: Boolean,
    /** Incorrect guesses that end the quiz, or null for no limit. */
    val strikeLimit: Int?,
    /** The timer is shown whatever the "Show timer" setting says. */
    val timerAlwaysShown: Boolean,
    /** Counts towards achievements and Play Games leaderboards (D16). */
    val countsForAchievements: Boolean
) {
    EASY("easy", allowsTypos = true, strikeLimit = null, timerAlwaysShown = false, countsForAchievements = false),
    NORMAL("normal", allowsTypos = true, strikeLimit = null, timerAlwaysShown = false, countsForAchievements = true),
    HARD("hard", allowsTypos = false, strikeLimit = 3, timerAlwaysShown = true, countsForAchievements = true);

    companion object {
        /** The tier used when nothing else says otherwise; also how pre-3.2 records read. */
        val DEFAULT: Difficulty = NORMAL

        /** The tier with [id], or null if there is none. */
        fun fromIdOrNull(id: String?): Difficulty? = entries.find { it.id == id }

        /** The tier with [id], or [DEFAULT] for null or an unknown id (old rows, bad routes). */
        fun fromIdOrDefault(id: String?): Difficulty = fromIdOrNull(id) ?: DEFAULT

        /**
         * The tier for a challenge quiz: challenges are never played at [EASY], so a player
         * whose default is Easy plays at [NORMAL]. [requested] (e.g. from the route) wins if
         * it is not Easy.
         */
        fun forChallenge(requested: Difficulty?, default: Difficulty): Difficulty = when {
            requested != null && requested != EASY -> requested
            default == HARD -> HARD
            else -> NORMAL
        }
    }
}
