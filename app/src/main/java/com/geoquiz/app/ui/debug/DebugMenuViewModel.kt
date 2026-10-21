package com.geoquiz.app.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val featureFlagRepository: FeatureFlagRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val flags: StateFlow<List<FeatureFlagState>> = featureFlagRepository.states
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onToggleFlag(flag: FeatureFlag, enabled: Boolean) {
        viewModelScope.launch {
            featureFlagRepository.setOverride(flag, enabled)
        }
    }

    fun onResetFlags() {
        viewModelScope.launch {
            featureFlagRepository.clearOverrides()
        }
    }

    /** Clears `whats_new_seen_version` (P10-F) so the one-off screen shows again on the next launch. */
    fun onShowWhatsNewAgain() {
        viewModelScope.launch {
            settingsRepository.setWhatsNewSeenVersion(SettingsRepository.WHATS_NEW_VERSION - 1)
        }
    }
}
