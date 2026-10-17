package com.geoquiz.app.ui.usca

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R

/**
 * Shown instead of a round when there are no regions to ask about (the admin-1 layer failed to
 * load, or a practice set no longer resolves). Nothing is recorded (Phase 9 review), like Border
 * hop's Unavailable state.
 */
@Composable
internal fun UsCaUnavailable(padding: PaddingValues, onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.us_ca_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { heading() }
            )
            Button(onClick = onNavigateBack) { Text(stringResource(R.string.action_back)) }
        }
    }
}
