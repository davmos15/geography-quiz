package com.geoquiz.app.ui.ads

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.data.local.preferences.settingsDataStore
import com.geoquiz.app.data.service.ConsentManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map

/**
 * Lets this composable reach the singleton [ConsentManager] without a ViewModel. The banner is a
 * leaf UI element shared by several screens, so a per-screen ViewModel would add no value.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BannerAdEntryPoint {
    fun consentManager(): ConsentManager
}

private fun Context.consentManager(): ConsentManager =
    EntryPointAccessors.fromApplication(applicationContext, BannerAdEntryPoint::class.java)
        .consentManager()

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    // No Hilt graph or ads in previews.
    if (LocalInspectionMode.current) return

    val context = LocalContext.current
    val adsRemovedFlow = remember {
        context.settingsDataStore.data.map {
            it[booleanPreferencesKey("ads_removed")] ?: false
        }
    }
    val adsRemoved by adsRemovedFlow.collectAsStateWithLifecycle(initialValue = false)

    // Nothing is created or requested until UMP consent allows it and the SDK is tagged.
    val consentManager = remember { context.consentManager() }
    val canRequestAds by consentManager.canRequestAds.collectAsStateWithLifecycle()

    if (adsRemoved || !canRequestAds) return

    val screenWidth = LocalConfiguration.current.screenWidthDp

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidth))
                adUnitId = context.getString(R.string.admob_banner_id)
                // Child-directed tagging (AdTagging) is applied globally via RequestConfiguration.
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
