package com.geoquiz.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R

internal data class BottomNavItem(
    @StringRes val label: Int,
    val icon: ImageVector,
    val route: String
)

/** The four top-level destinations; the bar is shown only on these routes. */
internal val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_play, Icons.Default.PlayArrow, Screen.Play.route),
    BottomNavItem(R.string.nav_stats, Icons.Default.BarChart, Screen.Stats.route),
    BottomNavItem(R.string.nav_achievements, Icons.Default.EmojiEvents, Screen.Achievements.route),
    BottomNavItem(R.string.nav_settings, Icons.Default.Settings, Screen.Settings.route)
)

internal val topLevelRoutes: Set<String> = bottomNavItems.map { it.route }.toSet()

/** From this text scale the bar's fixed height can't fit the labels, so it shows icons only. */
private const val ICON_ONLY_FONT_SCALE = 1.5f

/** Material's gap between bar items. */
private val ITEM_GAP = 8.dp

/** Slack kept around each label so it never touches the edge of its item. */
private val LABEL_SLACK = 4.dp

/**
 * The bottom navigation bar. Each item is read by TalkBack with its name and selected state:
 * from the visible label normally, or from the icon's description when the labels don't fit.
 * Labels are dropped for every item at once: from [ICON_ONLY_FONT_SCALE], or earlier when the
 * longest label would not fit its item on one line (narrow phones with slightly larger text),
 * so no label is ever cut short.
 *
 * The fit check uses BoxWithConstraints, which can't be measured intrinsically: keep the bar out
 * of layouts that ask for intrinsic sizes (Scaffold's bottomBar slot does not).
 */
@Composable
internal fun AppBottomBar(
    isSelected: (route: String) -> Boolean,
    onSelect: (route: String) -> Unit
) {
    val density = LocalDensity.current
    val names = bottomNavItems.map { stringResource(it.label) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemWidth = (maxWidth - ITEM_GAP * (bottomNavItems.size - 1)) / bottomNavItems.size
        val labelsFit = names.all { name ->
            val width = measurer.measure(name, labelStyle, maxLines = 1, softWrap = false).size.width
            with(density) { width.toDp() } + LABEL_SLACK <= itemWidth
        }
        val iconOnly = density.fontScale >= ICON_ONLY_FONT_SCALE || !labelsFit
        NavigationBar {
            bottomNavItems.forEachIndexed { index, item ->
                val name = names[index]
                val selected = isSelected(item.route)
                NavigationBarItem(
                    icon = { Icon(item.icon, contentDescription = if (iconOnly) name else null) },
                    label = if (iconOnly) null else {
                        { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    alwaysShowLabel = !iconOnly,
                    selected = selected,
                    onClick = { if (!selected) onSelect(item.route) }
                )
            }
        }
    }
}
