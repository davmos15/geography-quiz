package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CountryDao {

    @Query("SELECT * FROM countries ORDER BY commonName ASC")
    fun getAllCountries(): Flow<List<CountryEntity>>

    @Query("SELECT COUNT(*) FROM countries")
    suspend fun getCountryCount(): Int

    @Query(
        """
        SELECT c.* FROM countries c
        INNER JOIN aliases a ON c.cca3 = a.countryCca3
        WHERE a.normalizedAlias = :normalizedInput
        LIMIT 1
        """
    )
    suspend fun findCountryByNormalizedAlias(normalizedInput: String): CountryEntity?

    @Query("SELECT * FROM countries")
    suspend fun getAllCountriesOnce(): List<CountryEntity>

    @Query("SELECT * FROM countries WHERE cca3 IN (:codes)")
    suspend fun getCountriesByCodes(codes: List<String>): List<CountryEntity>

    @Query("SELECT * FROM aliases")
    suspend fun getAllAliases(): List<AliasEntity>
}
