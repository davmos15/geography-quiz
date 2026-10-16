package com.geoquiz.app.ui.share

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pure share text builders behind [ShareUtils.shareResults]/[ShareUtils.shareChallenge]:
 * a classic mode's result carries a challenge link, a new (non-classic) mode's does not (D35).
 */
@RunWith(RobolectricTestRunner::class)
class ShareUtilsTest {

    @Test
    fun `a classic mode's share text carries the challenge link`() {
        val link = Uri.parse("https://geoquiz-app.netlify.app/challenge?id=abc")

        val text = ShareUtils.buildResultsShareText(
            categoryName = "All Countries",
            itemsNoun = "countries",
            score = 150,
            total = 197,
            time = 192,
            deepLink = link
        )

        assertTrue(text.contains("150/197 countries"))
        assertTrue(text.contains("\"All Countries\""))
        assertTrue(text.endsWith("\n$link"))
    }

    @Test
    fun `a new mode's share text has no challenge link, but gets the date and the Play Store link`() {
        val text = ShareUtils.buildResultsShareText(
            categoryName = "Country silhouettes",
            itemsNoun = "countries",
            score = 10,
            total = 20,
            time = 60,
            deepLink = null,
            date = "16 Oct 2026"
        )

        assertTrue(text.contains("10/20 countries"))
        assertTrue(text.contains("Can you beat me?"))
        assertTrue(text.contains("\n16 Oct 2026\n"))
        assertTrue(text.endsWith(ShareCardContent.PLAY_STORE_URL))
    }

    @Test
    fun `a new mode's share text with no date still gets the Play Store link`() {
        val text = ShareUtils.buildResultsShareText(
            categoryName = "Country silhouettes",
            itemsNoun = "countries",
            score = 10,
            total = 20,
            time = 60,
            deepLink = null
        )

        assertTrue(text.endsWith(ShareCardContent.PLAY_STORE_URL))
        // No stray blank line where the omitted date would have gone.
        assertFalse(text.contains("\n\n"))
    }

    @Test
    fun `neither share text contains emoji`() {
        val link = Uri.parse("https://geoquiz-app.netlify.app/challenge?id=abc")
        val classicText = ShareUtils.buildResultsShareText(
            categoryName = "All Countries", itemsNoun = "countries",
            score = 150, total = 197, time = 192, deepLink = link
        )
        val newModeText = ShareUtils.buildResultsShareText(
            categoryName = "Country silhouettes", itemsNoun = "countries",
            score = 10, total = 20, time = 60, deepLink = null, date = "16 Oct 2026"
        )

        assertFalse(containsEmoji(classicText))
        assertFalse(containsEmoji(newModeText))
    }

    /** True if any codepoint falls in a common emoji block (pictographs, symbols, dingbats). */
    private fun containsEmoji(text: String): Boolean = text.codePoints().anyMatch { cp ->
        cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF
    }

    @Test
    fun `a challenge always carries its link`() {
        val link = Uri.parse("https://geoquiz-app.netlify.app/challenge?id=abc")

        val text = ShareUtils.buildChallengeShareText("All Countries", link)

        assertEquals("I challenge you to \"All Countries\"! Can you name them all?\n$link", text)
    }
}
