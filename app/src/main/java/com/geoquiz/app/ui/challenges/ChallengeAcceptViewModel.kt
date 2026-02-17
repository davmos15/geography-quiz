package com.geoquiz.app.ui.challenges

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.db.ChallengeEntity
import com.geoquiz.app.data.repository.ChallengeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChallengeAcceptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val challengeRepository: ChallengeRepository
) : ViewModel() {

    val challengeId: String = savedStateHandle["challengeId"] ?: ""

    private val _challenge = MutableStateFlow<ChallengeEntity?>(null)
    val challenge: StateFlow<ChallengeEntity?> = _challenge.asStateFlow()

    init {
        viewModelScope.launch {
            _challenge.value = challengeRepository.getChallengeById(challengeId)
        }
    }
}
