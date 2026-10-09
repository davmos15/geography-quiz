package com.geoquiz.app.ui.settings

import com.geoquiz.app.ui.components.WrappingTopAppBar
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.ui.components.DifficultySelector

private const val DEBUG_MENU_TAPS = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onOpenCredits: () -> Unit = {},
    onOpenDebugMenu: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val showTimer by viewModel.showTimer.collectAsStateWithLifecycle()
    val showFlags by viewModel.showFlags.collectAsStateWithLifecycle()
    val vibration by viewModel.vibration.collectAsStateWithLifecycle()
    val showCountryHint by viewModel.showCountryHint.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val adsRemoved by viewModel.adsRemoved.collectAsStateWithLifecycle()
    val removeAdsPrice by viewModel.removeAdsPrice.collectAsStateWithLifecycle()
    val privacyOptionsRequired by viewModel.privacyOptionsRequired.collectAsStateWithLifecycle()
    val resetStatus by viewModel.resetStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val uriHandler = LocalUriHandler.current
    var debugTapCount by remember { mutableIntStateOf(0) }
    val debugMenuOpenedMessage = stringResource(R.string.debug_menu_opened)
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resetDoneMessage = stringResource(R.string.reset_done)
    val resetFailedMessage = stringResource(R.string.reset_failed)
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    val openInBrowserLabel = stringResource(R.string.action_open_in_browser)

    LaunchedEffect(resetStatus) {
        val message = when (resetStatus) {
            ResetStatus.DONE -> resetDoneMessage
            ResetStatus.FAILED -> resetFailedMessage
            else -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onResetMessageShown()
        }
    }

    if (showResetDialog) {
        ResetAllDataDialog(
            onConfirm = {
                showResetDialog = false
                viewModel.onResetAllData()
            },
            onDismiss = { showResetDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Debug builds only: tap the title 7 times to open the hidden debug menu
            val titleModifier = if (onOpenDebugMenu != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    debugTapCount++
                    if (debugTapCount >= DEBUG_MENU_TAPS) {
                        debugTapCount = 0
                        Toast.makeText(context, debugMenuOpenedMessage, Toast.LENGTH_SHORT).show()
                        onOpenDebugMenu()
                    }
                }
            } else {
                Modifier
            }
            WrappingTopAppBar(
                title = stringResource(R.string.nav_settings),
                titleModifier = titleModifier,
                // No back arrow when shown as a bottom navigation tab
                navigationIcon = onNavigateBack?.let { back ->
                    @Composable {
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Timer",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Display count-up timer during quizzes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = showTimer,
                    onCheckedChange = { viewModel.onToggleTimer(it) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Flags",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Show flags next to countries in quizzes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = showFlags,
                    onCheckedChange = { viewModel.onToggleShowFlags(it) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_vibration),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.setting_vibration_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = vibration,
                    onCheckedChange = { viewModel.onToggleVibration(it) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Country Hint in Capitals",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Show the country name as a clue in capitals quizzes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = showCountryHint,
                    onCheckedChange = { viewModel.onToggleShowCountryHint(it) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_default_difficulty),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_default_difficulty_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                DifficultySelector(
                    selected = difficulty,
                    onSelect = viewModel::onDifficultySelected
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            if (adsRemoved) {
                Text(
                    text = "Ads removed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Button(
                    onClick = { activity?.let { viewModel.purchaseRemoveAds(it) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Ads${removeAdsPrice?.let { " - $it" } ?: ""}")
                }
            }

            TextButton(
                onClick = { viewModel.restorePurchases() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore Purchases")
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_data))
            SettingsActionRow(
                title = stringResource(R.string.settings_reset_all_data),
                summary = stringResource(R.string.settings_reset_all_data_summary),
                icon = Icons.Default.DeleteForever,
                destructive = true,
                enabled = resetStatus != ResetStatus.IN_PROGRESS,
                onClick = { showResetDialog = true }
            )

            HorizontalDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_about))
            SettingsActionRow(
                title = stringResource(R.string.settings_credits),
                summary = stringResource(R.string.settings_credits_summary),
                icon = Icons.Default.Info,
                onClick = onOpenCredits
            )
            SettingsActionRow(
                title = stringResource(R.string.settings_privacy_policy),
                summary = stringResource(R.string.settings_privacy_policy_summary),
                icon = Icons.Default.Policy,
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onClickLabel = openInBrowserLabel,
                onClick = {
                    // No browser installed: nothing sensible to do, so ignore the tap.
                    runCatching { uriHandler.openUri(privacyPolicyUrl) }
                }
            )
            if (privacyOptionsRequired && activity != null) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_privacy_options),
                    summary = stringResource(R.string.settings_privacy_options_summary),
                    icon = Icons.Default.PrivacyTip,
                    onClick = { viewModel.showPrivacyOptions(activity) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

/** A full-width tappable row (at least 56 dp tall) with an icon, a title and an optional summary. */
@Composable
private fun SettingsActionRow(
    title: String,
    summary: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    onClickLabel: String? = null
) {
    val titleColour = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColour = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Decorative: the title text already describes the action.
        Icon(icon, contentDescription = null, tint = iconColour, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColour)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ResetAllDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
        title = { Text(stringResource(R.string.reset_dialog_title)) },
        text = {
            // Scrollable so the whole explanation stays readable at large font scales.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.reset_dialog_body))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.reset_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** Walks the context wrappers to find the hosting Activity (the UMP privacy options form needs it). */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
