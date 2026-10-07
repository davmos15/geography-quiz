package com.geoquiz.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.geoquiz.app.data.local.db.RoomTransactionRunner
import com.geoquiz.app.data.local.db.TransactionRunner
import com.geoquiz.app.data.local.preferences.achievementDataStore
import com.geoquiz.app.data.local.preferences.settingsDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** The "settings" DataStore (same instance [com.geoquiz.app.data.local.preferences.SettingsRepository] uses). */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore

/** The "achievements" DataStore (same instance [com.geoquiz.app.data.local.preferences.AchievementRepository] uses). */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AchievementStore

/** Bindings used by "Reset all data" ([com.geoquiz.app.domain.usecase.ResetAllDataUseCase]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UserDataModule {

    @Binds
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner

    companion object {
        // The preferencesDataStore delegate is a process-wide singleton per context, so these
        // return the same instances the repositories read from.
        @Provides
        @SettingsStore
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.settingsDataStore

        @Provides
        @AchievementStore
        fun provideAchievementDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.achievementDataStore
    }
}
