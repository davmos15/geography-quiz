package com.geoquiz.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CountryEntity::class, AliasEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun countryDao(): CountryDao
}
