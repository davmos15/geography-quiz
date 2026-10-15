package com.geoquiz.app.di

import android.content.Context
import com.geoquiz.app.data.borderhop.AndroidBorderGraphAssetSource
import com.geoquiz.app.data.borderhop.AssetBorderGraphRepository
import com.geoquiz.app.data.borderhop.BorderGraphRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/** Border hop (task 6.1): the process-wide cache of the decoded `assets/border_hop/borders.json` graph. */
@Module
@InstallIn(SingletonComponent::class)
object BorderHopModule {

    @Provides
    @Singleton
    fun provideBorderGraphRepository(@ApplicationContext context: Context): BorderGraphRepository =
        AssetBorderGraphRepository(AndroidBorderGraphAssetSource(context.assets), Dispatchers.IO)
}
