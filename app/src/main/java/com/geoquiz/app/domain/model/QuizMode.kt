package com.geoquiz.app.domain.model

import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.classic.CapitalsGameMode
import com.geoquiz.app.domain.mode.classic.CountriesGameMode
import com.geoquiz.app.domain.mode.classic.FlagsGameMode

/**
 * The three original modes. [id] is persisted (Room, DataStore, challenge links, routes,
 * leaderboards), so never change it. Behaviour lives in the game mode registry
 * (`domain/mode/GameModeRegistry.kt`), keyed by the same id; new modes register there and do not
 * get an entry here.
 */
enum class QuizMode(val id: String, val displayLabel: String) {
    COUNTRIES("countries", "Countries"),
    CAPITALS("capitals", "Capitals"),
    FLAGS("flags", "Flags");

    /** Labels, icon and difficulties of this mode, for UI code with no access to the registry. */
    val spec: GameModeSpec
        get() = when (this) {
            COUNTRIES -> CountriesGameMode.SPEC
            CAPITALS -> CapitalsGameMode.SPEC
            FLAGS -> FlagsGameMode.SPEC
        }

    companion object {
        fun fromId(id: String): QuizMode = entries.find { it.id == id } ?: COUNTRIES
    }
}
