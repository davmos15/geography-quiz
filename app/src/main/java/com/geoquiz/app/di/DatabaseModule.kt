package com.geoquiz.app.di

import android.content.Context
import androidx.room.Room
import com.geoquiz.app.data.local.db.AppDatabase
import com.geoquiz.app.data.local.db.CountryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "geoquiz.db"
        ).build()
    }

    @Provides
    fun provideCountryDao(database: AppDatabase): CountryDao {
        return database.countryDao()
    }
}
