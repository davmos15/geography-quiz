package com.geoquiz.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CountryEntity::class,
        AliasEntity::class,
        SavedQuizEntity::class,
        ChallengeEntity::class,
        CapitalAliasEntity::class,
        FlagColorEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun countryDao(): CountryDao
    abstract fun savedQuizDao(): SavedQuizDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun capitalAliasDao(): CapitalAliasDao
    abstract fun flagColorDao(): FlagColorDao
}
