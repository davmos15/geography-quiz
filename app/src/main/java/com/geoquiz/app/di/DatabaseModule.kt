package com.geoquiz.app.di

import android.content.Context
import androidx.room.Room
import com.geoquiz.app.data.local.db.AppDatabase
import com.geoquiz.app.data.local.db.AppDatabaseMigrations
import com.geoquiz.app.data.local.db.CapitalAliasDao
import com.geoquiz.app.data.local.db.ChallengeDao
import com.geoquiz.app.data.local.db.CountryDao
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.data.local.db.QuizHistoryDao
import com.geoquiz.app.data.local.db.SavedQuizDao
import com.geoquiz.app.data.local.db.StaticDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Player data. Never destructive: every schema change needs a migration. */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabaseMigrations.ALL)
            .build()
    }

    /**
     * Static game content, copied from the prebuilt asset on first open. When the
     * asset's version is higher than the installed copy, the destructive fallback
     * replaces the installed copy with the new asset (no migrations, by design).
     */
    @Provides
    @Singleton
    fun provideStaticDatabase(@ApplicationContext context: Context): StaticDatabase {
        return Room.databaseBuilder(context, StaticDatabase::class.java, StaticDatabase.NAME)
            .createFromAsset(StaticDatabase.ASSET_PATH)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCountryDao(database: StaticDatabase): CountryDao {
        return database.countryDao()
    }

    @Provides
    fun provideCapitalAliasDao(database: StaticDatabase): CapitalAliasDao {
        return database.capitalAliasDao()
    }

    @Provides
    fun provideFlagColorDao(database: StaticDatabase): FlagColorDao {
        return database.flagColorDao()
    }

    @Provides
    fun provideFlagElementDao(database: StaticDatabase): FlagElementDao {
        return database.flagElementDao()
    }

    @Provides
    fun provideSavedQuizDao(database: AppDatabase): SavedQuizDao {
        return database.savedQuizDao()
    }

    @Provides
    fun provideChallengeDao(database: AppDatabase): ChallengeDao {
        return database.challengeDao()
    }

    @Provides
    fun provideQuizHistoryDao(database: AppDatabase): QuizHistoryDao {
        return database.quizHistoryDao()
    }
}
