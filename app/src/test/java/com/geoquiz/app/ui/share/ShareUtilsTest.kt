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
    fun `a new mode's share text has no link`() {
        val text = ShareUtils.buildResultsShareText(
            categoryName = "Country silhouettes",
            itemsNoun = "countries",
            score = 10,
            total = 20,
            time = 60,
            deepLink = null
        )

        assertFalse(text.contains("http"))
        assertTrue(text.contains("10/20 countries"))
        assertTrue(text.endsWith("Can you beat me?"))
    }

    @Test
    fun `a challenge always carries its link`() {
        val link = Uri.parse("https://geoquiz-app.netlify.app/challenge?id=abc")

        val text = ShareUtils.buildChallengeShareText("All Countries", link)

        assertEquals("I challenge you to \"All Countries\"! Can you name them all?\n$link", text)
    }
}
