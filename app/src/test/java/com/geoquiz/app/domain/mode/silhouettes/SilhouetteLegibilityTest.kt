package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.data.geo.GeoLayerReader
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.OutlineGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure-JVM measurement of every one of the 197 playable countries' real silhouettes (task P10-E,
 * Dav's device feedback "some Country silhouettes are a bit dodgy, like Tuvalu"), over the real
 * bundled `admin0_50m` asset, the same way `OutlineGeometryTest` loads it.
 */
class SilhouetteLegibilityTest {

    private val layer: GeoLayer by lazy { GeoLayerReader.read(File("src/main/assets/geo", GeoLayerId.ADMIN0_50M.fileName).readBytes()) }
    private val playableCodes: List<String> by lazy { layer.features.filter { it.isPlayable }.map { it.id } }

    private fun metricsFor(code: String): SilhouetteMetrics {
        val outline = requireNotNull(OutlineGeometry.build(layer, code, includeNeighbours = false)) { code }
        return SilhouetteLegibility.measure(outline)
    }

    @Test
    fun `print the measured metrics for all 197, sorted by land coverage`() {
        val rows = playableCodes.map { metricsFor(it) }.sortedBy { it.landCoverage }
        val report = StringBuilder()
        report.append("cca3,landCoverage,partCount,largestPartShare,largestPartVertexCount,recognisable\n")
        for (m in rows) {
            report.append(
                "%s,%.5f,%d,%.3f,%d,%s\n".format(
                    m.cca3, m.landCoverage, m.partCount, m.largestPartShare, m.largestPartVertexCount,
                    SilhouetteLegibility.isRecognisable(m)
                )
            )
        }
        println(report)
        assertEquals(197, rows.size)
    }

    @Test
    fun `the excluded set is exactly what the rule produces over the real asset`() {
        val excluded = playableCodes.filterNot { SilhouetteLegibility.isRecognisable(metricsFor(it)) }.toSet()
        assertEquals(SilhouetteLegibility.EXCLUDED_CCA3, excluded)
    }

    @Test
    fun `Tuvalu is excluded`() {
        assertTrue("TUV" in SilhouetteLegibility.EXCLUDED_CCA3)
        assertTrue(!SilhouetteLegibility.isRecognisable(metricsFor("TUV")))
    }

    @Test
    fun `big, clearly recognisable countries are never excluded`() {
        for (code in listOf("AUS", "ITA", "CHL", "JPN", "USA", "RUS", "BRA", "IND", "ZAF", "GBR")) {
            assertTrue("$code should not be excluded", code !in SilhouetteLegibility.EXCLUDED_CCA3)
            assertTrue("$code should be recognisable", SilhouetteLegibility.isRecognisable(metricsFor(code)))
        }
    }
}
