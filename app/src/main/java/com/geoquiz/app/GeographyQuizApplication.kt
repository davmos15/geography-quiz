package com.geoquiz.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.geoquiz.app.ui.map.CountryOutlinePathCache
import com.geoquiz.app.ui.map.MapPathCache
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GeographyQuizApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayGamesSdk.initialize(this)
        // The Mobile Ads SDK is initialised by ConsentManager, only after UMP consent allows it.
    }

    /**
     * Drops the process-wide map path caches ([MapPathCache.Shared],
     * [CountryOutlinePathCache.Shared]) once the UI is no longer visible, or sooner under real
     * memory pressure (task 9.2 review fix): both are otherwise only cleared by tests, so a
     * backgrounded app would keep every region preset's built paths, and every silhouette's
     * outline, for the life of the process. `Application` already implements
     * [ComponentCallbacks2], so no extra registration is needed. Safe to call while a build is in
     * flight - see each cache's `clear()` doc for why.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            MapPathCache.Shared.clear()
            CountryOutlinePathCache.Shared.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MapPathCache.Shared.clear()
        CountryOutlinePathCache.Shared.clear()
    }
}
