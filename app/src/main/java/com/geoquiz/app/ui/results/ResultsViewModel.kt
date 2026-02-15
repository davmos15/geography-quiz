package com.geoquiz.app.ui.results

import androidx.lifecycle.ViewModel
import com.geoquiz.app.data.local.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val playerName = settingsRepository.playerName.map { it.ifBlank { "A friend" } }
}
