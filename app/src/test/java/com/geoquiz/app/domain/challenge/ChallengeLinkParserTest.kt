package com.geoquiz.app.domain.challenge

import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.QuizCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChallengeLinkParserTest {

    private val signer = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray())
    private val parser = ChallengeLinkParser(signer)

    private val scored = ChallengeDeepLink(
        challengeId = "3f2a6c1e-1111-4a4a-9c9c-0123456789ab",
        categoryType = "region",
        categoryValue = "Europe",
        challengerName = "Dav",
        challengerScore = 40,
        challengerTotal = 44,
        challengerTime = 312,
        quizMode = "countries"
    )

    private fun httpsInput(query: Map<String, String>, path: String = "/challenge.html") =
        ChallengeLinkInput("https", "geoquiz-app.netlify.app", path, query)

    private fun customInput(query: Map<String, String>) =
        ChallengeLinkInput("geoquiz", "challenge", "", query)

    private fun signedQuery(link: ChallengeDeepLink = scored) = parser.encode(link).toMap()

    /** A pre-signing (v2.7.2) link: no v, no sig. */
    private val legacyQuery = mapOf(
        "id" to "abc-123", "ct" to "startletter", "cv" to "B", "name" to "Sam",
        "mode" to "capitals", "score" to "10", "total" to "12", "time" to "95"
    )

    private fun valid(result: ChallengeLinkParseResult): ChallengeLinkParseResult.Valid =
        result as? ChallengeLinkParseResult.Valid ?: run {
            fail("expected Valid, got $result")
            throw AssertionError()
        }

    private fun assertInvalid(input: ChallengeLinkInput) {
        val result = parser.parse(input)
        assertTrue("expected Invalid for $input, got $result", result is ChallengeLinkParseResult.Invalid)
    }

    // Round trip

    @Test
    fun `signed share link round trips with a verified score`() {
        val result = valid(parser.parse(httpsInput(signedQuery())))
        assertTrue(result.scoreVerified)
        assertEquals(scored, result.link)
        assertEquals(QuizCategory.ByRegion("Europe"), result.category)
    }

    @Test
    fun `signed custom scheme link round trips`() {
        val result = valid(parser.parse(customInput(signedQuery())))
        assertTrue(result.scoreVerified)
        assertEquals(scored, result.link)
    }

    @Test
    fun `netlify pretty path without html is accepted`() {
        assertTrue(valid(parser.parse(httpsInput(signedQuery(), path = "/challenge"))).scoreVerified)
    }

    @Test
    fun `signed link without a score round trips`() {
        val noScore = scored.copy(challengerScore = null, challengerTotal = null, challengerTime = null)
        val result = valid(parser.parse(httpsInput(signedQuery(noScore))))
        assertTrue(result.scoreVerified)
        assertEquals(noScore, result.link)
    }

    @Test
    fun `encode signs the sanitised name so long names still verify`() {
        val longName = scored.copy(challengerName = "  A very long player name that goes on  ")
        val query = signedQuery(longName)
        assertEquals("A very long player name", query["name"])
        val result = valid(parser.parse(httpsInput(query)))
        assertTrue(result.scoreVerified)
        assertEquals(40, result.link.challengerScore)
    }

    @Test
    fun `unicode name round trips`() {
        val link = scored.copy(challengerName = "Zoë 東京 🌏")
        val result = valid(parser.parse(httpsInput(signedQuery(link))))
        assertTrue(result.scoreVerified)
        assertEquals("Zoë 東京 🌏", result.link.challengerName)
    }

    @Test
    fun `flag and capital categories round trip`() {
        listOf(
            scored.copy(categoryType = "flagcombo", categoryValue = "red+white", quizMode = "flags"),
            scored.copy(categoryType = "flagelement", categoryValue = "coat_of_arms", quizMode = "flags"),
            scored.copy(categoryType = "capitalmatches", categoryValue = "_", quizMode = "capitals"),
            scored.copy(categoryType = "lengthrange", categoryValue = "6-6"),
            scored.copy(categoryType = "wordcount", categoryValue = "-2"),
            scored.copy(categoryType = "all", categoryValue = "_")
        ).forEach { link ->
            val result = valid(parser.parse(httpsInput(signedQuery(link))))
            assertTrue(result.scoreVerified)
            assertEquals(link, result.link)
        }
    }

    // Tampering

    @Test
    fun `tampered score is rejected but the challenge is kept`() {
        val query = signedQuery() + ("score" to "44")
        val result = valid(parser.parse(httpsInput(query)))
        assertFalse(result.scoreVerified)
        assertNull(result.link.challengerScore)
        assertNull(result.link.challengerTotal)
        assertNull(result.link.challengerTime)
        assertEquals("Dav", result.link.challengerName)
        assertEquals("region", result.link.categoryType)
    }

    @Test
    fun `tampering any field drops the score`() {
        listOf(
            "total" to "40", "time" to "1", "name" to "Eve", "cv" to "Asia",
            "ct" to "subregion", "mode" to "capitals", "id" to "other-id", "v" to "2"
        ).forEach { change ->
            val result = valid(parser.parse(httpsInput(signedQuery() + change)))
            assertFalse("$change should not verify", result.scoreVerified)
            assertNull(result.link.challengerScore)
        }
    }

    @Test
    fun `removing the time or the signature drops the score`() {
        listOf(signedQuery() - "time", signedQuery() - "sig", signedQuery() - "v").forEach { query ->
            val result = valid(parser.parse(httpsInput(query)))
            assertFalse(result.scoreVerified)
            assertNull(result.link.challengerScore)
        }
    }

    @Test
    fun `link signed with another key is unverified`() {
        val other = ChallengeLinkParser(ChallengeLinkSigner("another-key-0123456789abcdef".toByteArray()))
        val result = valid(parser.parse(httpsInput(other.encode(scored).toMap())))
        assertFalse(result.scoreVerified)
        assertNull(result.link.challengerScore)
    }

    // Old links

    @Test
    fun `unsigned links from older versions are accepted without the score`() {
        val result = valid(parser.parse(customInput(legacyQuery)))
        assertFalse(result.scoreVerified)
        assertEquals("abc-123", result.link.challengeId)
        assertEquals("Sam", result.link.challengerName)
        assertEquals("capitals", result.link.quizMode)
        assertEquals(QuizCategory.StartingWithLetter('B'), result.category)
        assertNull(result.link.challengerScore)
        assertNull(result.link.challengerTotal)
        assertNull(result.link.challengerTime)
    }

    @Test
    fun `missing mode defaults to countries and missing name to Someone`() {
        val result = valid(parser.parse(customInput(legacyQuery - "mode" - "name")))
        assertEquals("countries", result.link.quizMode)
        assertEquals(ChallengeLinkParser.DEFAULT_NAME, result.link.challengerName)
    }

    // Clamping

    @Test
    fun `numbers are clamped after verification`() {
        val outOfRange = scored.copy(challengerScore = 900, challengerTotal = 500, challengerTime = 999_999)
        val result = valid(parser.parse(httpsInput(signedQuery(outOfRange))))
        assertTrue(result.scoreVerified)
        assertEquals(ChallengeLinkParser.MAX_TOTAL, result.link.challengerTotal)
        assertEquals(ChallengeLinkParser.MAX_TOTAL, result.link.challengerScore)
        assertEquals(ChallengeLinkParser.MAX_TIME_SECONDS, result.link.challengerTime)
    }

    @Test
    fun `negative and zero numbers are clamped`() {
        val low = scored.copy(challengerScore = -5, challengerTotal = 0, challengerTime = -1)
        val result = valid(parser.parse(httpsInput(signedQuery(low))))
        assertEquals(1, result.link.challengerTotal)
        assertEquals(0, result.link.challengerScore)
        assertEquals(0, result.link.challengerTime)
    }

    @Test
    fun `score above total is clamped to total`() {
        val result = valid(parser.parse(httpsInput(signedQuery(scored.copy(challengerScore = 50)))))
        assertEquals(44, result.link.challengerScore)
    }

    // Names

    @Test
    fun `names are cleaned`() {
        mapOf(
            "  Dav  " to "Dav",
            "Da\u0000v\n" to "Dav",
            "A‮evil" to "Aevil",
            "two   words\there" to "two words here",
            "" to "Someone",
            "   " to "Someone",
            "\u0007\u0008" to "Someone",
            "abcdefghijklmnopqrstuvwxyz" to "abcdefghijklmnopqrstuvwx",
            "🌏".repeat(30) to "🌏".repeat(24)
        ).forEach { (raw, expected) ->
            assertEquals("name '$raw'", expected, ChallengeLinkParser.sanitiseName(raw))
        }
    }

    @Test
    fun `truncation never splits a surrogate pair`() {
        val name = "a".repeat(23) + "🌏🌏"
        assertEquals("a".repeat(23) + "🌏", ChallengeLinkParser.sanitiseName(name))
    }

    // Malformed links: every case is Invalid and nothing throws

    @Test
    fun `wrong scheme host or path is invalid`() {
        val q = signedQuery()
        listOf(
            ChallengeLinkInput("http", "geoquiz-app.netlify.app", "/challenge.html", q),
            ChallengeLinkInput("https", "evil.example.com", "/challenge.html", q),
            ChallengeLinkInput("https", "geoquiz-app.netlify.app", "/other.html", q),
            ChallengeLinkInput("https", "geoquiz-app.netlify.app", "/challengeX", q),
            ChallengeLinkInput("https", "geoquiz-app.netlify.app", null, q),
            ChallengeLinkInput("geoquiz", "other", "", q),
            ChallengeLinkInput("geoquiz", "challenge", "/deep/path", q),
            ChallengeLinkInput(null, null, null, q),
            ChallengeLinkInput("javascript", "challenge", "", q)
        ).forEach(::assertInvalid)
    }

    @Test
    fun `malformed queries are invalid`() {
        val base = legacyQuery
        listOf(
            emptyMap(),
            base - "id",
            base - "ct",
            base - "cv",
            base + ("id" to ""),
            base + ("id" to "has space"),
            base + ("id" to "../../etc"),
            base + ("id" to "a".repeat(65)),
            base + ("id" to "id;DROP TABLE"),
            base + ("mode" to "unknown"),
            base + ("mode" to ""),
            base + ("mode" to "COUNTRIES"),
            base + ("ct" to "nonsense"),
            base + ("ct" to ""),
            base + ("cv" to ""),
            base + ("cv" to "BB"),
            base + ("cv" to "b"),
            base + ("cv" to "1"),
            base + ("ct" to "x".repeat(5000)),
            base + ("name" to "n".repeat(10_000)),
            base + ("score" to "abc"),
            base + ("score" to "1.5"),
            base + ("total" to "99999999999999999999"),
            base + ("time" to "1e9"),
            base + ("score" to "")
        ).forEach { assertInvalid(customInput(it)) }
    }

    @Test
    fun `categories are checked strictly`() {
        listOf(
            "all" to "x",
            "region" to "",
            "region" to "<script>",
            "region" to "R".repeat(41),
            "lengthrange" to "5",
            "lengthrange" to "9-3",
            "lengthrange" to "0-0",
            "lengthrange" to "1-99",
            "wordcount" to "0",
            "wordcount" to "12",
            "wordcount" to "x",
            "endsuffix" to "st an",
            "doubleletter" to "x",
            "startletter" to ""
        ).forEach { (ct, cv) ->
            assertInvalid(customInput(legacyQuery + ("ct" to ct) + ("cv" to cv) + ("mode" to "countries")))
        }
    }

    @Test
    fun `category must be offered in the mode`() {
        listOf(
            Triple("flagcolor", "red", "countries"),
            Triple("flagcount", "3", "capitals"),
            Triple("all", "_", "flags"),
            Triple("region", "Europe", "flags"),
            Triple("capitalmatches", "_", "countries"),
            Triple("flagcombo", "white+red", "flags"), // not sorted
            Triple("flagcombo", "red+red", "flags"),
            Triple("flagcombo", "red", "flags"),
            Triple("flagcombo", "a+b+c+d", "flags"),
            Triple("flagcount", "0", "flags"),
            Triple("flagcount", "13", "flags"),
            Triple("flagcount", "-1", "flags"),
            Triple("flagelement", "Coat Of Arms", "flags")
        ).forEach { (ct, cv, mode) ->
            assertInvalid(customInput(legacyQuery + ("ct" to ct) + ("cv" to cv) + ("mode" to mode)))
        }
    }

    @Test
    fun `practice sets are never carried by a challenge link, signed or not`() {
        // 3.5c, D21: a practice quiz is the player's own misses and is never shared.
        listOf("countries", "capitals", "flags").forEach { mode ->
            val practice = scored.copy(categoryType = "practice", categoryValue = "FRA+DEU", quizMode = mode)
            assertInvalid(customInput(signedQuery(practice)))
            assertInvalid(httpsInput(signedQuery(practice)))
            assertInvalid(customInput(legacyQuery + ("ct" to "practice") + ("cv" to "FRA") + ("mode" to mode)))
        }
    }
}
