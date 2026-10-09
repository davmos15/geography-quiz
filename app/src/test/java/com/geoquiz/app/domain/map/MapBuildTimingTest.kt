package com.geoquiz.app.domain.map

import com.geoquiz.app.data.geo.GeoLayerReader
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM micro-check of the load path the map pays once per layer: decode every committed layer
 * and project the two world country layers (what `MapLayerPaths.build` does before it fills
 * the paths). Prints the numbers; the time bounds are checked only with -Dgeoquiz.perf=true.
 * Frame rate while panning and zooming can only be measured on a device (debug Map preview).
 */
class MapBuildTimingTest {

    private fun bytes(id: GeoLayerId) = File("src/main/assets/geo", id.fileName).readBytes()

    private inline fun bestOf(runs: Int, block: () -> Unit): Double {
        var best = Long.MAX_VALUE
        repeat(runs) {
            val start = System.nanoTime()
            block()
            best = minOf(best, System.nanoTime() - start)
        }
        return best / 1e6
    }

    @Test
    fun `decode and projection times`() {
        val files = GeoLayerId.entries.associateWith { bytes(it) }
        repeat(3) { files.values.forEach { GeoLayerReader.read(it) } } // warm up the JIT

        val decode = files.mapValues { (_, b) -> bestOf(5) { GeoLayerReader.read(b) } }
        val coarse = GeoLayerReader.read(files.getValue(GeoLayerId.ADMIN0_110M))
        val detail = GeoLayerReader.read(files.getValue(GeoLayerId.ADMIN0_50M))
        repeat(3) { ProjectedLayer.build(detail, EqualEarthProjection) }
        val projectCoarse = bestOf(5) { ProjectedLayer.build(coarse, EqualEarthProjection) }
        val projectDetail = bestOf(5) { ProjectedLayer.build(detail, EqualEarthProjection) }
        val laea = LambertAzimuthalEqualAreaProjection(15.0, 52.0)
        val projectDetailLaea = bestOf(5) { ProjectedLayer.build(detail, laea) }

        val report = buildString {
            appendLine("Map layer timings (JVM, best of 5, warm):")
            decode.forEach { (id, ms) -> appendLine("  decode %-20s %7.2f ms".format(id.fileName, ms)) }
            appendLine("  decode all              %7.2f ms".format(decode.values.sum()))
            appendLine("  project admin0_110m EE  %7.2f ms (${coarse.features.sumOf { it.pointCount }} points)".format(projectCoarse))
            appendLine("  project admin0_50m EE   %7.2f ms (${detail.features.sumOf { it.pointCount }} points)".format(projectDetail))
            appendLine("  project admin0_50m LAEA %7.2f ms".format(projectDetailLaea))
        }
        println(report)

        // Timing bounds only on request (-Dgeoquiz.perf=true), so shared CI machines don't flake.
        if (System.getProperty("geoquiz.perf") == "true") {
            assertTrue(report, decode.values.sum() < 2_000)
            assertTrue(report, projectDetail < 1_000)
        }
    }
}
