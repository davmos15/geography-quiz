package com.geoquiz.app.domain.map

/**
 * How the map shows one feature. Modes pass a `Map<featureId, MapFeatureState>` to the map;
 * features not in the map are [Default].
 *
 * Every state other than [Default] differs by more than colour (see `MapCanvas`):
 * - [Found]: medium solid outline and a tick marker (blue, like a correct answer).
 * - [Wrong]: cross-hatch, medium solid outline and a cross marker (orange).
 * - [Highlighted]: thick solid outline, no marker.
 * - [Start]: thick solid outline and a filled-dot marker.
 * - [End]: thick dashed outline and a ring marker.
 *
 * Markers sit on the label point at a fixed on-screen size and only on features at least
 * 12 dp across on screen; smaller ones rely on the tap zones (task 4.3).
 */
enum class MapFeatureState {
    Default,
    /** Named or found by the player (blue family). */
    Found,
    /** The feature a question points at. */
    Highlighted,
    /** Route start. */
    Start,
    /** Route end. */
    End,
    /** A wrong tap or a missed feature (orange family). */
    Wrong,
}
