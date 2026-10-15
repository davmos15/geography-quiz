package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.model.QuizCategory

/**
 * Maps a round's [PhysicalFeatureFilter] to a [QuizCategory] (task 7.2 review). This mode has no
 * classic category filter (D51: tabs are a mode-specific concept, not region/subregion/letter),
 * so, exactly like [com.geoquiz.app.domain.mode.usca.UsCaRegionsCategory] (D43), a round uses
 * [QuizCategory.FixedRegion]: its `label` doubles as the route value
 * ([QuizCategory.valueKey]) and the display name ([QuizCategory.displayName]). Its `isOfferedIn`
 * is always false, so this label is never mistaken for a real continent by other modes'
 * `offers()` or [com.geoquiz.app.data.local.preferences.AchievementRepository]'s region
 * tracking - the same reason [UsCaRegionsCategory]'s doc gives for not reusing `ByRegion`.
 */
object PhysicalFeaturesCategory {
    private const val SEPARATOR = " - "

    /** Plain English tab name; also what Results, Stats and history show (D43's pattern: no
     * Android string resource in the domain layer, same as [UsCaRegionsCategory]'s labels). */
    fun tabLabel(tab: PhysicalFeatureTab): String = when (tab) {
        PhysicalFeatureTab.RIVERS -> "Rivers"
        PhysicalFeatureTab.MOUNTAINS -> "Mountains"
        PhysicalFeatureTab.LAKES -> "Lakes"
    }

    fun toQuizCategory(filter: PhysicalFeatureFilter): QuizCategory.FixedRegion {
        val base = tabLabel(filter.tab)
        val label = filter.continent?.let { "$base$SEPARATOR$it" } ?: base
        return QuizCategory.FixedRegion(label)
    }

    /**
     * Reverses [toQuizCategory]'s label. An unknown or missing label falls back to Rivers with no
     * continent filter, the same safe-default pattern every other category's route parsing uses
     * (`QuizCategory.fromRoute`'s catch-all, [UsCaRegionsCategory.groupAndDcFromLabel]).
     */
    fun filterFromLabel(label: String?): PhysicalFeatureFilter {
        val parts = label?.split(SEPARATOR, limit = 2).orEmpty()
        val tab = PhysicalFeatureTab.entries.find { tabLabel(it) == parts.getOrNull(0) } ?: PhysicalFeatureTab.RIVERS
        val continent = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        return PhysicalFeatureFilter(tab, continent)
    }
}
