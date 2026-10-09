package com.geoquiz.app.ui.category

import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagColorEntity
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.data.local.db.FlagElementEntity
import com.geoquiz.app.domain.model.Country
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Countries and flag data that give every category group some options: letter and word
 * patterns, islands, a capital that matches its country, a country without a capital, the
 * renamed subregion, two- and three-colour combos and mappings for a country outside the list.
 * Not real data; built to exercise the option rules.
 */
object CategoryOptionsFixture {

    private fun country(code: String, name: String, capital: String, region: String, subregion: String) =
        Country(
            code = code,
            name = name,
            officialName = name,
            region = region,
            subregion = subregion,
            nameLength = name.length,
            capital = capital
        )

    val COUNTRIES: List<Country> = listOf(
        country("FRA", "France", "Paris", "Europe", "Western Europe"),
        country("DEU", "Germany", "Berlin", "Europe", "Western Europe"),
        country("GBR", "United Kingdom", "London", "Europe", "Northern Europe"),
        country("ISL", "Iceland", "Reykjavik", "Europe", "Northern Europe"),
        country("KAZ", "Kazakhstan", "Astana", "Asia", "Central Asia"),
        country("PHL", "Philippines", "Manila", "Asia", "South-eastern Asia"),
        country("MAC", "Macau", "", "Asia", "Eastern Asia"),
        country("PNG", "Papua New Guinea", "Port Moresby", "Oceania", "Melanesia"),
        country("SLB", "Solomon Islands", "Honiara", "Oceania", "Melanesia"),
        country("AUS", "Australia", "Canberra", "Oceania", "Australia and New Zealand"),
        country("NZL", "New Zealand", "Wellington", "Oceania", "Australia and New Zealand"),
        country("ZAF", "South Africa", "Pretoria", "Africa", "Southern Africa"),
        country("MOZ", "Mozambique", "Maputo", "Africa", "Eastern Africa"),
        country("TCD", "Chad", "N'Djamena", "Africa", "Middle Africa"),
        country("DJI", "Djibouti", "Djibouti", "Africa", "Eastern Africa"),
        country("MEX", "Mexico", "Mexico City", "Americas", "North America"),
        country("ARG", "Argentina", "Buenos Aires", "Americas", "South America")
    )

    val FLAG_COLOURS: List<FlagColorEntity> = listOf(
        "FRA" to "blue", "FRA" to "white", "FRA" to "red",
        "GBR" to "blue", "GBR" to "white", "GBR" to "red",
        "ISL" to "blue", "ISL" to "white", "ISL" to "red",
        "NZL" to "blue", "NZL" to "white", "NZL" to "red",
        "AUS" to "blue", "AUS" to "white", "AUS" to "red",
        "DEU" to "black", "DEU" to "red", "DEU" to "yellow",
        "KAZ" to "blue", "KAZ" to "yellow",
        "DJI" to "blue", "DJI" to "yellow",
        "ZAF" to "red", "ZAF" to "white",
        "MOZ" to "red", "MOZ" to "white",
        "MEX" to "green", "MEX" to "white", "MEX" to "red",
        "PHL" to "blue", "PHL" to "red", "PHL" to "white", "PHL" to "yellow",
        "ARG" to "blue", "ARG" to "white", "ARG" to "yellow",
        // Not in COUNTRIES: must be ignored
        "ZZZ" to "purple", "ZZZ" to "red"
    ).map { (code, colour) -> FlagColorEntity(code, colour) }

    val FLAG_ELEMENTS: List<FlagElementEntity> = listOf(
        "AUS" to "star", "NZL" to "star", "PHL" to "star", "PHL" to "sun", "ARG" to "sun",
        "GBR" to "cross", "ISL" to "cross", "MEX" to "eagle",
        "ZZZ" to "dragon"
    ).map { (code, element) -> FlagElementEntity(code, element) }

    fun flagColorDao(): FlagColorDao = mockk { coEvery { getAllMappings() } returns FLAG_COLOURS }

    fun flagElementDao(): FlagElementDao = mockk { coEvery { getAllMappings() } returns FLAG_ELEMENTS }

    fun builder() = CategoryOptionsBuilder(flagColorDao(), flagElementDao())
}
