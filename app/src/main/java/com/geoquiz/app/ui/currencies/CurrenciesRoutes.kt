package com.geoquiz.app.ui.currencies

import android.net.Uri

/**
 * Currencies' own route (task 7.1), built the same way `Screen.Silhouettes`/`Screen.TapMap` are:
 * this mode has no entry yet in `ui/navigation/Screen.kt`/`AppNavigation.kt`/`ui/navigation/ModeRoutes.kt`
 * (shared files for the lead's later integration wave, see the task brief), so this screen
 * navigates with a plain route string of its own rather than `ModeRoutes.start` (whose `when`
 * has no `CurrencyGameMode.MODE_ID` case yet and would wrongly fall back to the classic
 * `Screen.Quiz` route).
 *
 * Suggested wiring for that pass: a `Screen.Currencies` data object with
 * [ROUTE_PATTERN], and one `CurrencyGameMode.MODE_ID -> Screen.Currencies.createRoute(...)` case
 * in `ModeRoutes.start`/`playAgain`/`practise`, exactly like `Screen.Silhouettes`'s.
 */
object CurrenciesRoutes {
    const val ARG_DIFFICULTY = "difficulty"

    /** For `NavHost`/`composable(...)` registration once this mode is wired into the nav graph. */
    const val ROUTE_PATTERN = "currencies/{categoryType}/{categoryValue}?difficulty={difficulty}"

    fun createRoute(categoryType: String, categoryValue: String, difficulty: String? = null): String {
        val encoded = Uri.encode(categoryValue)
        val base = "currencies/$categoryType/$encoded"
        return difficulty?.let { "$base?$ARG_DIFFICULTY=${Uri.encode(it)}" } ?: base
    }
}
