package com.geoquiz.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val showTimer: StateFlow<Boolean> = settingsRepository.showTimer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun onToggleTimer(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTimer(show)
        }
    }
}
