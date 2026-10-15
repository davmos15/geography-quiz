package com.geoquiz.app.ui.physicalfeatures

/**
 * Route argument names for this mode's game screen (task 7.2), the same generic shape every
 * other new mode uses ([com.geoquiz.app.domain.model.QuizCategory.typeKey]/`valueKey`,
 * `difficulty`): `categoryType` is always `"fixed_region"` ([PhysicalFeaturesCategory]),
 * `categoryValue` the tab/continent label to parse back with
 * [com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesCategory.filterFromLabel].
 *
 * Not yet wired into `ui/navigation/Screen.kt`/`ModeRoutes.kt` (shared files outside this task's
 * list, see the task 7.2 report): these names are what the lead's integration pass should use
 * for the new destination's nav arguments.
 */
object PhysicalFeaturesRouteArgs {
    const val ARG_CATEGORY_TYPE = "categoryType"
    const val ARG_CATEGORY_VALUE = "categoryValue"
    const val ARG_DIFFICULTY = "difficulty"
}
