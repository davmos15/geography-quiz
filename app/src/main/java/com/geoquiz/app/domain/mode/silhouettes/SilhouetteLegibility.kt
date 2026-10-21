package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.map.OutlineRing
import kotlin.math.abs

/**
 * How recognisable a built silhouette ([OutlineModel]) actually is (task P10-E, Dav's device
 * feedback: "some Country silhouettes are a bit dodgy, like Tuvalu"). [OutlineGeometry][com.geoquiz.app.domain.map.OutlineGeometry]
 * sizes every country's own bounding box to a consistent visual size - great for one compact
 * landmass, but for a nation of scattered atolls (Tuvalu, Kiribati, the Marshall Islands,
 * Micronesia, the Maldives...) that box is mostly empty sea with a few specks of land, which
 * reads as unrecognisable rather than as a silhouette.
 *
 * @property landCoverage fraction of the 1x1 box ([com.geoquiz.app.domain.map.OutlineGeometry.BOX_HALF_EXTENT]'s
 *   box has area 1.0) actually covered by land (outer ring area minus holes).
 * @property partCount number of separate landmasses (outer rings, holes excluded).
 * @property largestPartShare the single largest part's own area, as a fraction of the total land
 *   area - 1.0 for one compact landmass, low for several similarly sized scattered islands.
 * @property largestPartVertexCount vertex count of that largest part (a very simple shape, like a
 *   speck simplified to a handful of points, is itself a sign of nothing worth showing).
 */
data class SilhouetteMetrics(
    val cca3: String,
    val landCoverage: Float,
    val partCount: Int,
    val largestPartShare: Float,
    val largestPartVertexCount: Int,
)

object SilhouetteLegibility {

    /**
     * Below this land coverage, a silhouette reads as empty box + specks rather than a shape.
     * Measured over the real `admin0_50m` asset (task P10-E; see `SilhouetteLegibilityTest`'s
     * printed report for the full 197-country distribution this was chosen from): there is a
     * sharp, natural gap between Solomon Islands at 1.43% and every lower country - Palau 0.10%,
     * Tonga 0.35%, Maldives 0.49%, Kiribati 0.004%, Micronesia 0.006%, Marshall Islands 0.055% -
     * each of which renders as a few flecks in an empty box (confirmed against the contact sheet,
     * `SilhouetteContactSheetTest`), while Solomon Islands and everything above it has enough
     * land, even split across several scattered islands, to read as a visible if ragged shape.
     * 1% sits in that gap.
     *
     * A single dominant part was tried as an escape hatch for a country otherwise below this
     * floor (reasoning: "a single recognisable island with a few negligible specks nearby reads
     * fine"), but the contact sheet showed it does not hold up in practice: Palau's one dominant
     * island is still only 0.1% of the box, which is too small to actually see, dominant or not -
     * so there is no escape hatch here, deliberately. Land coverage alone decides this floor.
     */
    const val MIN_LAND_COVERAGE = 0.01f

    /**
     * A silhouette whose largest part has this few vertices or fewer is excluded regardless of
     * coverage: at 50m resolution it has been simplified down to a bare triangle or quadrilateral
     * that carries no shape information at all - not "a small but real outline", just a crude
     * polygon any tiny country's bounding box would produce (task P10-E, "tiny states may be
     * crude blobs at 50m resolution"). Measured effect: across all 197, only three countries'
     * largest part has 4 vertices or fewer - Tuvalu and the Vatican (3, literal triangles) and
     * Monaco (4) - while the next lowest, Nauru, already has 5 and reads as a recognisable rounded
     * shape (and fills most of its own box, 46.6% coverage). This is also the mechanism behind
     * Dav's specific "like Tuvalu" feedback: Tuvalu's own land area is not actually that sparse in
     * its box (9.0%), so [MIN_LAND_COVERAGE] alone would not have caught it - confirmed on the
     * contact sheet, where Tuvalu, the Vatican and Monaco all render as a plain, featureless
     * triangle.
     */
    const val MIN_VERTEX_COUNT = 4

    /**
     * True if [metrics] is recognisable enough to keep in a Country silhouettes round: enough
     * land in the box, *and* enough vertices in its largest part to carry real outline
     * information.
     */
    fun isRecognisable(metrics: SilhouetteMetrics): Boolean =
        metrics.landCoverage >= MIN_LAND_COVERAGE && metrics.largestPartVertexCount > MIN_VERTEX_COUNT

    /**
     * Country silhouettes' excluded set (task P10-E): every playable country whose silhouette
     * fails [isRecognisable] on the real bundled `admin0_50m` asset, pinned here (rather than
     * computed at quiz time) so excluding a country is a deliberate, reviewable line, and so
     * [com.geoquiz.app.domain.mode.silhouettes.SilhouettesQuestionGenerator] needs no map-layer
     * dependency just to filter a candidate list. `SilhouetteLegibilityTest` re-derives this set
     * from [isRecognisable] over the real asset and asserts it matches exactly: if the geodata
     * ever changes enough to move a country across the line, that test fails instead of silently
     * drifting. Eyeballed against `SilhouetteContactSheetTest`'s rendered PNG: every one of these
     * nine is blank or a plain, featureless shape; every other country shows a visible, if
     * sometimes ragged, outline.
     *
     * Two distinct reasons, both measured (see the constants above):
     * - Scattered-atoll (or, for Palau, just plain tiny) nations below the land-coverage floor:
     *   Kiribati, Micronesia, the Marshall Islands, Palau, Tonga, Maldives.
     * - Crude, near-featureless blobs (3-4 vertices) regardless of coverage: Tuvalu, the Vatican,
     *   Monaco.
     *
     * Nauru, San Marino, Comoros and São Tomé and Príncipe (also tiny) are kept because they are
     * comfortably over both floors: see `SilhouetteLegibilityTest`'s `"included"` assertions.
     */
    val EXCLUDED_CCA3: Set<String> = setOf(
        "KIR", // Kiribati
        "FSM", // Micronesia
        "MHL", // Marshall Islands
        "PLW", // Palau
        "TON", // Tonga
        "MDV", // Maldives
        "TUV", // Tuvalu
        "VAT", // Vatican City
        "MCO", // Monaco
    )

    /**
     * Land area, part count and the largest part's share, measured the same way for every
     * caller (the generator's exclusion check, `SilhouetteLegibilityTest`'s regression and
     * contact-sheet tooling).
     */
    fun measure(outline: OutlineModel): SilhouetteMetrics {
        var largestArea = -1f
        var largestVertexCount = 0
        var totalLand = 0f
        var partCount = 0
        var currentOuterArea = 0f
        var currentHolesArea = 0f
        var currentVertexCount = 0
        var hasCurrent = false

        fun flush() {
            if (!hasCurrent) return
            val net = (currentOuterArea - currentHolesArea).coerceAtLeast(0f)
            totalLand += net
            partCount++
            if (net > largestArea) {
                largestArea = net
                largestVertexCount = currentVertexCount
            }
        }

        for (ring in outline.rings) {
            val area = ringArea(ring.points)
            if (!ring.isHole) {
                flush()
                currentOuterArea = area
                currentHolesArea = 0f
                currentVertexCount = ring.points.size / 2
                hasCurrent = true
            } else if (hasCurrent) {
                currentHolesArea += area
            }
        }
        flush()

        val largestShare = if (totalLand > 0f) (largestArea.coerceAtLeast(0f)) / totalLand else 0f
        return SilhouetteMetrics(
            cca3 = outline.cca3,
            landCoverage = totalLand,
            partCount = partCount,
            largestPartShare = largestShare,
            largestPartVertexCount = largestVertexCount,
        )
    }

    /** The shoelace formula's absolute area, in box units; [OutlineRing.points] are interleaved `[x0, y0, x1, y1, ...]`. */
    private fun ringArea(points: FloatArray): Float {
        val n = points.size / 2
        if (n < 3) return 0f
        var sum = 0f
        for (i in 0 until n) {
            val x1 = points[2 * i]
            val y1 = points[2 * i + 1]
            val j = (i + 1) % n
            val x2 = points[2 * j]
            val y2 = points[2 * j + 1]
            sum += x1 * y2 - x2 * y1
        }
        return abs(sum) / 2f
    }
}
