package com.geoquiz.app.ui.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.data.local.preferences.settingsDataStore
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.flow.map

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adsRemovedFlow = remember {
        context.settingsDataStore.data.map {
            it[booleanPreferencesKey("ads_removed")] ?: false
        }
    }
    val adsRemoved by adsRemovedFlow.collectAsStateWithLifecycle(initialValue = false)

    if (adsRemoved) return

    val screenWidth = LocalConfiguration.current.screenWidthDp

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidth))
                adUnitId = context.getString(R.string.admob_banner_id)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
