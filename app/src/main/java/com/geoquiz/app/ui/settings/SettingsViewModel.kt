package com.geoquiz.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.service.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {

    val showTimer: StateFlow<Boolean> = settingsRepository.showTimer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showFlags: StateFlow<Boolean> = settingsRepository.showFlags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showCountryHint: StateFlow<Boolean> = settingsRepository.showCountryHint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hardMode: StateFlow<Boolean> = settingsRepository.hardMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val adsRemoved: StateFlow<Boolean> = billingRepository.adsRemoved
    val removeAdsPrice: StateFlow<String?> = billingRepository.price

    fun onToggleTimer(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTimer(show)
        }
    }

    fun onToggleShowFlags(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowFlags(show)
        }
    }

    fun onToggleShowCountryHint(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowCountryHint(show)
        }
    }

    fun onToggleHardMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHardMode(enabled)
        }
    }

    fun purchaseRemoveAds(activity: Activity) {
        billingRepository.launchPurchaseFlow(activity)
    }

    fun restorePurchases() {
        viewModelScope.launch {
            billingRepository.restorePurchases()
        }
    }
}
