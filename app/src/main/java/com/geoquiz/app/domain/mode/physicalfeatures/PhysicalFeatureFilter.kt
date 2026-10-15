package com.geoquiz.app.domain.mode.physicalfeatures

/**
 * The setup screen's own filter concept (task 7.2 brief): a tab plus an optional continent, not
 * a [com.geoquiz.app.domain.model.QuizCategory] (D43's precedent: none of that sealed class's
 * cases fit 101 curated features with no region/subregion/letter structure of their own).
 * [continent] null means every continent in [tab].
 */
data class PhysicalFeatureFilter(val tab: PhysicalFeatureTab, val continent: String? = null)

/** Every feature in [tab], any continent (the pool Easy's distractors are drawn from, D51: "4
 * feature names from the SAME type tab" - continent is not part of that rule). */
fun List<PhysicalFeature>.inTab(tab: PhysicalFeatureTab): List<PhysicalFeature> = filter { tab.matches(it.type) }

/** Every feature matching [filter] (tab, and continent if set): a round's candidate pool. */
fun List<PhysicalFeature>.matching(filter: PhysicalFeatureFilter): List<PhysicalFeature> =
    inTab(filter.tab).filter { filter.continent == null || it.continent == filter.continent }

/** Continents that have at least one feature in [tab], sorted, for the setup screen's picker. */
fun List<PhysicalFeature>.continentsFor(tab: PhysicalFeatureTab): List<String> =
    inTab(tab).map { it.continent }.distinct().sorted()
