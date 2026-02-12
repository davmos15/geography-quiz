package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CapitalAliasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(aliases: List<CapitalAliasEntity>)

    @Query(
        """
        SELECT c.* FROM countries c
        INNER JOIN capital_aliases ca ON c.cca3 = ca.countryCca3
        WHERE ca.normalizedAlias = :normalizedInput
        LIMIT 1
        """
    )
    suspend fun findCountryByNormalizedCapitalAlias(normalizedInput: String): CountryEntity?
}
