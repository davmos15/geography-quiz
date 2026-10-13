package com.geoquiz.app.ui.silhouettes

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
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.navigation.ModeRoutes

/**
 * Stub for task 5.1's category/difficulty picker (`SilhouettesGameMode.MODE_ID`). Registered at
 * [com.geoquiz.app.ui.navigation.Screen.ModeSetup] so Play's "New modes" grid, navigation and
 * results plumbing work end to end before the real screen exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilhouettesSetupScreen(onStart: (route: String) -> Unit, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_silhouettes_name),
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
                stringResource(R.string.mode_silhouettes_placeholder_coming_soon),
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = {
                    onStart(
                        ModeRoutes.start(
                            modeId = SilhouettesGameMode.MODE_ID,
                            categoryType = QuizCategory.AllCountries.typeKey,
                            categoryValue = QuizCategory.AllCountries.valueKey
                        )
                    )
                }
            ) { Text(stringResource(R.string.mode_silhouettes_placeholder_start_all_countries)) }
        }
    }
}

/**
 * Stub for task 5.1's game screen, registered at
 * [com.geoquiz.app.ui.navigation.Screen.Silhouettes]. Finishing navigates to Results exactly like
 * the classic quiz, popping this screen off the back stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilhouettesGameScreen(onFinished: (resultId: String) -> Unit, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.mode_silhouettes_name),
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
                stringResource(R.string.mode_silhouettes_placeholder_coming_soon),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
