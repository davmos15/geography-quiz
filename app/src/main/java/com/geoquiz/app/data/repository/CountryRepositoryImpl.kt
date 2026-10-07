package com.geoquiz.app.data.repository

import com.geoquiz.app.data.local.db.CapitalAliasDao
import com.geoquiz.app.data.local.db.CountryDao
import com.geoquiz.app.data.local.db.CountryEntity
import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads countries and answer aliases from the prebuilt static database
 * (`assets/databases/static.db`, built by `tools/data/build_static_db.py`).
 */
@Singleton
class CountryRepositoryImpl @Inject constructor(
    private val countryDao: CountryDao,
    private val capitalAliasDao: CapitalAliasDao,
    private val normalizeInput: NormalizeInputUseCase
) : CountryRepository {

    override fun getAllCountries(): Flow<List<Country>> {
        return countryDao.getAllCountries().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCountryCount(): Int = countryDao.getCountryCount()

    override suspend fun getCountriesByCodes(codes: List<String>): List<Country> {
        if (codes.isEmpty()) return emptyList()
        // At most ~200 codes, well under SQLite's bound-variable limit.
        val byCode = countryDao.getCountriesByCodes(codes.distinct()).associateBy { it.cca3 }
        return codes.distinct().mapNotNull { byCode[it]?.toDomain() }
    }

    override suspend fun findCountryByAnswer(input: String): Country? {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return null
        return countryDao.findCountryByNormalizedAlias(normalized)?.toDomain()
    }

    override suspend fun findCountryByCapitalAnswer(input: String): Country? {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return null
        return capitalAliasDao.findCountryByNormalizedCapitalAlias(normalized)?.toDomain()
    }

    // The static database is read-only, so the alias lists never change while the app runs.
    private val aliasCacheLock = Mutex()
    private var countryAliases: List<AnswerAlias>? = null
    private var capitalAliases: List<AnswerAlias>? = null

    override suspend fun getCountryAnswerAliases(): List<AnswerAlias> = aliasCacheLock.withLock {
        countryAliases ?: run {
            val countries = countriesByCode()
            countryDao.getAllAliases()
                .map { AnswerAlias(it.normalizedAlias, countries.getValue(it.countryCca3)) }
                .also { countryAliases = it }
        }
    }

    override suspend fun getCapitalAnswerAliases(): List<AnswerAlias> = aliasCacheLock.withLock {
        capitalAliases ?: run {
            val countries = countriesByCode()
            capitalAliasDao.getAllCapitalAliases()
                .map { AnswerAlias(it.normalizedAlias, countries.getValue(it.countryCca3)) }
                .also { capitalAliases = it }
        }
    }

    private suspend fun countriesByCode(): Map<String, Country> =
        countryDao.getAllCountriesOnce().associate { it.cca3 to it.toDomain() }

    private fun CountryEntity.toDomain() = Country(
        code = cca3,
        name = commonName,
        officialName = officialName,
        region = region,
        subregion = subregion,
        nameLength = nameLength,
        capital = capital
    )
}
