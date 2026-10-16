package com.geoquiz.app.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * [ShareCardRenderer.render]'s plain [android.graphics] drawing (8.3), offscreen (Robolectric
 * native graphics, same technique as `CountryOutlineRenderTest`/`MapCanvasUiTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardRendererTest {

    private val content = ShareCardContent(
        title = "Countries: All Countries",
        date = "16 Oct 2026",
        score = "150/197",
        percentage = 76,
        percentageText = "76%",
        time = "3m 12s",
        timeSeconds = 192,
        metaLine = "16 Oct 2026 · 3m 12s",
        link = "https://play.google.com/store/apps/details?id=com.geoquiz.app",
        displayLink = "play.google.com/store/apps/details?id=com.geoquiz.app"
    )

    private val background = 0xFFFFFFFF.toInt()

    private val palette = ShareCardPalette(
        background = background,
        onBackground = 0xFF101010.toInt(),
        onSurfaceVariant = 0xFF444444.toInt(),
        primary = 0xFF0B5CAD.toInt()
    )

    /** True when any pixel in the box differs from [background] (a glyph was drawn there). */
    private fun regionHasNonBackgroundPixel(
        bitmap: android.graphics.Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Boolean {
        for (x in left until right) {
            for (y in top until bottom) {
                if (bitmap.getPixel(x, y) != background) return true
            }
        }
        return false
    }

    @Test
    fun `the card is the documented 1080x1080 size`() {
        val bitmap = ShareCardRenderer.render(content, palette, appName = "GeoQuiz")

        assertEquals(1080, ShareCardRenderer.WIDTH)
        assertEquals(1080, ShareCardRenderer.HEIGHT)
        assertEquals(ShareCardRenderer.WIDTH, bitmap.width)
        assertEquals(ShareCardRenderer.HEIGHT, bitmap.height)
    }

    @Test
    fun `the background fills a corner untouched by any text`() {
        val bitmap = ShareCardRenderer.render(content, palette, appName = "GeoQuiz")

        assertEquals(background, bitmap.getPixel(bitmap.width - 2, 2))
    }

    @Test
    fun `the card is not a single flat colour - each text block draws something`() {
        val bitmap = ShareCardRenderer.render(content, palette, appName = "GeoQuiz")

        assertTrue(
            "the app name should draw a visible glyph",
            regionHasNonBackgroundPixel(bitmap, 0, 60, 400, 170)
        )
        assertTrue(
            "the title should draw a visible glyph",
            regionHasNonBackgroundPixel(bitmap, 0, 190, 1080, 340)
        )
        assertTrue(
            "the big score should draw a visible glyph",
            regionHasNonBackgroundPixel(bitmap, 0, 340, 700, 620)
        )
        assertTrue(
            "the link at the bottom should draw a visible glyph",
            regionHasNonBackgroundPixel(bitmap, 0, 950, 1080, 1080)
        )
    }

    @Test
    fun `a long title wraps onto more than one line without crashing`() {
        val longTitle = content.copy(
            title = "Countries: A very long category name that goes on and on and on for quite a while indeed"
        )

        val bitmap = ShareCardRenderer.render(longTitle, palette, appName = "GeoQuiz")

        assertEquals(ShareCardRenderer.WIDTH, bitmap.width)
        assertTrue(
            "the wrapped title should still draw a visible glyph",
            regionHasNonBackgroundPixel(bitmap, 0, 190, 1080, 420)
        )
    }

    @Test
    fun `the longest title and link never overlap the date line`() {
        val worst = content.copy(
            title = "Capitals: " + "A very long place category name ".repeat(12),
            displayLink = "geoquiz-app.netlify.app/" + "challenge/".repeat(40)
        )

        val at = ShareCardRenderer.placementFor(worst)

        assertTrue("meta ends at ${at.metaBottom}, link starts at ${at.linkTop}", at.metaBottom < at.linkTop)
        // And it still draws without crashing.
        assertEquals(ShareCardRenderer.HEIGHT, ShareCardRenderer.render(worst, palette, appName = "Geography Quiz").height)
    }

    @Test
    fun `the cached card is shared through the app's FileProvider with a read grant`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = ShareCardRenderer.render(content, palette, appName = "Geography Quiz")

        val first = ShareCardRenderer.writeToCache(context, bitmap)
        Thread.sleep(5) // distinct timestamped names
        val second = ShareCardRenderer.writeToCache(context, bitmap)

        assertTrue("an older card is cleared", !first.exists())
        assertTrue(second.length() > 0)

        // FileProvider matches roots with "/" separators, so on a Windows test host no file ever
        // belongs to one; Android and Linux CI check the real authority and file_paths.xml here.
        org.junit.Assume.assumeTrue(java.io.File.separatorChar == '/')
        val intent = ShareCardRenderer.imageShareIntent(context, second, "text")
        assertEquals("image/png", intent.type)
        assertEquals("text", intent.getStringExtra(android.content.Intent.EXTRA_TEXT))
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
