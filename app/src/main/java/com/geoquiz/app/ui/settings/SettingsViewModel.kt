package com.geoquiz.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.service.BillingRepository
import com.geoquiz.app.data.service.ConsentManager
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.usecase.ResetAllDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of "Reset all data", shown as a snackbar once finished. */
enum class ResetStatus { IDLE, IN_PROGRESS, DONE, FAILED }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository,
    private val consentManager: ConsentManager,
    private val resetAllData: ResetAllDataUseCase
) : ViewModel() {

    val showTimer: StateFlow<Boolean> = settingsRepository.showTimer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showFlags: StateFlow<Boolean> = settingsRepository.showFlags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showCountryHint: StateFlow<Boolean> = settingsRepository.showCountryHint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** The remembered default tier (also changed from the category list). */
    val difficulty: StateFlow<Difficulty> = settingsRepository.difficulty
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Difficulty.DEFAULT)

    val adsRemoved: StateFlow<Boolean> = billingRepository.adsRemoved
    val removeAdsPrice: StateFlow<String?> = billingRepository.price

    /** The "Privacy options" row is only shown when UMP says an entry point is required. */
    val privacyOptionsRequired: StateFlow<Boolean> = consentManager.privacyOptionsRequired

    private val _resetStatus = MutableStateFlow(ResetStatus.IDLE)
    val resetStatus: StateFlow<ResetStatus> = _resetStatus.asStateFlow()

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

    fun onDifficultySelected(difficulty: Difficulty) {
        viewModelScope.launch {
            settingsRepository.setDifficulty(difficulty)
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

    fun showPrivacyOptions(activity: Activity) {
        consentManager.showPrivacyOptionsForm(activity) {}
    }

    fun onResetAllData() {
        if (_resetStatus.value == ResetStatus.IN_PROGRESS) return
        _resetStatus.value = ResetStatus.IN_PROGRESS
        viewModelScope.launch {
            _resetStatus.value = try {
                resetAllData()
                ResetStatus.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ResetStatus.FAILED
            }
        }
    }

    /** Call once the DONE or FAILED message has been shown. */
    fun onResetMessageShown() {
        if (_resetStatus.value == ResetStatus.DONE || _resetStatus.value == ResetStatus.FAILED) {
            _resetStatus.value = ResetStatus.IDLE
        }
    }
}
