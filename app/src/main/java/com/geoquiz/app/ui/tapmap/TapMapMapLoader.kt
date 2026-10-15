package com.geoquiz.app.ui.tapmap

import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.map.frame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Loads [TapMapMapState] for a round: the coarse/detail/tap-zone layers and [preset]'s frame
 * around [members] (shared by [TapMapGameViewModel]'s Find round and
 * [TapMapNameAllViewModel]'s Name all round, task 5.2b). See [TapMapGameViewModel.loadMapLayers]'s
 * former doc for why World is never framed (a practice set excepted) and why the frame has a
 * floor on its size.
 */
internal suspend fun loadTapMapMapState(
    mapRepository: MapRepository,
    defaultDispatcher: CoroutineDispatcher,
    preset: RegionPreset,
    members: Set<String>,
    isPractice: Boolean
): TapMapMapState {
    val coarse = runCatching { mapRepository.layer(GeoLayerId.ADMIN0_110M) }.getOrNull()
    val detail = runCatching { mapRepository.layer(GeoLayerId.ADMIN0_50M) }.getOrNull()
    val tapZones = runCatching { mapRepository.layer(GeoLayerId.TAP_ZONES) }.getOrNull()
    val layer = detail ?: coarse
    val frame = if (layer == null) {
        null
    } else {
        withContext(defaultDispatcher) {
            if (preset == RegionPreset.WORLD && !isPractice) {
                null
            } else {
                preset.frame(layer, members)
                    ?.withMinimumFrameSize(PRACTICE_MIN_FRAME_WIDTH, PRACTICE_MIN_FRAME_HEIGHT)
            }
        }
    }
    return TapMapMapState(
        preset = preset,
        members = members,
        coarse = coarse,
        detail = detail,
        tapZones = tapZones,
        frame = frame,
        // Nit: an error instead of an endless spinner if every layer fails to load.
        loadFailed = coarse == null && detail == null
    )
}

/** [this] grown to at least [minWidth] by [minHeight], about its own centre, if it is smaller. */
internal fun PlaneRect.withMinimumFrameSize(minWidth: Float, minHeight: Float): PlaneRect {
    val extraWidth = (minWidth - width).coerceAtLeast(0f) / 2f
    val extraHeight = (minHeight - height).coerceAtLeast(0f) / 2f
    return if (extraWidth == 0f && extraHeight == 0f) {
        this
    } else {
        PlaneRect(minX - extraWidth, minY - extraHeight, maxX + extraWidth, maxY + extraHeight)
    }
}

/**
 * A floor on a round's map frame, in [com.geoquiz.app.domain.map.EqualEarthProjection] plane
 * units (world: x spans about 5.4, y about 2.6), so a practice set of one tiny country is not
 * zoomed in absurdly far (S7).
 */
private const val PRACTICE_MIN_FRAME_WIDTH = 0.6f
private const val PRACTICE_MIN_FRAME_HEIGHT = 0.3f
