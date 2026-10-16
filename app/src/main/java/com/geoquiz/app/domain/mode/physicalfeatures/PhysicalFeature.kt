package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.model.Country

/**
 * A curated feature's kind (task 7.2, `data/physical_features.json`'s `type` field). Mountain
 * ranges and individual peaks are both shown under the "Mountains" tab ([PhysicalFeatureTab]).
 */
enum class PhysicalFeatureType(val jsonValue: String) {
    RIVER("river"),
    RANGE("range"),
    PEAK("peak"),
    LAKE("lake");

    companion object {
        fun fromJsonValue(value: String): PhysicalFeatureType? = entries.find { it.jsonValue == value }
    }
}

/**
 * The setup screen's tab (task 7.2, D51): "Rivers, mountains and lakes" is one mode and one
 * [com.geoquiz.app.domain.model.FeatureFlag] (`PHYSICAL_FEATURES`), not three; the tab is a
 * content filter, same spirit as [PhysicalFeatureFilter].
 */
enum class PhysicalFeatureTab {
    RIVERS, MOUNTAINS, LAKES;

    fun matches(type: PhysicalFeatureType): Boolean = when (this) {
        RIVERS -> type == PhysicalFeatureType.RIVER
        MOUNTAINS -> type == PhysicalFeatureType.RANGE || type == PhysicalFeatureType.PEAK
        LAKES -> type == PhysicalFeatureType.LAKE
    }
}

/**
 * One curated feature from `data/physical_features.json` (task 7.2, D47 public domain/CC0, D49
 * tiers): 101 rivers, mountain ranges, peaks and lakes, each cross-checked against Wikidata.
 *
 * @property id stable, lower-case, hyphenated (e.g. `"aral-sea"`) - never a cca3 code. Used as
 *   the synthetic [Country.code] ([toQuizCountry]) and as this mode's quiz item identity
 *   everywhere (answered/missed sets, Easy's [com.geoquiz.app.domain.mode.ChoiceQuestion]).
 * @property aliases alternate accepted names (e.g. "Lake Nyasa" for Lake Malawi), matched the
 *   same way [name] is ([PhysicalFeatureAnswerValidator]); this mode has no Room alias table.
 * @property layerId the bundled map layer this feature's geometry lives in
 *   ([GeoLayerId.RIVERS_50M], [GeoLayerId.LAKES_50M], [GeoLayerId.REGIONS_50M] for ranges, or
 *   [GeoLayerId.GEO_POINTS_50M] for peaks and Vinson Massif).
 * @property neIds Natural Earth ids of this feature's part(s) in [layerId] - a [GeoLayer][com.geoquiz.app.domain.map.GeoLayer]
 *   feature's own `id` for these four layers *is* its Natural Earth id as a string
 *   (`data/geo/FORMAT.md`; confirmed against `tools/geodata/build_geodata.py`'s `ne_id()`/
 *   `group_by_ne_id()`), so resolving one is a direct [com.geoquiz.app.domain.map.GeoLayer.feature]
 *   lookup, no separate cross-reference table. More than one id means Natural Earth kept the
 *   feature as several records (e.g. the Aral Sea's North and South halves after it shrank).
 * @property bbox `[minLon, minLat, maxLon, maxLat]` in degrees, precomputed by the data-prep
 *   pipeline: framing a question uses this directly ([physicalFeatureFrame]), not geometry.
 * @property tier 1-3, a content-curation concept (D49), not this mode's game difficulty; unused
 *   by the game logic itself, kept for a possible future "harder" distractor pool.
 */
data class PhysicalFeature(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val type: PhysicalFeatureType,
    val layerId: GeoLayerId,
    val neIds: List<String>,
    val countries: List<String>,
    val continent: String,
    val bbox: GeoBounds,
    val tier: Int,
    val wikidata: String
)

/**
 * This feature as a synthetic [Country] (task 7.2, the same field-repurposing convention
 * [com.geoquiz.app.domain.mode.usca.UsCaRegionSet]'s `GeoFeature.toRegionCountry()` and
 * [com.geoquiz.app.domain.mode.borderhop.BorderHopScoring] use, D37/D43): the carrier
 * [com.geoquiz.app.domain.model.QuizState]/[com.geoquiz.app.domain.model.Quiz]/
 * [com.geoquiz.app.domain.mode.ChoiceQuestion] already need. [Country.code] is this feature's
 * [PhysicalFeature.id] (never a cca3 code); [Country.region] is [continent] (handy if a future
 * change wants same-continent Easy distractors, though D51's spec only asks for "same tab");
 * [Country.subregion]/[capital] are unused placeholders, never written to
 * [com.geoquiz.app.domain.repository.CountryRepository] or Room.
 */
fun PhysicalFeature.toQuizCountry(): Country = Country(
    code = id,
    name = name,
    officialName = name,
    region = continent,
    subregion = "",
    nameLength = name.length,
    capital = ""
)
