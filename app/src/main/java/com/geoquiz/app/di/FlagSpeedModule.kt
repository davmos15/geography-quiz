package com.geoquiz.app.di

import android.content.Context
import com.geoquiz.app.data.flagspeed.AndroidFlagLookalikeAssetSource
import com.geoquiz.app.data.flagspeed.AssetFlagLookalikeRepository
import com.geoquiz.app.data.flagspeed.FlagLookalikeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/**
 * Flag speed round (task 7.3): the process-wide cache of the decoded
 * `assets/flag_speed/flag_lookalikes.json` data. Mirrors `di/BorderHopModule.kt`'s pattern
 * exactly. The mode's registration into `GameModeRegistry`'s `@IntoSet` multibinding lives
 * solely in `di/GameModeModule.kt` (the lead's integration pass) - duplicating it here as well
 * caused a `[Dagger/SetMultibinding] Multiple set contributions` build failure, since Dagger
 * multibinding keys by contribution, not by module, so the same `GameMode` instance can't be
 * bound into the set from two different modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object FlagSpeedModule {

    @Provides
    @Singleton
    fun provideFlagLookalikeRepository(@ApplicationContext context: Context): FlagLookalikeRepository =
        AssetFlagLookalikeRepository(AndroidFlagLookalikeAssetSource(context.assets), Dispatchers.IO)
}
