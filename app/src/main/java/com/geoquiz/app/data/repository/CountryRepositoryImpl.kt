package com.geoquiz.app.data.repository

import com.geoquiz.app.data.local.db.CapitalAliasDao
import com.geoquiz.app.data.local.db.CountryDao
import com.geoquiz.app.data.local.db.CountryEntity
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
