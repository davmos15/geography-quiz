package com.geoquiz.app.domain.mode.silhouettes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import com.geoquiz.app.data.geo.GeoLayerReader
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.OutlineGeometry
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * A contact sheet of all 197 real silhouettes, for a human to eyeball (task P10-E, Dav's device
 * feedback "some Country silhouettes are a bit dodgy, like Tuvalu"). Renders with
 * `android.graphics` under Robolectric's native graphics mode, the same technique
 * `ShareCardRenderer`/`ShareCardRendererTest` and `CountryOutlineRenderTest` use for real pixel
 * output in a plain JVM test - plain `java.awt` is not usable here (Android's `android.jar`
 * shadows it on this module's unit-test classpath, confirmed by a failed compile attempt).
 *
 * `@Ignore`d so it never runs as part of the normal suite - this is a one-off dev tool, not a
 * regression test, and (unlike the existing `-Pgeoquiz.perf=true`/`-Pgeoquiz.storeShots=true`
 * gates) needs no Gradle wiring at all. Run it directly (temporarily remove `@Ignore`) to
 * regenerate the PNG.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SilhouetteContactSheetTest {

    private val layer: GeoLayer by lazy { GeoLayerReader.read(File("src/main/assets/geo", GeoLayerId.ADMIN0_50M.fileName).readBytes()) }
    private val playableCodes: List<String> by lazy { layer.features.filter { it.isPlayable }.map { it.id }.sorted() }

    @Ignore("Dev tool only: a human looks at the PNG, never asserted on. Run directly to regenerate it.")
    @Test
    fun `render a contact sheet of all 197 silhouettes`() {
        val columns = 14
        val cellSize = 160
        val labelHeight = 26
        val rows = (playableCodes.size + columns - 1) / columns
        val width = columns * cellSize
        val height = rows * (cellSize + labelHeight)

        val bitmap: Bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val cellBackgroundPaint = Paint().apply { color = Color.rgb(245, 245, 245) }
        val cellBorderPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.rgb(60, 60, 60)
        }
        val labelPaint = Paint().apply { isAntiAlias = true; textSize = 13f }

        playableCodes.forEachIndexed { index, code ->
            val col = index % columns
            val row = index / columns
            val cellX = col * cellSize
            val cellY = row * (cellSize + labelHeight)

            canvas.drawRect(
                cellX.toFloat(), cellY.toFloat(), (cellX + cellSize).toFloat(), (cellY + cellSize).toFloat(), cellBackgroundPaint
            )
            canvas.drawRect(
                cellX + 0.5f, cellY + 0.5f, (cellX + cellSize) - 0.5f, (cellY + cellSize) - 0.5f, cellBorderPaint
            )

            val outline = OutlineGeometry.build(layer, code, includeNeighbours = false)
            val metrics = outline?.let { SilhouetteLegibility.measure(it) }
            val excluded = code in SilhouetteLegibility.EXCLUDED_CCA3

            if (outline != null) {
                val padding = cellSize * 0.08f
                val boxPx = cellSize - 2 * padding
                val centreX = cellX + cellSize / 2f
                val centreY = cellY + cellSize / 2f
                val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
                for (ring in outline.rings) {
                    val points = ring.points
                    if (points.size < 4) continue
                    path.moveTo(centreX + points[0] * boxPx, centreY + points[1] * boxPx)
                    var p = 2
                    while (p < points.size) {
                        path.lineTo(centreX + points[p] * boxPx, centreY + points[p + 1] * boxPx)
                        p += 2
                    }
                    path.close()
                }
                fillPaint.color = if (excluded) Color.rgb(224, 123, 123) else Color.rgb(94, 150, 214)
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            labelPaint.color = if (excluded) Color.rgb(200, 30, 30) else Color.DKGRAY
            val label = if (metrics != null) "$code ${"%.3f".format(metrics.landCoverage)}" else "$code ?"
            canvas.drawText(label, cellX + 4f, (cellY + cellSize + labelHeight - 8).toFloat(), labelPaint)
        }

        val outFile = File(CONTACT_SHEET_PATH)
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" }
        }
        println("Wrote ${playableCodes.size} silhouettes to ${outFile.absolutePath}")
    }

    companion object {
        /** Written under the module's build folder (relative to `app/`, the test working directory). */
        const val CONTACT_SHEET_PATH = "build/silhouettes_contact_sheet.png"
    }
}
