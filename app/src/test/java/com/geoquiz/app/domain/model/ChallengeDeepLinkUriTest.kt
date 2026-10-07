package com.geoquiz.app.domain.model

import android.net.Uri
import com.geoquiz.app.domain.challenge.ChallengeLinkParseResult
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The real android.net.Uri encoding and decoding, end to end. */
@RunWith(RobolectricTestRunner::class)
class ChallengeDeepLinkUriTest {

    private val signer = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray())

    private val link = ChallengeDeepLink(
        challengeId = "3f2a6c1e-1111-4a4a-9c9c-0123456789ab",
        categoryType = "flagcombo",
        categoryValue = "red+white",
        challengerName = "Zoë & Bo 🌏 +1",
        challengerScore = 9,
        challengerTotal = 12,
        challengerTime = 75,
        quizMode = "flags"
    )

    @Test
    fun `share url parses back as a verified challenge`() {
        val url = link.toShareUrl(signer)
        assertEquals("https", url.scheme)
        assertEquals("geoquiz-app.netlify.app", url.host)
        assertEquals("/challenge.html", url.path)
        assertEquals("1", url.getQueryParameter("v"))

        val result = ChallengeDeepLink.parse(Uri.parse(url.toString()), signer)
        result as ChallengeLinkParseResult.Valid
        assertTrue(result.scoreVerified)
        assertEquals(link, result.link)
    }

    @Test
    fun `custom scheme uri parses back as a verified challenge`() {
        val result = ChallengeDeepLink.parse(Uri.parse(link.toUri(signer).toString()), signer)
        result as ChallengeLinkParseResult.Valid
        assertTrue(result.scoreVerified)
        assertEquals(link, result.link)
    }

    @Test
    fun `edited score in the url is not trusted`() {
        val tampered = link.toShareUrl(signer).toString().replace("score=9", "score=12")
        val result = ChallengeDeepLink.parse(Uri.parse(tampered), signer)
        result as ChallengeLinkParseResult.Valid
        assertFalse(result.scoreVerified)
        assertNull(result.link.challengerScore)
    }

    @Test
    fun `garbage uris are invalid and never throw`() {
        listOf(
            "geoquiz://challenge",
            "geoquiz://challenge?id",
            "geoquiz://challenge?%%%=%zz&id=%",
            "geoquiz:challenge?id=a&ct=all&cv=_",
            "https://geoquiz-app.netlify.app/challenge.html?id=a&ct=all&cv=_&mode=flags",
            "https://geoquiz-app.netlify.app/challenge.html?id=a&ct=../../&cv=_",
            "mailto:someone@example.com",
            ""
        ).forEach { raw ->
            val result = ChallengeDeepLink.parse(Uri.parse(raw), signer)
            assertTrue("$raw should be invalid, got $result", result is ChallengeLinkParseResult.Invalid)
        }
    }

    @Test
    fun `duplicate parameters use the first value`() {
        val url = link.toShareUrl(signer).toString() + "&score=12"
        val result = ChallengeDeepLink.parse(Uri.parse(url), signer)
        result as ChallengeLinkParseResult.Valid
        assertTrue(result.scoreVerified)
        assertEquals(9, result.link.challengerScore)
    }
}
