package com.geoquiz.app.ui.whatsnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.ui.components.WrappingTopAppBar
import com.geoquiz.app.ui.components.readableWidth

/**
 * Hosts the one-off "What's new" screen (P10-F) on top of whatever is currently shown (the Play
 * hub on first launch): nothing while [WhatsNewViewModel]'s state is [WhatsNewUiState.Hidden], or
 * while [visible] is false (task P10-R1: `AppNavigation` passes `false` whenever the current
 * destination is not Play, e.g. a cold start over a challenge deep link - [viewModel] still runs
 * and may resolve to [WhatsNewUiState.Shown] either way, so it is shown for real the next time
 * [visible] is true and the player has not dismissed it in between).
 */
@Composable
fun WhatsNewHost(visible: Boolean = true, viewModel: WhatsNewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!visible) return
    val shown = state as? WhatsNewUiState.Shown ?: return
    WhatsNewDialog(sections = shown.sections, onDismiss = viewModel::onDismiss)
}

/**
 * The full-screen dialog itself: a scrollable, stacked list of what's new with an icon per row
 * (no tile grids, L9), dismissible by the close icon, the pinned "Got it" button or the system
 * back action (the dialog's default `dismissOnBackPress`).
 */
@Composable
fun WhatsNewDialog(sections: List<WhatsNewSection>, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    WrappingTopAppBar(
                        title = stringResource(R.string.whats_new_title),
                        actions = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    // The dialog window spans the whole screen, so keep "Got it" clear of the
                    // gesture/navigation bar (it sat under it on a 3.0.0 RC device test).
                    Surface(modifier = Modifier.navigationBarsPadding()) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.whats_new_got_it))
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .readableWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    sections.forEach { section ->
                        item {
                            Text(
                                text = stringResource(section.headingRes),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .padding(top = 16.dp, bottom = 4.dp)
                                    .semantics { heading() }
                            )
                        }
                        items(section.items) { item -> WhatsNewRow(item) }
                    }
                    item { Spacer(modifier = Modifier.heightIn(min = 16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WhatsNewRow(item: WhatsNewItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            // Decorative: the title next to it already names the mode or change.
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(item.titleRes),
                style = MaterialTheme.typography.bodyLarge
            )
            item.subtitleRes?.let { subtitleRes ->
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
