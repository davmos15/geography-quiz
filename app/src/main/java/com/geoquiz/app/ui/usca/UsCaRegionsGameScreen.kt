package com.geoquiz.app.ui.usca

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode

/**
 * US states and Canadian provinces' game screen entry point (task 6.2). Dispatches on the
 * route's `variant` (read via [UsCaRegionsVariantViewModel]) to [UsCaTapScreen],
 * [UsCaNameAllScreen] or [UsCaChoiceScreen].
 */
@Composable
fun UsCaRegionsGameScreen(
    onFinished: (resultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    variantViewModel: UsCaRegionsVariantViewModel = hiltViewModel()
) {
    when (variantViewModel.variant) {
        UsCaRegionsGameMode.VARIANT_NAME_ALL -> UsCaNameAllScreen(onFinished = onFinished, onNavigateBack = onNavigateBack)
        UsCaRegionsGameMode.VARIANT_CHOICE -> UsCaChoiceScreen(onFinished = onFinished, onNavigateBack = onNavigateBack)
        else -> UsCaTapScreen(onFinished = onFinished, onNavigateBack = onNavigateBack)
    }
}
