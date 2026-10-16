package com.geoquiz.app.ui.share

import android.net.Uri
import com.geoquiz.app.testutil.TestQuizData
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pure share-card builder behind both the text share and [ShareCardRenderer]'s image (8.3).
 * [TestQuizData.completedQuiz] defaults to a classic Countries result; individual tests override
 * just the fields they're exercising.
 */
@RunWith(RobolectricTestRunner::class)
class ShareCardContentTest {

    private val locale = Locale.UK
    private val zone = ZoneId.of("UTC")

    /** Same patterns as strings_share.xml. */
    private val strings = ShareCardStrings(
        titleFormat = "%1\$s: %2\$s",
        dailyTitleFormat = "Daily challenge – %1\$s",
        timeFormat = "%1\$dm %2\$ds",
        percentageFormat = "%1\$d%%",
        metaFormat = "%1\$s · %2\$s"
    )

    @Test
    fun `a classic mode's card carries its challenge link`() {
        val link = Uri.parse("https://geoquiz-app.netlify.app/challenge?id=abc")
        val quiz = TestQuizData.completedQuiz(quizModeId = "countries").copy(
            correct = 150, total = 197, timeSeconds = 192
        )

        val content = ShareCardContent.from(quiz, modeName = "Countries", challengeLink = link, strings = strings, locale = locale, zone = zone)

        assertEquals("Countries: All Countries", content.title)
        assertEquals("150/197", content.score)
        assertEquals(link.toString(), content.link)
    }

    @Test
    fun `a new mode's card falls back to the Play Store link`() {
        val quiz = TestQuizData.completedQuiz(quizModeId = "silhouettes")

        val content = ShareCardContent.from(quiz, modeName = "Country silhouettes", challengeLink = null, strings = strings, locale = locale, zone = zone)

        assertEquals(ShareCardContent.PLAY_STORE_URL, content.link)
        assertTrue(content.link.startsWith("https://play.google.com/"))
    }

    @Test
    fun `the daily challenge uses its own date, not the completion date`() {
        // completedAtMillis below is 2023-11-14 UTC; categoryValue is a different, earlier date -
        // the one that must win, since the round itself is keyed to that device-local date (D4),
        // not to whenever the result happens to be looked at again.
        val quiz = TestQuizData.completedQuiz(
            quizModeId = "daily_challenge",
            categoryType = "daily",
            categoryValue = "2026-01-05"
        ).copy(categoryName = "Today's challenge")

        val content = ShareCardContent.from(quiz, modeName = "Today's challenge", challengeLink = null, strings = strings, locale = locale, zone = zone)

        assertEquals("5 Jan 2026", content.date)
        assertEquals("Daily challenge – 5 Jan 2026", content.title)
    }

    @Test
    fun `a non-daily result uses its completion date`() {
        // 1_700_000_000_000 ms (TestQuizData's default) is 2023-11-14T22:13:20Z.
        val quiz = TestQuizData.completedQuiz(quizModeId = "countries")

        val content = ShareCardContent.from(quiz, modeName = "Countries", challengeLink = null, strings = strings, locale = locale, zone = zone)

        assertEquals("14 Nov 2023", content.date)
    }

    @Test
    fun `an empty quiz scores zero percent, not a crash`() {
        val quiz = TestQuizData.completedQuiz(
            countryCodes = emptyList(),
            answeredCodes = emptyList()
        )

        val content = ShareCardContent.from(quiz, modeName = "Countries", challengeLink = null, strings = strings, locale = locale, zone = zone)

        assertEquals(0, content.percentage)
        assertEquals("0/0", content.score)
    }

    @Test
    fun `time formats as minutes and seconds, including under a minute`() {
        val short = TestQuizData.completedQuiz(quizModeId = "countries").copy(timeSeconds = 9)
        val long = TestQuizData.completedQuiz(quizModeId = "countries").copy(timeSeconds = 192)

        assertEquals("0m 9s", ShareCardContent.from(short, "Countries", null, strings, locale, zone).time)
        assertEquals("3m 12s", ShareCardContent.from(long, "Countries", null, strings, locale, zone).time)
    }

    @Test
    fun `a long category name is kept whole, not truncated`() {
        val longName = "A very long category name that goes on and on and on for quite a while indeed"
        val quiz = TestQuizData.completedQuiz(quizModeId = "countries").copy(categoryName = longName)

        val content = ShareCardContent.from(quiz, modeName = "Countries", challengeLink = null, strings = strings, locale = locale, zone = zone)

        assertTrue(content.title.contains(longName))
    }

    @Test
    fun `a malformed daily date falls back to the completion date instead of crashing`() {
        val quiz = TestQuizData.completedQuiz(
            quizModeId = "daily_challenge",
            categoryType = "daily",
            categoryValue = "2026-13-45"
        )

        val content = ShareCardContent.from(quiz, modeName = "Daily", challengeLink = null, strings = strings, locale = locale, zone = zone)

        assertEquals("14 Nov 2023", content.date)
    }

    @Test
    fun `the card prints a short link, the text keeps the full one`() {
        val signed = Uri.parse(
            "https://geoquiz-app.netlify.app/challenge?id=0f8c&ct=region&cv=Europe&n=Sam&m=capitals&s=40&t=44&tm=312&v=1&sig=abcdef0123456789"
        )
        val quiz = TestQuizData.completedQuiz(quizModeId = "capitals")

        val classic = ShareCardContent.from(quiz, "Capitals", signed, strings, locale, zone)
        val newMode = ShareCardContent.from(quiz, "Capitals", null, strings, locale, zone)

        assertEquals(signed.toString(), classic.link)
        assertEquals("geoquiz-app.netlify.app/challenge", classic.displayLink)
        assertEquals("play.google.com/store/apps/details?id=com.geoquiz.app", newMode.displayLink)
    }

    @Test
    fun `percentage and meta line come from the supplied patterns`() {
        val quiz = TestQuizData.completedQuiz(quizModeId = "countries").copy(correct = 150, total = 197, timeSeconds = 192)

        val content = ShareCardContent.from(quiz, "Countries", null, strings, locale, zone)

        assertEquals("76%", content.percentageText)
        assertEquals("14 Nov 2023 · 3m 12s", content.metaLine)
    }
}
