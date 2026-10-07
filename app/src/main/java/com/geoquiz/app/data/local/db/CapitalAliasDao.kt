package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CapitalAliasDao {

    @Query(
        """
        SELECT c.* FROM countries c
        INNER JOIN capital_aliases ca ON c.cca3 = ca.countryCca3
        WHERE ca.normalizedAlias = :normalizedInput
        LIMIT 1
        """
    )
    suspend fun findCountryByNormalizedCapitalAlias(normalizedInput: String): CountryEntity?

    @Query("SELECT * FROM capital_aliases")
    suspend fun getAllCapitalAliases(): List<CapitalAliasEntity>
}
