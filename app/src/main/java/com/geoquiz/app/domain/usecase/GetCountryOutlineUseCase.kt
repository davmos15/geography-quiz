package com.geoquiz.app.domain.usecase

import com.geoquiz.app.di.DefaultDispatcher
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.OutlineGeometry
import com.geoquiz.app.domain.map.OutlineModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Builds one country's silhouette (task 4.5, used by Country silhouettes in Phase 5) from the
 * bundled `admin0_50m` layer. Read only: [MapRepository] decodes and caches the layer once for
 * the life of the process, so repeated calls (e.g. stepping through a quiz) are cheap after the
 * first. [OutlineGeometry.build] itself (walking every ring of the candidate neighbours) runs on
 * [defaultDispatcher] (see [com.geoquiz.app.di.DefaultDispatcher]), off the caller's dispatcher,
 * as it would for any other CPU-bound geometry work. The Kotlin default of [Dispatchers.Default]
 * is only for tests that construct this class directly (without Hilt); production always gets
 * the qualified binding from `di/AppModule.kt`.
 */
class GetCountryOutlineUseCase @Inject constructor(
    private val mapRepository: MapRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * @param cca3 the country's code.
     * @param includeRemote keep far-flung parts (e.g. France's overseas territories); off by
     *   default, matching how a country is framed for a region preset.
     * @param includeNeighbours also collect faint neighbouring land for context (see
     *   [OutlineGeometry]'s class doc for what counts as a neighbour here).
     * @return the silhouette, or null if [cca3] isn't a feature of the layer.
     */
    suspend operator fun invoke(
        cca3: String,
        includeRemote: Boolean = false,
        includeNeighbours: Boolean = true,
    ): OutlineModel? {
        val layer = mapRepository.layer(GeoLayerId.ADMIN0_50M)
        return withContext(defaultDispatcher) { OutlineGeometry.build(layer, cca3, includeRemote, includeNeighbours) }
    }
}
