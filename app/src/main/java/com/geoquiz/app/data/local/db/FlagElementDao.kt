package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface FlagElementDao {

    @Query("SELECT * FROM flag_elements")
    suspend fun getAllMappings(): List<FlagElementEntity>

    @Query("SELECT DISTINCT element FROM flag_elements ORDER BY element ASC")
    suspend fun getAllElements(): List<String>
}
