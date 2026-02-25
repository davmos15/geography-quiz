package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FlagElementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlagElements(elements: List<FlagElementEntity>)

    @Query("SELECT * FROM flag_elements")
    suspend fun getAllMappings(): List<FlagElementEntity>

    @Query("SELECT DISTINCT element FROM flag_elements ORDER BY element ASC")
    suspend fun getAllElements(): List<String>
}
