package com.geoquiz.app.di

import android.content.Context
import com.geoquiz.app.data.geo.AndroidGeoAssetSource
import com.geoquiz.app.data.geo.AssetMapRepository
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.ui.map.MapPathCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/** Map layers (Phase 4): process-wide caches of the decoded `assets/geo/` files and their paths. */
@Module
@InstallIn(SingletonComponent::class)
object MapModule {

    @Provides
    @Singleton
    fun provideMapRepository(@ApplicationContext context: Context): MapRepository =
        AssetMapRepository(AndroidGeoAssetSource(context.assets), Dispatchers.IO)

    /** The same process-wide path cache the map composables read through `LocalMapPathCache`. */
    @Provides
    fun provideMapPathCache(): MapPathCache = MapPathCache.Shared
}
