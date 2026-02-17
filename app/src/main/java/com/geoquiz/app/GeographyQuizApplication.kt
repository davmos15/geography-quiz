package com.geoquiz.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GeographyQuizApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayGamesSdk.initialize(this)
        MobileAds.initialize(this) {}
    }
}
