package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.model.Country

/**
 * Which fixed member set a round draws from (task 6.2; Australia added P10-D2): "US states" (50
 * states + DC, DC optional), "Canadian provinces and territories" (13, no optional member) or
 * "Australian states and territories" (8, no optional member). A route value (`us`/`ca`/`au`),
 * never changed, and the admin1 layer's `country` property (`data/geo/FORMAT.md`) that selects
 * this group's features.
 */
enum class UsCaRegionGroup(val routeValue: String, val countryProperty: String) {
    US_STATES("us", "USA"),
    CANADIAN_PROVINCES("ca", "CAN"),
    AU_STATES("au", "AUS");

    companion object {
        fun fromRouteOrNull(value: String?): UsCaRegionGroup? = entries.firstOrNull { it.routeValue == value }
    }
}

/**
 * US states + DC, Canadian provinces and territories, and Australian states and territories
 * (task 6.2; Australia added P10-D2): a fixed ISO 3166-2 member set read straight from the
 * bundled `admin1_50m.bin` layer (via [GeoLayerId.ADMIN1_50M] and
 * [com.geoquiz.app.domain.map.MapRepository]), not derived from
 * [com.geoquiz.app.domain.model.Country]/[com.geoquiz.app.domain.map.RegionPreset] filtering
 * (D43): those are typed to the app's 197 UN-member countries and have no notion of a US state,
 * a Canadian province or an Australian state/territory.
 */
object UsCaRegionSet {
    /** Washington DC's admin1 id: the only member [includeDc] can ever exclude. */
    const val DC_CODE = "US-DC"

    private const val PROP_COUNTRY = "country"

    /**
     * [group]'s members from [layer], sorted by English name. DC is left out unless [includeDc]
     * (meaningless for [UsCaRegionGroup.CANADIAN_PROVINCES] and [UsCaRegionGroup.AU_STATES],
     * neither of which has an optional member).
     */
    fun members(layer: GeoLayer, group: UsCaRegionGroup, includeDc: Boolean): List<GeoFeature> =
        layer.features
            .filter { it.properties.stringOrNull(PROP_COUNTRY) == group.countryProperty }
            .filter { includeDc || it.id != DC_CODE }
            .sortedBy { it.regionName() }
}

/** English name of an admin1 feature (`name_en`, falling back to `name` if that is blank). */
fun GeoFeature.regionName(): String =
    properties.stringOrNull("name_en")?.takeIf { it.isNotBlank() } ?: properties.string("name")

/** Postal abbreviation of an admin1 feature (`postal`), or blank if the property is missing. */
fun GeoFeature.postalCode(): String = properties.stringOrNull("postal").orEmpty()

/**
 * This admin1 feature as a synthetic [Country] (task 6.2, D43): [Country.code] is the feature's
 * ISO 3166-2 id (`US-CA`), [Country.name]/[Country.officialName] its English name. This is the
 * carrier the shared quiz/map/multiple-choice plumbing ([com.geoquiz.app.domain.model.QuizState],
 * [com.geoquiz.app.domain.model.Quiz], [com.geoquiz.app.ui.map.MapListAlternative],
 * [com.geoquiz.app.domain.mode.ChoiceQuestionGenerator]) already needs - the same pattern Border
 * hop used to pack its own round into a plain [com.geoquiz.app.domain.model.QuizState] (D37,
 * D44). [Country.region]/[subregion]/[capital] are unused placeholders; this value is never
 * written to [com.geoquiz.app.domain.repository.CountryRepository] or Room, and the actual
 * gameplay logic (answer matching, choice distractors) works from the admin1 feature's own name
 * and postal code, not from any region/subregion/alias lookup (D43).
 */
fun GeoFeature.toRegionCountry(): Country {
    val name = regionName()
    return Country(
        code = id,
        name = name,
        officialName = name,
        region = "",
        subregion = "",
        nameLength = name.length,
        capital = ""
    )
}
