package com.geoquiz.app.domain.model

/**
 * Switches for features that are still being built. Every new mode or feature from
 * docs/UPGRADE_PLAN.md ships behind one of these until it is approved for release.
 *
 * [key] is persisted in DataStore, so never rename it. [debugLabel] is only shown in the
 * debug menu, never to players.
 */
enum class FeatureFlag(
    val key: String,
    val debugLabel: String,
    val defaultEnabled: Boolean = false
) {
    COUNTRY_SILHOUETTES("mode_country_silhouettes", "Mode: Country silhouettes"),
    TAP_THE_MAP("mode_tap_the_map", "Mode: Tap the map"),
    BORDER_HOP("mode_border_hop", "Mode: Border hop"),
    US_CA_REGIONS("mode_us_ca_regions", "Mode: US states and Canadian provinces"),
    CURRENCIES("mode_currencies", "Mode: Currencies"),
    PHYSICAL_FEATURES("mode_physical_features", "Mode: Rivers, mountains and lakes"),
    FLAG_SPEED_ROUND("mode_flag_speed_round", "Mode: Flag speed round"),
    DAILY_CHALLENGE("daily_challenge", "Daily challenge and streaks")
}

data class FeatureFlagState(
    val flag: FeatureFlag,
    val enabled: Boolean,
    val overridden: Boolean
)
