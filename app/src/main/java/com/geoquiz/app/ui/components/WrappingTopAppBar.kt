package com.geoquiz.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Lines a top-bar title may wrap to before it is shortened with an ellipsis. */
const val TOP_BAR_TITLE_MAX_LINES = 2

/**
 * A small top app bar whose title wraps (3.6). Material's TopAppBar has a fixed 64 dp height, so
 * a long title at large text sizes is cut off; this bar is at least 64 dp tall and grows with
 * its title, up to [TOP_BAR_TITLE_MAX_LINES] lines and then an ellipsis. The title is a heading
 * and TalkBack always reads it in full. Same colours and insets as the Material bar.
 *
 * [titleModifier] goes on the title text (e.g. the hidden debug-menu taps on Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrappingTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = TOP_BAR_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .then(titleModifier)
                    .semantics { heading() }
            )
            // Actions use onSurfaceVariant, as in Material's TopAppBar (the navigation icon and
            // title stay onSurface).
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}
