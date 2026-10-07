package com.geoquiz.app.domain.model

/**
 * How hard a quiz is. Each game mode lists the tiers it supports
 * ([com.geoquiz.app.domain.mode.GameModeSpec.supportedDifficulties]).
 *
 * - [EASY]: multiple choice (task 3.2).
 * - [NORMAL]: type the answers; typos are forgiven.
 * - [HARD]: type the answers, 3 strikes, always-on count-up timer (D15).
 *
 * [id] will be persisted (settings, history), so never change it.
 */
enum class Difficulty(val id: String) {
    EASY("easy"),
    NORMAL("normal"),
    HARD("hard");

    companion object {
        /** The tier with [id], or null if there is none. */
        fun fromIdOrNull(id: String?): Difficulty? = entries.find { it.id == id }
    }
}
