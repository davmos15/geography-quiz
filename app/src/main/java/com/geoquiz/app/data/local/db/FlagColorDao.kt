package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FlagColorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlagColors(colors: List<FlagColorEntity>)

    @Query("SELECT * FROM flag_colors")
    suspend fun getAllMappings(): List<FlagColorEntity>

    @Query("SELECT DISTINCT color FROM flag_colors ORDER BY color ASC")
    suspend fun getAllColors(): List<String>

    @Query("SELECT countryCca3 FROM flag_colors WHERE color = :color")
    suspend fun getCountryCodesForColor(color: String): List<String>

    @Query("SELECT color FROM flag_colors WHERE countryCca3 = :countryCode")
    suspend fun getColorsForCountry(countryCode: String): List<String>

    @Query("SELECT COUNT(DISTINCT color) FROM flag_colors WHERE countryCca3 = :countryCode")
    suspend fun getColorCountForCountry(countryCode: String): Int
}
