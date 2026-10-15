package com.geoquiz.app.ui.tapmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Reads the route's `variant` argument before [TapMapGameScreen] decides which real view model
 * (and content) to show (task 5.2b): [TapMapGameViewModel] for `find`, [TapMapNameAllViewModel]
 * for `name_all`. Bound to the same `NavBackStackEntry` as those (via `hiltViewModel()`), so this
 * is cheap: it does nothing but read one saved-state argument.
 */
@HiltViewModel
class TapMapVariantViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    val variant: String = savedStateHandle[TapMapGameViewModel.ARG_VARIANT] ?: TapMapGameMode.VARIANT_FIND
}
