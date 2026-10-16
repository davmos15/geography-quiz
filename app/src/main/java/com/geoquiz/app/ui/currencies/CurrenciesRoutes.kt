package com.geoquiz.app.ui.currencies

import android.net.Uri

/**
 * Currencies' own route (task 7.1), built the same way `Screen.Silhouettes`/`Screen.TapMap` are.
 * Now mirrored by `Screen.Currencies` in `ui/navigation/Screen.kt` and the
 * `CurrencyGameMode.MODE_ID -> Screen.Currencies.createRoute(...)` case in
 * `ui/navigation/ModeRoutes.kt`, which the lead wired in after this mode's first pass; this
 * object remains the one [CurrenciesSetupViewModel] itself builds routes from.
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
