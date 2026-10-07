package com.geoquiz.app.di

import android.os.SystemClock
import com.geoquiz.app.domain.time.MonotonicClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    /** Monotonic, keeps counting during deep sleep, unaffected by wall-clock changes. */
    @Provides
    fun provideMonotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
}
