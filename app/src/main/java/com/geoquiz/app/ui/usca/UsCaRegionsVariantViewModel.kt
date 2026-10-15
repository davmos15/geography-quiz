package com.geoquiz.app.ui.usca

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Reads the route's `variant` before either real screen's own ViewModel is created (task 6.2),
 * exactly like `ui/tapmap/TapMapVariantViewModel.kt`. An unrecognised or missing value falls
 * back to [UsCaRegionsGameMode.VARIANT_TAP].
 */
@HiltViewModel
class UsCaRegionsVariantViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    val variant: String = when (savedStateHandle.get<String>(UsCaRegionsRouteArgs.ARG_VARIANT)) {
        UsCaRegionsGameMode.VARIANT_NAME_ALL -> UsCaRegionsGameMode.VARIANT_NAME_ALL
        UsCaRegionsGameMode.VARIANT_CHOICE -> UsCaRegionsGameMode.VARIANT_CHOICE
        else -> UsCaRegionsGameMode.VARIANT_TAP
    }
}
