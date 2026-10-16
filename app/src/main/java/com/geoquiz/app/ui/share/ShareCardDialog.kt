package com.geoquiz.app.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.ui.components.AdaptiveButtonRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ShareCardStrings] from `strings_share.xml`, for [ShareCardContent.from]. */
@Composable
fun shareCardStrings(): ShareCardStrings {
    val title = stringResource(R.string.share_card_title_format)
    val daily = stringResource(R.string.share_card_daily_title_format)
    val time = stringResource(R.string.share_card_time_format)
    val percentage = stringResource(R.string.share_card_percentage_format)
    val meta = stringResource(R.string.share_card_meta_format)
    return remember(title, daily, time, percentage, meta) {
        ShareCardStrings(title, daily, time, percentage, meta)
    }
}

/**
 * Previews the share card (8.3) from Results' Share button: the same card either shared as a PNG
 * or described as plain text, so the player sees what they're about to send. Rendering ([render])
 * runs off the main thread; the dialog shows a progress indicator until it's ready. [onShareImage]
 * gets the already-rendered bitmap (writing it to a file and starting the share intent is for the
 * caller, which holds the [android.content.Context] and the surrounding coroutine scope).
 */
@Composable
fun ShareCardDialog(
    content: ShareCardContent,
    /** For the preview's content description, e.g. "Share card: 8 of 10, 16 Oct 2026, 1 minute 42 seconds". */
    correct: Int,
    total: Int,
    onDismiss: () -> Unit,
    onShareText: () -> Unit,
    onShareImage: (Bitmap) -> Unit
) {
    val appName = stringResource(R.string.app_name)
    val colours = MaterialTheme.colorScheme
    val palette = remember(colours) {
        ShareCardPalette(
            background = colours.background.toArgb(),
            onBackground = colours.onBackground.toArgb(),
            onSurfaceVariant = colours.onSurfaceVariant.toArgb(),
            primary = colours.primary.toArgb()
        )
    }
    var bitmap by remember(content, palette) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(content, palette, appName) {
        bitmap = withContext(Dispatchers.Default) {
            ShareCardRenderer.render(content, palette, appName)
        }
    }

    val spokenTime = pluralStringResource(
        R.plurals.share_card_spoken_minutes, content.timeSeconds / 60, content.timeSeconds / 60
    ) + " " + pluralStringResource(
        R.plurals.share_card_spoken_seconds, content.timeSeconds % 60, content.timeSeconds % 60
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_dialog_title)) },
        text = {
            Column {
                val current = bitmap
                if (current == null) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text(stringResource(R.string.share_dialog_preparing))
                } else {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.share_card_content_description,
                            correct, total, content.date, spokenTime
                        ),
                        // Square, but only as tall as the space left once the buttons below are
                        // measured: in landscape or at 200% font the preview shrinks, the
                        // buttons never get pushed out of the dialog.
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                AdaptiveButtonRow(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { bitmap?.let(onShareImage) },
                        // Nothing to share until the preview has rendered.
                        enabled = bitmap != null
                    ) {
                        Text(stringResource(R.string.share_dialog_share_image))
                    }
                    OutlinedButton(onClick = onShareText) {
                        Text(stringResource(R.string.share_dialog_share_text))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
