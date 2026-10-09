package com.geoquiz.app.ui.credits

import com.geoquiz.app.ui.components.WrappingTopAppBar
import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.geoquiz.app.ui.components.readableWidth

/** Full flag-icons licence text, bundled next to the SVGs. */
private const val FLAG_LICENCE_ASSET = "flags/LICENSE"

/**
 * "Settings → Credits and licences" (L1): attribution for every bundled data source and asset,
 * plus the entry point to the open-source licences list (L2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(
    onNavigateBack: () -> Unit,
    onOpenSourceLicences: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val openInBrowserLabel = stringResource(R.string.action_open_in_browser)
    var showFlagLicence by rememberSaveable { mutableStateOf(false) }
    val openUrl: (String) -> Unit = { url ->
        // No browser installed: nothing sensible to do, so ignore the tap.
        runCatching { uriHandler.openUri(url) }
    }

    if (showFlagLicence) {
        FlagLicenceDialog(onDismiss = { showFlagLicence = false })
    }

    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.credits_title),
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .readableWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            CreditsSection(
                heading = stringResource(R.string.credits_country_data_heading),
                body = stringResource(R.string.credits_country_data_body)
            ) {
                val mledozeUrl = stringResource(R.string.credits_url_mledoze)
                val odblUrl = stringResource(R.string.credits_url_odbl)
                LinkRow(
                    label = stringResource(R.string.credits_link_mledoze),
                    onClickLabel = openInBrowserLabel,
                    onClick = { openUrl(mledozeUrl) }
                )
                LinkRow(
                    label = stringResource(R.string.credits_link_odbl),
                    onClickLabel = openInBrowserLabel,
                    onClick = { openUrl(odblUrl) }
                )
            }

            CreditsSection(
                heading = stringResource(R.string.credits_aliases_heading),
                body = stringResource(R.string.credits_aliases_body)
            ) {
                val aliasesUrl = stringResource(R.string.credits_url_aliases)
                val odblUrl = stringResource(R.string.credits_url_odbl)
                LinkRow(
                    label = stringResource(R.string.credits_link_aliases),
                    onClickLabel = openInBrowserLabel,
                    onClick = { openUrl(aliasesUrl) }
                )
                LinkRow(
                    label = stringResource(R.string.credits_link_odbl),
                    onClickLabel = openInBrowserLabel,
                    onClick = { openUrl(odblUrl) }
                )
            }

            CreditsSection(
                heading = stringResource(R.string.credits_flags_heading),
                body = stringResource(R.string.credits_flags_body)
            ) {
                val flagIconsUrl = stringResource(R.string.credits_url_flag_icons)
                LinkRow(
                    label = stringResource(R.string.credits_link_flag_icons),
                    onClickLabel = openInBrowserLabel,
                    onClick = { openUrl(flagIconsUrl) }
                )
                LinkRow(
                    label = stringResource(R.string.credits_view_flag_licence),
                    icon = Icons.Default.Description,
                    onClick = { showFlagLicence = true }
                )
            }

            // Maps and regions: no Natural Earth or UN M49 data is bundled yet. Add a section here
            // (with its attribution) in the phase that first ships that data.

            CreditsSection(
                heading = stringResource(R.string.credits_fonts_heading),
                body = stringResource(R.string.credits_fonts_body)
            )

            CreditsSection(
                heading = stringResource(R.string.credits_artwork_heading),
                body = stringResource(R.string.credits_artwork_body)
            )

            CreditsSection(
                heading = stringResource(R.string.credits_open_source_heading),
                body = stringResource(R.string.credits_open_source_licences_summary),
                showDivider = false
            ) {
                LinkRow(
                    label = stringResource(R.string.credits_open_source_licences),
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = onOpenSourceLicences
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CreditsSection(
    heading: String,
    body: String,
    showDivider: Boolean = true,
    links: @Composable () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        links()
    }
    if (showDivider) HorizontalDivider()
}

/**
 * A tappable row at least 48 dp tall, announced by TalkBack as a button with the label text.
 * The trailing icon is decorative; [onClickLabel] tells TalkBack what a double tap does.
 */
@Composable
private fun LinkRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Filled.OpenInNew,
    onClickLabel: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FlagLicenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val licenceText by produceState<String?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { context.readAssetText(FLAG_LICENCE_ASSET) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.credits_flag_licence_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = licenceText ?: stringResource(R.string.credits_flag_licence_loading),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

private fun Context.readAssetText(path: String): String =
    assets.open(path).bufferedReader().use { it.readText() }
