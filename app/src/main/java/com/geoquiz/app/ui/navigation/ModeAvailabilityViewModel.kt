package com.geoquiz.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.domain.mode.GameModeRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Which registered mode ids (classic or new) are currently reachable, by the same rule as Play's
 * "New modes" grid ([GameModeRegistry.available]): released modes plus those whose
 * [com.geoquiz.app.domain.model.FeatureFlag] is on (debug overrides only).
 *
 * Used by [AppNavigation] to pop a new mode's own screens (`ModeSetup`, `Silhouettes`, `TapMap`)
 * back to Play if its flag is off and not overridden - for example, a bookmarked deep link, or a
 * flag flipped from the debug menu while the player is on the screen (task 5.0 review).
 */
@HiltViewModel
class ModeAvailabilityViewModel @Inject constructor(
    registry: GameModeRegistry,
    featureFlagRepository: FeatureFlagRepository
) : ViewModel() {

    /** Null while the flags are still loading, so nothing is popped before they are known. */
    val availableModeIds: StateFlow<Set<String>?> = featureFlagRepository.states
        .map { states ->
            val enabled = states.filter { it.enabled }.map { it.flag }.toSet()
            registry.available { it in enabled }.map { it.id }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
