package com.geoquiz.app.ui.navigation

import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode

/**
 * Builds a route to start a quiz for any registered mode, classic or new (task 5.0). Classic ids
 * ([com.geoquiz.app.domain.model.QuizMode]) go to [Screen.Quiz]; Country silhouettes and Tap the
 * map go to their own screen.
 *
 * [variantId] only matters for Tap the map (`TapMapGameMode.VARIANT_FIND` /
 * `VARIANT_NAME_ALL`, carried on [com.geoquiz.app.domain.model.CompletedQuiz.variantId] so a
 * replay uses the same one); every other mode ignores it. A missing variant for Tap the map
 * defaults to "find".
 */
object ModeRoutes {
    /** These ids are persisted (Room, DataStore, routes) and never change. */
    const val SILHOUETTES_MODE_ID = SilhouettesGameMode.MODE_ID
    const val TAP_MAP_MODE_ID = TapMapGameMode.MODE_ID
    const val DEFAULT_TAP_MAP_VARIANT = TapMapGameMode.VARIANT_FIND
    const val BORDER_HOP_MODE_ID = BorderHopGameMode.MODE_ID

    /**
     * A fresh quiz for [modeId] over [categoryType]/[categoryValue] at [difficulty]. [challengeId]
     * only applies to classic modes (new modes carry no challenge links, D35).
     */
    fun start(
        modeId: String,
        categoryType: String,
        categoryValue: String,
        difficulty: String? = null,
        challengeId: String? = null,
        variantId: String? = null
    ): String = when (modeId) {
        SILHOUETTES_MODE_ID -> Screen.Silhouettes.createRoute(categoryType, categoryValue, difficulty)
        TAP_MAP_MODE_ID -> Screen.TapMap.createRoute(
            variant = variantId ?: DEFAULT_TAP_MAP_VARIANT,
            categoryType = categoryType,
            categoryValue = categoryValue,
            difficulty = difficulty
        )
        BORDER_HOP_MODE_ID -> Screen.BorderHop.createRoute(categoryType, categoryValue, difficulty)
        else -> Screen.Quiz.createRoute(modeId, categoryType, categoryValue, challengeId, difficulty)
    }

    /** "Play again" from Results: the same mode, category and tier (and Tap the map variant). */
    fun playAgain(
        modeId: String,
        categoryType: String,
        categoryValue: String,
        difficulty: String?,
        variantId: String? = null
    ): String = start(modeId, categoryType, categoryValue, difficulty = difficulty, variantId = variantId)

    /**
     * "Practise the ones you missed" from Results: [categoryType] and [categoryValue] are
     * already a [com.geoquiz.app.domain.model.QuizCategory.Practice] route (D21 applies to new
     * modes too, task 5.0).
     */
    fun practise(
        modeId: String,
        categoryType: String,
        categoryValue: String,
        difficulty: String?,
        variantId: String? = null
    ): String = start(modeId, categoryType, categoryValue, difficulty = difficulty, variantId = variantId)
}
