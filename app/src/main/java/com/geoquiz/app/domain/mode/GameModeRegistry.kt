package com.geoquiz.app.domain.mode

import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every registered [GameMode], keyed by [GameMode.id]. Modes are registered with Hilt
 * multibinding in `di/GameModeModule.kt`.
 */
@Singleton
class GameModeRegistry @Inject constructor(
    modes: Set<@JvmSuppressWildcards GameMode>
) {

    private val byId: Map<String, GameMode> = modes.groupBy { it.id }.mapValues { (id, sameId) ->
        require(sameId.size == 1) { "Game mode '$id' is registered more than once" }
        sameId.single()
    }

    /** All modes in display order, including those hidden behind a feature flag. */
    val all: List<GameMode> = byId.values.sortedWith(compareBy({ it.spec.sortOrder }, { it.id }))

    /** Countries, Capitals and Flags, in [QuizMode] order. */
    val classic: List<GameMode> get() = QuizMode.entries.map { get(it) }

    /** The mode with [id], or null if none is registered. */
    fun find(id: String): GameMode? = byId[id]

    /** The mode with [id]; throws [IllegalArgumentException] if none is registered. */
    fun require(id: String): GameMode =
        find(id) ?: throw IllegalArgumentException("Unknown game mode '$id'")

    operator fun get(mode: QuizMode): GameMode = require(mode.id)

    /** The mode with [id], or Countries for an unknown id (same fallback as [QuizMode.fromId]). */
    fun findOrDefault(id: String): GameMode = find(id) ?: get(QuizMode.COUNTRIES)

    /** Modes the player may see: released modes plus those whose flag [isEnabled]. */
    fun available(isEnabled: (FeatureFlag) -> Boolean): List<GameMode> =
        all.filter { mode -> mode.spec.featureFlag?.let(isEnabled) ?: true }
}
