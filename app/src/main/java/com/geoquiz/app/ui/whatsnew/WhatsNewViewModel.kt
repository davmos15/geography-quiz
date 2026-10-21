package com.geoquiz.app.ui.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the one-off "What's new" screen (P10-F) is on screen. */
sealed interface WhatsNewUiState {
    data object Hidden : WhatsNewUiState
    data class Shown(val sections: List<WhatsNewSection>) : WhatsNewUiState
}

/**
 * The one-off "What's new" screen shown on top of the Play hub the first time a player opens the
 * app after the 3.0.0 update (P10-F), never for a fresh install, and never again once dismissed.
 *
 * `whats_new_seen_version` ([SettingsRepository.whatsNewSeenVersion]) is a brand new setting, so
 * it reads `null` for every player on their first run after this update, old player or new
 * install alike. That first `null` is resolved by two upgrade signals, either of which is enough:
 * - [AppInstallInfo.wasUpdated] (`PackageManager`'s `firstInstallTime < lastUpdateTime`);
 * - any existing quiz history ([QuizHistoryRepository.totalQuizzesCompleted]), which also catches
 *   a returning player whose install timestamps were reset by something like "Clear data" or a
 *   backup/restore, where [AppInstallInfo] alone would wrongly look like a fresh install.
 *
 * A genuine fresh install (neither signal true) silently records the current version and is
 * never shown the screen. Once the key is set (shown, dismissed, or silently recorded), a later
 * release only needs the plain version comparison: [SettingsRepository.WHATS_NEW_VERSION] bumps
 * with new content, and anyone below it sees the new screen again.
 *
 * Accepted edge case (code review round, task P10-R1, flagged for the lead): a player who installs
 * 3.0.0 and the Play Store auto-updates it again before their very first launch has
 * `firstInstallTime < lastUpdateTime` just like a genuine upgrade, so [AppInstallInfo.wasUpdated]
 * is true and they see this screen even though they never actually ran the earlier version. Rare
 * (auto-update finishing before the player ever opens a brand new install) and harmless (an
 * unnecessary "what's new" is not a bug the size of a false fresh-install would be), so left as is.
 */
@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val quizHistoryRepository: QuizHistoryRepository,
    private val installInfo: AppInstallInfo
) : ViewModel() {

    private val _uiState = MutableStateFlow<WhatsNewUiState>(WhatsNewUiState.Hidden)
    val uiState: StateFlow<WhatsNewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = settingsRepository.whatsNewSeenVersion.first()
            val shouldShow = if (stored != null) {
                stored < SettingsRepository.WHATS_NEW_VERSION
            } else {
                val isUpgrade = installInfo.wasUpdated || quizHistoryRepository.totalQuizzesCompleted.first() > 0
                if (!isUpgrade) {
                    // Fresh install: never shown, but recorded so a later version bump doesn't
                    // retroactively show this version's content to a player who never had it.
                    settingsRepository.setWhatsNewSeenVersion(SettingsRepository.WHATS_NEW_VERSION)
                }
                isUpgrade
            }
            _uiState.value = if (shouldShow) {
                val enabled = featureFlagRepository.states.first()
                    .filter { it.enabled }
                    .map { it.flag }
                    .toSet()
                WhatsNewUiState.Shown(whatsNewSections(enabled))
            } else {
                WhatsNewUiState.Hidden
            }
        }
    }

    /**
     * "Got it", the close icon or the system back action: dismiss and never show it again.
     * [WhatsNewUiState.Hidden] is set first, before the [SettingsRepository] write (code review
     * round, task P10-R1), so a double tap (or back then a tap, in whatever order) can only ever
     * run the write once - `uiState`'s immediate flip to [WhatsNewUiState.Hidden] makes the
     * dialog disappear on the first call, leaving nothing for a second call's `Shown` check to
     * dismiss again - rather than both calls racing to launch their own write.
     */
    fun onDismiss() {
        if (_uiState.value !is WhatsNewUiState.Shown) return
        _uiState.value = WhatsNewUiState.Hidden
        viewModelScope.launch {
            settingsRepository.setWhatsNewSeenVersion(SettingsRepository.WHATS_NEW_VERSION)
        }
    }
}
