package com.geoquiz.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * The bottom navigation bar. Each item is read by TalkBack with its name and selected state:
 * from the visible label normally, or from the icon's description when large text leaves room
 * for icons only.
 */
@Composable
internal fun AppBottomBar(
    isSelected: (route: String) -> Boolean,
    onSelect: (route: String) -> Unit
) {
    val iconOnly = LocalDensity.current.fontScale >= ICON_ONLY_FONT_SCALE
    NavigationBar {
        bottomNavItems.forEach { item ->
            val name = stringResource(item.label)
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
