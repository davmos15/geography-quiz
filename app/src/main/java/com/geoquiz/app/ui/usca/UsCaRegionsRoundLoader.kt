package com.geoquiz.app.ui.usca

import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.UsCaRegionSet
import com.geoquiz.app.domain.mode.usca.UsCaRegionsCategory
import com.geoquiz.app.domain.mode.usca.postalCode
import com.geoquiz.app.domain.mode.usca.toRegionCountry
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory

/**
 * One round's content, resolved from the route's `categoryType`/`categoryValue` (task 6.2, D43:
 * built straight from the bundled `admin1_50m.bin` layer, shared by all three variant
 * ViewModels). Two shapes:
 *
 * - A normal round: `categoryType` is `"region"`, `categoryValue` one of
 *   [UsCaRegionsCategory]'s four labels - every member of [group] ([includeDc] applying only to
 *   US states).
 * - A "Practise the ones you missed" round ([com.geoquiz.app.domain.model.CompletedQuiz.practiceCategoryOrNull]):
 *   `categoryType` is [QuizCategory.Practice.TYPE_KEY], `categoryValue` the missed admin1 codes,
 *   "+"-joined, in quiz order - exactly those codes, looked up directly in the layer (their
 *   group is inferred from the codes themselves, so this works whichever group they came from).
 */
data class UsCaRegionsRound(
    val items: List<Country>,
    val postalByCode: Map<String, String>,
    val category: QuizCategory,
    val group: UsCaRegionGroup,
    val includeDc: Boolean,
    val isPractice: Boolean
)

suspend fun loadUsCaRegionsRound(
    mapRepository: MapRepository,
    categoryTypeRaw: String,
    categoryValueRaw: String
): UsCaRegionsRound {
    val layer = mapRepository.layer(GeoLayerId.ADMIN1_50M)
    if (categoryTypeRaw == QuizCategory.Practice.TYPE_KEY) {
        val codes = categoryValueRaw.split("+").filter { it.isNotBlank() }
        val features = codes.mapNotNull { layer.feature(it) }
        // P10-D2: derive the group from the feature's own `country` property for all three
        // groups, rather than a CAN-else-US guess that silently mis-sorted Australia into US
        // states. Falls back to US states (the pre-existing safe default) if the code is
        // somehow from neither group.
        val group = when (features.firstOrNull()?.properties?.stringOrNull("country")) {
            "CAN" -> UsCaRegionGroup.CANADIAN_PROVINCES
            "AUS" -> UsCaRegionGroup.AU_STATES
            else -> UsCaRegionGroup.US_STATES
        }
        return UsCaRegionsRound(
            items = features.map { it.toRegionCountry() },
            postalByCode = features.associate { it.id to it.postalCode() },
            category = QuizCategory.Practice(codes),
            group = group,
            includeDc = true,
            isPractice = true
        )
    }
    val (group, includeDc) = UsCaRegionsCategory.groupAndDcFromLabel(categoryValueRaw)
    val features = UsCaRegionSet.members(layer, group, includeDc)
    return UsCaRegionsRound(
        items = features.map { it.toRegionCountry() },
        postalByCode = features.associate { it.id to it.postalCode() },
        category = UsCaRegionsCategory.toQuizCategory(group, includeDc),
        group = group,
        includeDc = includeDc,
        isPractice = false
    )
}
