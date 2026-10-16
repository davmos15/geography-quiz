package com.geoquiz.app.ui.share

import android.net.Uri
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.model.CompletedQuiz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The user-facing wording [ShareCardContent.from] fills in, resolved by the caller from
 * `strings_share.xml` ([shareCardStrings] in a composable) so no English lives in Kotlin.
 * Each is a `String.format` pattern.
 */
data class ShareCardStrings(
    /** "%1$s: %2$s" - mode name, category name. */
    val titleFormat: String,
    /** "Daily challenge – %1$s" - the round's date. */
    val dailyTitleFormat: String,
    /** "%1$dm %2$ds" - minutes, seconds. */
    val timeFormat: String,
    /** "%1$d%%" - percentage. */
    val percentageFormat: String,
    /** "%1$s · %2$s" - date, time. */
    val metaFormat: String
)

/**
 * Pure content for an original share card (8.3), built once from a finished [CompletedQuiz] so
 * both the plain-text share (see [ShareUtils]) and the image card ([ShareCardRenderer]) read the
 * same fields, with no [android.content.Context] or `@Composable` needed - so this is unit
 * tested directly.
 *
 * @property title the mode name and category, e.g. "Countries: All Countries"; for the daily
 *   challenge "Daily challenge – <date>" (not "Today's", which reads wrongly when an older
 *   result is shared).
 * @property date a locale-aware medium date, e.g. "16 Oct 2026".
 * @property score "correct/total".
 * @property percentage 0 to 100 (rounded down), 0 for an empty quiz.
 * @property time e.g. "3m 12s"; [timeSeconds] is kept for a spoken version.
 * @property link the full link for the share text: the signed challenge link for a classic mode,
 *   or the Play Store listing for a new (non-classic) mode, which has none (D35).
 * @property displayLink the short form printed on the image (D60): a signed challenge link is
 *   ~250 characters that nobody can tap or type from a picture, so the card shows only its host
 *   and path; the full link travels in the share text alongside the image.
 */
data class ShareCardContent(
    val title: String,
    val date: String,
    val score: String,
    val percentage: Int,
    val percentageText: String,
    val time: String,
    val timeSeconds: Int,
    val metaLine: String,
    val link: String,
    val displayLink: String
) {
    companion object {
        /**
         * Always the release application id, even from a debug build: every build shares the
         * same store listing.
         */
        const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.geoquiz.app"

        /**
         * @param modeName the mode's own label ([com.geoquiz.app.domain.mode.ModeLabels.name]),
         *   already resolved by the caller (e.g. `stringResource(modeSpec.labels.name)`).
         * @param challengeLink the signed challenge link for a classic mode, or null for a new
         *   (non-classic) mode (D35): [ShareCardContent.link] then falls back to [PLAY_STORE_URL].
         * @param locale/[zone] only ever overridden by tests, for a deterministic date; the app
         *   itself always uses the device's own locale and zone.
         */
        fun from(
            result: CompletedQuiz,
            modeName: String,
            challengeLink: Uri?,
            strings: ShareCardStrings,
            locale: Locale = Locale.getDefault(),
            zone: ZoneId = ZoneId.systemDefault()
        ): ShareCardContent {
            // The daily challenge's categoryValue is that round's own "yyyy-MM-dd"
            // (QuizCategory.DailyChallenge, D4), which is the date to show even if the result is
            // shared later. Anything unparseable falls back to the completion date, never a crash.
            val completedDate = Instant.ofEpochMilli(result.completedAtMillis).atZone(zone).toLocalDate()
            val isDaily = result.quizModeId == DailyChallengeGameMode.MODE_ID
            val localDate = if (isDaily) {
                runCatching { LocalDate.parse(result.categoryValue) }.getOrDefault(completedDate)
            } else {
                completedDate
            }
            val dateText = localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
            val title = if (isDaily) {
                String.format(locale, strings.dailyTitleFormat, dateText)
            } else {
                String.format(locale, strings.titleFormat, modeName, result.categoryName)
            }
            val percentage = if (result.total > 0) {
                (result.correct.toDouble() / result.total * 100).toInt()
            } else {
                0
            }
            val time = String.format(locale, strings.timeFormat, result.timeSeconds / 60, result.timeSeconds % 60)
            val link = challengeLink?.toString() ?: PLAY_STORE_URL
            return ShareCardContent(
                title = title,
                date = dateText,
                score = "${result.correct}/${result.total}",
                percentage = percentage,
                percentageText = String.format(locale, strings.percentageFormat, percentage),
                time = time,
                timeSeconds = result.timeSeconds,
                metaLine = String.format(locale, strings.metaFormat, dateText, time),
                link = link,
                displayLink = displayLink(challengeLink)
            )
        }

        /** Host and path of a challenge link (no query), or the store URL without its scheme. */
        internal fun displayLink(challengeLink: Uri?): String {
            val host = challengeLink?.host
            if (challengeLink == null || host.isNullOrEmpty()) return PLAY_STORE_URL.removePrefix("https://")
            return host + (challengeLink.path ?: "")
        }
    }
}
