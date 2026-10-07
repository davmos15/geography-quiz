package com.geoquiz.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.geoquiz.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FeatureFlagStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FeatureFlagOverridesAllowed

private val Context.featureFlagDataStore: DataStore<Preferences> by preferencesDataStore(name = "feature_flags")

@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagModule {

    @Provides
    @Singleton
    @FeatureFlagStore
    fun provideFeatureFlagDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.featureFlagDataStore

    @Provides
    @FeatureFlagOverridesAllowed
    fun provideFeatureFlagOverridesAllowed(): Boolean = BuildConfig.DEBUG
}
