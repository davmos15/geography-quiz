package com.geoquiz.app.ui.whatsnew

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.ui.mode.imageVector

/** One row of the "What's new" screen (P10-F): an icon, a title and an optional one-line subtitle. */
data class WhatsNewItem(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int? = null,
    val icon: ImageVector
)

/** A heading plus its rows. */
data class WhatsNewSection(
    @StringRes val headingRes: Int,
    val items: List<WhatsNewItem>
)

/** A "New ways to play" row, shown only while [flag] is enabled. */
private data class FlaggedItem(
    val flag: FeatureFlag,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector
)

/**
 * Every mode this screen can mention, in display order, each tied to the [FeatureFlag] that must
 * be enabled for it to show (same flags as [com.geoquiz.app.domain.mode.GameModeRegistry.available]).
 */
private val NEW_MODE_ITEMS = listOf(
    FlaggedItem(
        FeatureFlag.COUNTRY_SILHOUETTES,
        R.string.whats_new_mode_silhouettes_title,
        R.string.whats_new_mode_silhouettes_subtitle,
        ModeIcon.SILHOUETTE.imageVector
    ),
    FlaggedItem(
        FeatureFlag.TAP_THE_MAP,
        R.string.whats_new_mode_tap_map_title,
        R.string.whats_new_mode_tap_map_subtitle,
        ModeIcon.MAP_PIN.imageVector
    ),
    FlaggedItem(
        FeatureFlag.BORDER_HOP,
        R.string.whats_new_mode_border_hop_title,
        R.string.whats_new_mode_border_hop_subtitle,
        ModeIcon.BORDER_HOP.imageVector
    ),
    FlaggedItem(
        FeatureFlag.US_CA_REGIONS,
        R.string.whats_new_mode_us_ca_title,
        R.string.whats_new_mode_us_ca_subtitle,
        ModeIcon.MAP_PIN.imageVector
    ),
    FlaggedItem(
        FeatureFlag.CURRENCIES,
        R.string.whats_new_mode_currencies_title,
        R.string.whats_new_mode_currencies_subtitle,
        ModeIcon.CURRENCY.imageVector
    ),
    FlaggedItem(
        FeatureFlag.PHYSICAL_FEATURES,
        R.string.whats_new_mode_physical_features_title,
        R.string.whats_new_mode_physical_features_subtitle,
        ModeIcon.TERRAIN_FEATURE.imageVector
    ),
    FlaggedItem(
        FeatureFlag.FLAG_SPEED_ROUND,
        R.string.whats_new_mode_flag_speed_title,
        R.string.whats_new_mode_flag_speed_subtitle,
        ModeIcon.FLAG.imageVector
    ),
    FlaggedItem(
        FeatureFlag.DAILY_CHALLENGE,
        R.string.whats_new_mode_daily_challenge_title,
        R.string.whats_new_mode_daily_challenge_subtitle,
        Icons.Default.LocalFireDepartment
    )
)

/** "New ways to play": only the modes whose feature flag is currently enabled. */
fun newWaysToPlaySection(enabledFlags: Set<FeatureFlag>): WhatsNewSection =
    WhatsNewSection(
        headingRes = R.string.whats_new_section_new_modes,
        items = NEW_MODE_ITEMS
            .filter { it.flag in enabledFlags }
            .map { WhatsNewItem(it.titleRes, it.subtitleRes, it.icon) }
    )

/** "Changes": always shown, not tied to any feature flag. */
fun changesSection(): WhatsNewSection =
    WhatsNewSection(
        headingRes = R.string.whats_new_section_changes,
        items = listOf(
            WhatsNewItem(
                R.string.whats_new_change_difficulty_title,
                R.string.whats_new_change_difficulty_subtitle,
                Icons.Default.Tune
            ),
            WhatsNewItem(
                R.string.whats_new_change_more_ways_title,
                R.string.whats_new_change_more_ways_subtitle,
                Icons.Default.Apps
            ),
            WhatsNewItem(
                R.string.whats_new_change_surprise_me_title,
                R.string.whats_new_change_surprise_me_subtitle,
                Icons.Default.Shuffle
            ),
            WhatsNewItem(
                R.string.whats_new_change_colour_title,
                R.string.whats_new_change_colour_subtitle,
                Icons.Default.Palette
            ),
            WhatsNewItem(
                R.string.whats_new_change_share_title,
                R.string.whats_new_change_share_subtitle,
                Icons.Default.Share
            ),
            WhatsNewItem(
                R.string.whats_new_change_sound_title,
                R.string.whats_new_change_sound_subtitle,
                Icons.Default.VolumeUp
            )
        )
    )

/**
 * The whole screen's content (P10-F): "New ways to play" (filtered to [enabledFlags]) followed by
 * "Changes". A section with no items (every one of its modes' flags off) is dropped rather than
 * shown with an empty heading.
 */
fun whatsNewSections(enabledFlags: Set<FeatureFlag>): List<WhatsNewSection> =
    listOf(newWaysToPlaySection(enabledFlags), changesSection()).filter { it.items.isNotEmpty() }
