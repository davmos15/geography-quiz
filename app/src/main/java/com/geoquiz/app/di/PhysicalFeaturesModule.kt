package com.geoquiz.app.di

import android.content.Context
import com.geoquiz.app.data.physicalfeatures.AndroidPhysicalFeatureAssetSource
import com.geoquiz.app.data.physicalfeatures.AssetPhysicalFeatureRepository
import com.geoquiz.app.data.physicalfeatures.PhysicalFeatureRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/**
 * Rivers, mountains and lakes (task 7.2): the process-wide cache of the decoded
 * `assets/physical_features/physical_features.json` list. Mirrors `di/BorderHopModule.kt`. This
 * mode's own small module, not an addition to `di/GameModeModule.kt` (shared, outside this
 * task's file list).
 */
@Module
@InstallIn(SingletonComponent::class)
object PhysicalFeaturesModule {

    @Provides
    @Singleton
    fun providePhysicalFeatureRepository(@ApplicationContext context: Context): PhysicalFeatureRepository =
        AssetPhysicalFeatureRepository(AndroidPhysicalFeatureAssetSource(context.assets), Dispatchers.IO)
}
