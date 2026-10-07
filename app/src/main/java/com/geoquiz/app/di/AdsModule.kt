package com.geoquiz.app.di

import com.geoquiz.app.data.service.ConsentGateway
import com.geoquiz.app.data.service.GoogleConsentGateway
import com.geoquiz.app.data.service.GoogleInterstitialAdLoader
import com.geoquiz.app.data.service.InterstitialAdLoader
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Main-thread scope for ad work (the Mobile Ads SDK must load ads on the main thread). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AdsMainScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsModule {

    @Binds
    @Singleton
    abstract fun bindConsentGateway(impl: GoogleConsentGateway): ConsentGateway

    @Binds
    abstract fun bindInterstitialAdLoader(impl: GoogleInterstitialAdLoader): InterstitialAdLoader

    companion object {
        @Provides
        @Singleton
        @AdsMainScope
        fun provideAdsMainScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
