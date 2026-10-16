package com.geoquiz.app.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Colour ints for [ShareCardRenderer.render], resolved by the caller from the current Material
 * theme (`MaterialTheme.colorScheme.X.toArgb()` in a composable), so this plain
 * [android.graphics] renderer stays free of Compose and theme tokens are still the only source
 * of colour, as everywhere else in the app.
 */
data class ShareCardPalette(
    val background: Int,
    val onBackground: Int,
    val onSurfaceVariant: Int,
    val primary: Int
)

/**
 * Draws an original share card (8.3) for a finished result: app name, title, a big score with
 * its percentage, the date and time, and a link - GeoQuiz's own plain layout, no grids or tiles,
 * nothing borrowed from another app (L9).
 *
 * A fixed 1080x1080 px square PNG: square fits most share-sheet and social previews without an
 * odd crop, and keeps this renderer simple (one width to wrap text to, no portrait/landscape
 * variants to maintain).
 */
object ShareCardRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1080
    private const val MARGIN = 80f
    private const val TITLE_MAX_LINES = 3
    private const val LINK_MAX_LINES = 2
    private const val SHARE_DIR = "share"

    private const val APP_NAME_SIZE = 40f
    private const val TITLE_SIZE = 56f
    private const val SCORE_SIZE = 150f
    private const val PERCENTAGE_SIZE = 44f
    private const val META_SIZE = 42f
    private const val LINK_SIZE = 34f

    /**
     * Where each block sits, in px from the top. [metaBottom] < [linkTop] always holds, because
     * the title and link are both capped in lines (checked by [ShareCardRendererTest]).
     */
    internal data class Placement(
        val titleTop: Float,
        val scoreTop: Float,
        val percentageBaseline: Float,
        val metaTop: Float,
        val metaBottom: Float,
        val linkTop: Float
    )

    /** Pure drawing, no [Context]: unit-tested directly ([ShareCardRendererTest]). */
    fun render(content: ShareCardContent, palette: ShareCardPalette, appName: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.background)

        val appNamePaint = textPaint(palette.onSurfaceVariant, APP_NAME_SIZE, bold = true)
        val titleLayout = titleLayout(content, palette.onBackground)
        val scorePaint = textPaint(palette.primary, SCORE_SIZE, bold = true)
        val percentagePaint = textPaint(palette.onSurfaceVariant, PERCENTAGE_SIZE, bold = false)
        val metaPaint = textPaint(palette.onBackground, META_SIZE, bold = false)
        val linkLayout = linkLayout(content, palette.onSurfaceVariant)
        val at = place(titleLayout, linkLayout)

        canvas.drawText(appName, MARGIN, MARGIN + APP_NAME_SIZE, appNamePaint)
        canvas.withTranslation(MARGIN, at.titleTop) { titleLayout.draw(this) }
        canvas.drawText(content.score, MARGIN, at.scoreTop + SCORE_SIZE, scorePaint)
        canvas.drawText(content.percentageText, MARGIN, at.percentageBaseline, percentagePaint)
        canvas.drawText(
            TextUtils.ellipsize(content.metaLine, metaPaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString(),
            MARGIN, at.metaTop + META_SIZE, metaPaint
        )
        canvas.withTranslation(MARGIN, at.linkTop) { linkLayout.draw(this) }
        return bitmap
    }

    /** The block positions for [content], without drawing ([ShareCardRendererTest]). */
    internal fun placementFor(content: ShareCardContent): Placement =
        place(titleLayout(content, 0), linkLayout(content, 0))

    private val textWidth: Int get() = (WIDTH - MARGIN * 2).toInt()

    private fun titleLayout(content: ShareCardContent, colour: Int) =
        staticLayout(content.title, textPaint(colour, TITLE_SIZE, bold = true), TITLE_MAX_LINES)

    private fun linkLayout(content: ShareCardContent, colour: Int) =
        staticLayout(content.displayLink, textPaint(colour, LINK_SIZE, bold = false), LINK_MAX_LINES)

    private fun place(titleLayout: StaticLayout, linkLayout: StaticLayout): Placement {
        val titleTop = MARGIN + 130f
        val scoreTop = titleTop + titleLayout.height + 70f
        val percentageBaseline = scoreTop + SCORE_SIZE + 64f
        val metaTop = percentageBaseline + 90f
        return Placement(
            titleTop = titleTop,
            scoreTop = scoreTop,
            percentageBaseline = percentageBaseline,
            metaTop = metaTop,
            metaBottom = metaTop + META_SIZE * 1.3f,
            linkTop = HEIGHT - MARGIN - linkLayout.height
        )
    }

    private fun textPaint(colorInt: Int, size: Float, bold: Boolean) = TextPaint().apply {
        color = colorInt
        textSize = size
        isAntiAlias = true
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun staticLayout(text: String, paint: TextPaint, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private inline fun Canvas.withTranslation(dx: Float, dy: Float, block: Canvas.() -> Unit) {
        save()
        translate(dx, dy)
        block()
        restore()
    }

    /**
     * Writes [bitmap] to a new `cacheDir/share/share_card_<time>.png`, deleting older cards first,
     * so a receiving app still reading the previous URI never sees it overwritten. Blocking I/O:
     * call off the main thread. Throws on I/O failure; the caller falls back to the text share.
     */
    fun writeToCache(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "share_card_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" }
        }
        return file
    }

    /** The image share intent for [file], before the chooser wraps it ([ShareCardRendererTest]). */
    internal fun imageShareIntent(context: Context, file: File, text: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            // ClipData lets the system chooser show the image preview (Android 10+).
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Shares [file] (from [writeToCache]) as `image/png` through [FileProvider], with [text] as
     * [Intent.EXTRA_TEXT] alongside it. Starts the chooser activity; call from the main thread.
     */
    fun shareImage(context: Context, file: File, text: String, chooserTitle: String) {
        val chooser = Intent.createChooser(imageShareIntent(context, file, text), chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
