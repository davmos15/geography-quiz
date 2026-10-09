package com.geoquiz.app.domain.challenge

import com.geoquiz.app.di.ChallengeModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeLinkSignerTest {

    private val signer = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray())

    private val fields = ChallengeLinkFields(
        id = "3f2a6c1e-1111-4a4a-9c9c-0123456789ab",
        categoryType = "region",
        categoryValue = "Europe",
        name = "Dav",
        mode = "countries",
        score = "40",
        total = "44",
        time = "312"
    )

    @Test
    fun `signature verifies under the same key`() {
        assertTrue(signer.verify(fields, signer.sign(fields)))
    }

    @Test
    fun `signature is base64url without padding`() {
        val sig = signer.sign(fields)
        assertTrue(sig.matches(Regex("[A-Za-z0-9_-]{43}")))
    }

    @Test
    fun `changing any field invalidates the signature`() {
        val sig = signer.sign(fields)
        val tampered = listOf(
            fields.copy(id = "3f2a6c1e-1111-4a4a-9c9c-0123456789ac"),
            fields.copy(categoryType = "subregion"),
            fields.copy(categoryValue = "Asia"),
            fields.copy(name = "Dave"),
            fields.copy(mode = "capitals"),
            fields.copy(score = "44"),
            fields.copy(score = "040"),
            fields.copy(total = "45"),
            fields.copy(time = "1"),
            fields.copy(score = null),
            fields.copy(time = null),
            fields.copy(name = null),
            fields.copy(mode = null)
        )
        tampered.forEach { assertFalse("$it should not verify", signer.verify(it, sig)) }
    }

    @Test
    fun `absent and empty fields sign differently`() {
        assertNotEquals(
            signer.sign(fields.copy(time = null)),
            signer.sign(fields.copy(time = ""))
        )
    }

    @Test
    fun `moving characters between fields changes the signature`() {
        val a = fields.copy(categoryType = "ab", categoryValue = "c")
        val b = fields.copy(categoryType = "a", categoryValue = "bc")
        assertNotEquals(ChallengeLinkSigner.canonical(a), ChallengeLinkSigner.canonical(b))
        assertFalse(signer.verify(b, signer.sign(a)))
    }

    @Test
    fun `a different key is rejected`() {
        val other = ChallengeLinkSigner("another-key-0123456789abcdef".toByteArray())
        assertFalse(other.verify(fields, signer.sign(fields)))
    }

    @Test
    fun `missing or garbage signatures are rejected without throwing`() {
        listOf(null, "", "!!!", "abc", "a".repeat(500), flipFirstChar(signer.sign(fields)))
            .forEach { assertFalse("$it should not verify", signer.verify(fields, it)) }
    }

    private fun flipFirstChar(sig: String): String =
        (if (sig[0] == 'A') 'B' else 'A') + sig.substring(1)

    @Test
    fun `unicode names round trip`() {
        val unicode = fields.copy(name = "Zoë 東京 🌏")
        assertTrue(signer.verify(unicode, signer.sign(unicode)))
        assertFalse(signer.verify(unicode.copy(name = "Zoe 東京 🌏"), signer.sign(unicode)))
    }

    @Test
    fun `base64url key decodes with or without padding`() {
        val padded = ChallengeLinkSigner.fromBase64Url("dGVzdC1rZXktMDEyMzQ1Njc4OWFiY2RlZg==")
        val unpadded = ChallengeLinkSigner.fromBase64Url("dGVzdC1rZXktMDEyMzQ1Njc4OWFiY2RlZg")
        assertEquals(signer.sign(fields), padded.sign(fields))
        assertEquals(signer.sign(fields), unpadded.sign(fields))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `short keys are refused`() {
        ChallengeLinkSigner(ByteArray(8))
    }

    @Test
    fun `build config key from the Hilt module is usable`() {
        val buildSigner = ChallengeModule.provideChallengeLinkSigner()
        assertTrue(buildSigner.verify(fields, buildSigner.sign(fields)))
    }
}
