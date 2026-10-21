package com.geoquiz.app.domain.model

/**
 * Switches for new modes and features from docs/UPGRADE_PLAN.md. For the 3.0.0 release
 * candidate every flag defaults to on so the whole build can be tested (D63); set a flag's
 * [defaultEnabled] back to `false` to hold that feature back before shipping. Debug builds can
 * still override any flag from the debug menu.
 *
 * [key] is persisted in DataStore, so never rename it. [debugLabel] is only shown in the
 * debug menu, never to players.
 */
enum class FeatureFlag(
    val key: String,
    val debugLabel: String,
    val defaultEnabled: Boolean = false
) {
    COUNTRY_SILHOUETTES("mode_country_silhouettes", "Mode: Country silhouettes", defaultEnabled = true),
    TAP_THE_MAP("mode_tap_the_map", "Mode: Tap the map", defaultEnabled = true),
    BORDER_HOP("mode_border_hop", "Mode: Border hop", defaultEnabled = true),
    US_CA_REGIONS("mode_us_ca_regions", "Mode: States and territories", defaultEnabled = true),
    CURRENCIES("mode_currencies", "Mode: Currencies", defaultEnabled = true),
    PHYSICAL_FEATURES("mode_physical_features", "Mode: Rivers, mountains and lakes", defaultEnabled = true),
    FLAG_SPEED_ROUND("mode_flag_speed_round", "Mode: Flag speed round", defaultEnabled = true),
    DAILY_CHALLENGE("daily_challenge", "Daily challenge and streaks", defaultEnabled = true)
}

data class FeatureFlagState(
    val flag: FeatureFlag,
    val enabled: Boolean,
    val overridden: Boolean
)
