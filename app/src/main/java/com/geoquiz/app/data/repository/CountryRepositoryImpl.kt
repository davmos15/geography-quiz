package com.geoquiz.app.data.repository

import android.content.Context
import com.geoquiz.app.data.local.db.AliasEntity
import com.geoquiz.app.data.local.db.CapitalAliasDao
import com.geoquiz.app.data.local.db.CapitalAliasEntity
import com.geoquiz.app.data.local.db.CountryDao
import com.geoquiz.app.data.local.db.CountryEntity
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagColorEntity
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val countryDao: CountryDao,
    private val capitalAliasDao: CapitalAliasDao,
    private val flagColorDao: FlagColorDao,
    private val normalizeInput: NormalizeInputUseCase
) : CountryRepository {

    private val seedMutex = Mutex()

    private val json = Json { ignoreUnknownKeys = true }

    override fun getAllCountries(): Flow<List<Country>> {
        return countryDao.getAllCountries().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCountryCount(): Int {
        ensureSeeded()
        return countryDao.getCountryCount()
    }

    override suspend fun findCountryByAnswer(input: String): Country? {
        ensureSeeded()
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return null
        return countryDao.findCountryByNormalizedAlias(normalized)?.toDomain()
    }

    override suspend fun findCountryByCapitalAnswer(input: String): Country? {
        ensureSeeded()
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return null
        return capitalAliasDao.findCountryByNormalizedCapitalAlias(normalized)?.toDomain()
    }

    override suspend fun ensureSeeded() {
        if (countryDao.getCountryCount() > 0) return
        seedMutex.withLock {
            if (countryDao.getCountryCount() > 0) return
            seedFromAsset()
        }
    }

    private suspend fun seedFromAsset() {
        val rawJson = context.assets.open("countries.json")
            .bufferedReader().use { it.readText() }

        val parsed = json.decodeFromString<List<CountryJson>>(rawJson)
            .filter { it.unMember }

        // Load flag colors
        val flagColorsRaw = context.assets.open("flag_colors.json")
            .bufferedReader().use { it.readText() }
        val flagColorsMap = json.decodeFromString<Map<String, List<String>>>(flagColorsRaw)

        val entities = mutableListOf<CountryEntity>()
        val aliases = mutableListOf<AliasEntity>()
        val capitalAliases = mutableListOf<CapitalAliasEntity>()
        val flagColorEntities = mutableListOf<FlagColorEntity>()

        for (c in parsed) {
            val commonName = c.name.common
            val officialName = c.name.official
            val region = c.region
            val subregion = c.subregion ?: ""
            val cca3 = c.cca3
            val capital = c.capital.firstOrNull() ?: ""
            val flag = c.flag ?: ""

            entities.add(
                CountryEntity(
                    cca3 = cca3,
                    commonName = commonName,
                    officialName = officialName,
                    region = region,
                    subregion = subregion,
                    nameLength = commonName.length,
                    capital = capital,
                    flag = flag
                )
            )

            // Country name aliases
            val aliasSet = mutableSetOf<String>()
            aliasSet.add(commonName)
            aliasSet.add(officialName)
            c.altSpellings.forEach { aliasSet.add(it) }

            val abbreviations = ABBREVIATIONS[cca3]
            if (abbreviations != null) {
                aliasSet.addAll(abbreviations)
            }

            val allowedShort = abbreviations?.toSet() ?: emptySet()

            for (alias in aliasSet) {
                if (alias.isBlank()) continue
                if (alias.length <= 3 && alias.all { it.isUpperCase() } && alias !in allowedShort) continue
                aliases.add(
                    AliasEntity(
                        countryCca3 = cca3,
                        alias = alias,
                        normalizedAlias = normalizeInput(alias)
                    )
                )
            }

            // Capital aliases
            val capitalSet = mutableSetOf<String>()
            c.capital.forEach { capitalSet.add(it) }
            // Add custom capital aliases
            CAPITAL_ALIASES[cca3]?.forEach { capitalSet.add(it) }

            for (cap in capitalSet) {
                if (cap.isBlank()) continue
                capitalAliases.add(
                    CapitalAliasEntity(
                        countryCca3 = cca3,
                        alias = cap,
                        normalizedAlias = normalizeInput(cap)
                    )
                )
            }

            // Flag colors - use cca2 to look up in flag_colors.json, but store with cca3
            val cca2 = c.cca2
            val colors = flagColorsMap[cca3] ?: flagColorsMap[cca2] ?: emptyList()
            for (color in colors) {
                flagColorEntities.add(
                    FlagColorEntity(
                        countryCca3 = cca3,
                        color = color
                    )
                )
            }
        }

        countryDao.insertCountries(entities)
        countryDao.insertAliases(aliases)
        capitalAliasDao.insertAliases(capitalAliases)
        flagColorDao.insertFlagColors(flagColorEntities)
    }

    private fun CountryEntity.toDomain() = Country(
        code = cca3,
        name = commonName,
        officialName = officialName,
        region = region,
        subregion = subregion,
        nameLength = nameLength,
        capital = capital,
        flag = flag
    )

    companion object {
        private val ABBREVIATIONS = mapOf(
            "GBR" to listOf("UK", "Britain", "Great Britain"),
            "USA" to listOf("US", "America", "United States"),
            "ARE" to listOf("UAE"),
            "COD" to listOf("DRC", "DR Congo", "Congo Kinshasa"),
            "COG" to listOf("Congo Brazzaville", "Republic of the Congo", "Republic of Congo"),
            "PRK" to listOf("DPRK", "North Korea"),
            "KOR" to listOf("South Korea"),
            "CIV" to listOf("Ivory Coast"),
            "SWZ" to listOf("Swaziland"),
            "MKD" to listOf("Macedonia"),
            "CZE" to listOf("Czech Republic", "Czechia"),
            "TLS" to listOf("East Timor"),
            "MMR" to listOf("Burma"),
            "RUS" to listOf("Russia"),
            "BRN" to listOf("Brunei"),
            "LAO" to listOf("Laos"),
            "FSM" to listOf("Micronesia"),
            "VCT" to listOf("St Vincent", "Saint Vincent"),
            "KNA" to listOf("St Kitts", "Saint Kitts"),
            "LCA" to listOf("St Lucia", "Saint Lucia"),
            "STP" to listOf("Sao Tome"),
            "BIH" to listOf("Bosnia"),
            "TTO" to listOf("Trinidad"),
            "ATG" to listOf("Antigua"),
            "PNG" to listOf("Papua New Guinea"),
            "CAF" to listOf("Central African Republic"),
            "GNQ" to listOf("Equatorial Guinea"),
            "DOM" to listOf("Dominican Republic"),
            "SLB" to listOf("Solomon Islands"),
            "MHL" to listOf("Marshall Islands"),
            "CPV" to listOf("Cape Verde"),
        )

        private val CAPITAL_ALIASES = mapOf(
            "TUR" to listOf("Ankara"),
            "CZE" to listOf("Prague"),
            "MMR" to listOf("Naypyidaw", "Nay Pyi Taw"),
            "LKA" to listOf("Colombo", "Sri Jayawardenepura Kotte", "Kotte"),
            "MYS" to listOf("Kuala Lumpur", "KL"),
            "ZAF" to listOf("Pretoria", "Cape Town", "Bloemfontein"),
            "BRN" to listOf("Bandar Seri Begawan"),
            "SWZ" to listOf("Mbabane", "Lobamba"),
            "CPV" to listOf("Praia"),
            "CIV" to listOf("Yamoussoukro"),
            "COD" to listOf("Kinshasa"),
            "COG" to listOf("Brazzaville"),
            "KOR" to listOf("Seoul"),
            "PRK" to listOf("Pyongyang"),
            "TLS" to listOf("Dili"),
            "FSM" to listOf("Palikir"),
            "MHL" to listOf("Majuro"),
            "SLB" to listOf("Honiara"),
        )
    }
}

@Serializable
private data class CountryJson(
    val cca2: String = "",
    val cca3: String,
    val name: NameJson,
    val region: String,
    val subregion: String? = null,
    val altSpellings: List<String> = emptyList(),
    val unMember: Boolean = false,
    val capital: List<String> = emptyList(),
    val flag: String? = null
)

@Serializable
private data class NameJson(
    val common: String,
    val official: String
)
