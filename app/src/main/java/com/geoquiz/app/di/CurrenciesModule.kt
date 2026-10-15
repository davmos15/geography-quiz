package com.geoquiz.app.di

import android.content.Context
import com.geoquiz.app.data.currencies.AndroidCurrencyAssetSource
import com.geoquiz.app.data.currencies.AssetCurrencyRepository
import com.geoquiz.app.data.currencies.CurrencyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/** Currencies (task 7.1): the process-wide cache of the decoded `assets/currencies/currencies.json` data. */
@Module
@InstallIn(SingletonComponent::class)
object CurrenciesModule {

    @Provides
    @Singleton
    fun provideCurrencyRepository(@ApplicationContext context: Context): CurrencyRepository =
        AssetCurrencyRepository(AndroidCurrencyAssetSource(context.assets), Dispatchers.IO)
}
