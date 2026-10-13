package com.geoquiz.app.ui.tapmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.navigation.ModeRoutes

/**
 * Stub for task 5.2's region/variant picker (`TapMapGameMode.MODE_ID`). Registered at
 * [com.geoquiz.app.ui.navigation.Screen.ModeSetup] so Play's "New modes" grid, navigation and
 * results plumbing work end to end before the real screen exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapMapSetupScreen(onStart: (route: String) -> Unit, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_tap_map_name),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.mode_tap_map_placeholder_coming_soon),
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = {
                    val world = QuizCategory.MapRegion(RegionPreset.WORLD.id)
                    onStart(
                        ModeRoutes.start(
                            modeId = TapMapGameMode.MODE_ID,
                            categoryType = world.typeKey,
                            categoryValue = world.valueKey,
                            variantId = ModeRoutes.DEFAULT_TAP_MAP_VARIANT
                        )
                    )
                }
            ) { Text(stringResource(R.string.mode_tap_map_placeholder_start_world)) }
        }
    }
}

/**
 * Stub for task 5.2's game screen, registered at
 * [com.geoquiz.app.ui.navigation.Screen.TapMap]. Finishing navigates to Results exactly like the
 * classic quiz, popping this screen off the back stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapMapGameScreen(onFinished: (resultId: String) -> Unit, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_tap_map_name),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.mode_tap_map_placeholder_coming_soon),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
