package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CountryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountries(countries: List<CountryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(aliases: List<AliasEntity>)

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
}
