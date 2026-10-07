package com.geoquiz.app.domain.challenge

import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode

/** The parts of an incoming link the parser needs; built from an `android.net.Uri` in the app. */
data class ChallengeLinkInput(
    val scheme: String?,
    val host: String?,
    val path: String?,
    val query: Map<String, String>
)

sealed interface ChallengeLinkParseResult {
    /**
     * A usable challenge. [scoreVerified] is false when the link had no signature (links from
     * v2.7.2 and earlier) or a bad one; the challenger's score, total and time are then null.
     */
    data class Valid(
        val link: ChallengeDeepLink,
        val category: QuizCategory,
        val scoreVerified: Boolean
    ) : ChallengeLinkParseResult

    /** The link can't be used. [reason] is for logs and tests, never shown to the player. */
    data class Invalid(val reason: String) : ChallengeLinkParseResult
}

/**
 * Parses and signs challenge links. Every field from a link is untrusted: it is checked,
 * clamped or rejected here, and [parse] never throws.
 */
class ChallengeLinkParser(private val signer: ChallengeLinkSigner) {

    fun parse(input: ChallengeLinkInput): ChallengeLinkParseResult = try {
        parseOrThrow(input)
    } catch (e: InvalidLink) {
        ChallengeLinkParseResult.Invalid(e.message ?: "invalid")
    } catch (e: RuntimeException) {
        ChallengeLinkParseResult.Invalid("unexpected: ${e.javaClass.simpleName}")
    }

    /** Query parameters for [link] in share order, including `v` and `sig`. */
    fun encode(link: ChallengeDeepLink): List<Pair<String, String>> {
        val hasScore = link.challengerScore != null && link.challengerTotal != null
        val fields = ChallengeLinkFields(
            id = link.challengeId,
            categoryType = link.categoryType,
            categoryValue = link.categoryValue,
            name = sanitiseName(link.challengerName),
            mode = link.quizMode,
            score = if (hasScore) link.challengerScore.toString() else null,
            total = if (hasScore) link.challengerTotal.toString() else null,
            time = if (hasScore) link.challengerTime?.toString() else null
        )
        return buildList {
            add(PARAM_ID to fields.id!!)
            add(PARAM_CATEGORY_TYPE to fields.categoryType!!)
            add(PARAM_CATEGORY_VALUE to fields.categoryValue!!)
            add(PARAM_NAME to fields.name!!)
            add(PARAM_MODE to fields.mode!!)
            fields.score?.let { add(PARAM_SCORE to it) }
            fields.total?.let { add(PARAM_TOTAL to it) }
            fields.time?.let { add(PARAM_TIME to it) }
            add(PARAM_VERSION to ChallengeLinkSigner.VERSION)
            add(PARAM_SIGNATURE to signer.sign(fields))
        }
    }

    private class InvalidLink(reason: String) : Exception(reason)

    private fun parseOrThrow(input: ChallengeLinkInput): ChallengeLinkParseResult.Valid {
        checkOrigin(input)
        val q = input.query

        val id = q[PARAM_ID] ?: throw InvalidLink("missing id")
        if (!ID_PATTERN.matches(id)) throw InvalidLink("bad id")

        val ct = q[PARAM_CATEGORY_TYPE] ?: throw InvalidLink("missing ct")
        val cv = q[PARAM_CATEGORY_VALUE] ?: throw InvalidLink("missing cv")
        if (ct.length > MAX_ROUTE_CHARS || cv.length > MAX_ROUTE_CHARS) throw InvalidLink("category too long")
        val category = QuizCategory.fromRouteOrNull(ct, cv) ?: throw InvalidLink("unknown category")

        val rawMode = q[PARAM_MODE]
        val mode = if (rawMode == null) {
            QuizMode.COUNTRIES // links made before the mode parameter existed
        } else {
            QuizMode.entries.find { it.id == rawMode } ?: throw InvalidLink("unknown mode")
        }
        if (!category.isOfferedIn(mode)) throw InvalidLink("category not offered in mode")

        val rawName = q[PARAM_NAME]
        if (rawName != null && rawName.length > MAX_RAW_NAME_CHARS) throw InvalidLink("name too long")

        val score = parseNumber(q[PARAM_SCORE], "score")
        val total = parseNumber(q[PARAM_TOTAL], "total")
        val time = parseNumber(q[PARAM_TIME], "time")

        val fields = ChallengeLinkFields(
            id = id, categoryType = ct, categoryValue = cv, name = rawName, mode = rawMode,
            score = q[PARAM_SCORE], total = q[PARAM_TOTAL], time = q[PARAM_TIME]
        )
        val verified = q[PARAM_VERSION] == ChallengeLinkSigner.VERSION &&
            signer.verify(fields, q[PARAM_SIGNATURE])

        val keepScore = verified && score != null && total != null
        val clampedTotal = total?.coerceIn(1L, MAX_TOTAL.toLong())?.toInt()
        val link = ChallengeDeepLink(
            challengeId = id,
            categoryType = category.typeKey,
            categoryValue = category.valueKey,
            challengerName = sanitiseName(rawName),
            challengerScore = if (keepScore) score!!.coerceIn(0L, clampedTotal!!.toLong()).toInt() else null,
            challengerTotal = if (keepScore) clampedTotal else null,
            challengerTime = if (keepScore) time?.coerceIn(0L, MAX_TIME_SECONDS.toLong())?.toInt() else null,
            quizMode = mode.id
        )
        return ChallengeLinkParseResult.Valid(link, category, scoreVerified = verified)
    }

    private fun checkOrigin(input: ChallengeLinkInput) {
        val scheme = input.scheme?.lowercase()
        val host = input.host?.lowercase()
        val path = input.path.orEmpty()
        val ok = when (scheme) {
            CUSTOM_SCHEME -> host == CUSTOM_HOST && (path.isEmpty() || path == "/")
            HTTPS -> host == WEB_HOST && path in WEB_PATHS
            else -> false
        }
        if (!ok) throw InvalidLink("not a challenge link")
    }

    /** Null when absent; throws when present but not a plain integer. */
    private fun parseNumber(raw: String?, field: String): Long? {
        if (raw == null) return null
        if (!NUMBER_PATTERN.matches(raw)) throw InvalidLink("bad $field")
        return raw.toLong()
    }

    companion object {
        const val PARAM_ID = "id"
        const val PARAM_CATEGORY_TYPE = "ct"
        const val PARAM_CATEGORY_VALUE = "cv"
        const val PARAM_NAME = "name"
        const val PARAM_MODE = "mode"
        const val PARAM_SCORE = "score"
        const val PARAM_TOTAL = "total"
        const val PARAM_TIME = "time"
        const val PARAM_VERSION = "v"
        const val PARAM_SIGNATURE = "sig"

        const val CUSTOM_SCHEME = "geoquiz"
        const val CUSTOM_HOST = "challenge"
        const val HTTPS = "https"
        const val WEB_HOST = "geoquiz-app.netlify.app"
        const val WEB_SHARE_PATH = "/challenge.html"
        /** Netlify serves the page with and without `.html`; the App Link filter matches both. */
        private val WEB_PATHS = setOf(WEB_SHARE_PATH, "/challenge", "/challenge/")

        /** Stored as data in the challenges table, so it stays a code constant. */
        const val DEFAULT_NAME = "Someone"
        const val MAX_NAME_CODE_POINTS = 24
        /** The quiz covers 197 countries; no category can have more. */
        const val MAX_TOTAL = 197
        const val MAX_TIME_SECONDS = 86_400

        private const val MAX_ROUTE_CHARS = 100
        private const val MAX_RAW_NAME_CHARS = 512
        private val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
        private val NUMBER_PATTERN = Regex("-?\\d{1,9}")
        private val WHITESPACE = Regex("\\s+")

        /**
         * Trims, drops control and formatting characters (including bidi overrides),
         * collapses whitespace and keeps at most [MAX_NAME_CODE_POINTS] code points.
         * Blank names become [DEFAULT_NAME].
         */
        fun sanitiseName(raw: String?): String {
            if (raw == null) return DEFAULT_NAME
            val cleaned = buildString {
                var i = 0
                while (i < raw.length) {
                    val cp = raw.codePointAt(i)
                    val type = Character.getType(cp)
                    val drop = Character.isISOControl(cp) ||
                        type == Character.FORMAT.toInt() ||
                        type == Character.SURROGATE.toInt() ||
                        type == Character.PRIVATE_USE.toInt() ||
                        type == Character.UNASSIGNED.toInt() ||
                        type == Character.LINE_SEPARATOR.toInt() ||
                        type == Character.PARAGRAPH_SEPARATOR.toInt()
                    when {
                        Character.isWhitespace(cp) -> append(' ') // tabs and newlines too
                        !drop -> appendCodePoint(cp)
                    }
                    i += Character.charCount(cp)
                }
            }.replace(WHITESPACE, " ").trim()
            if (cleaned.isEmpty()) return DEFAULT_NAME
            val limit = cleaned.codePointCount(0, cleaned.length)
            if (limit <= MAX_NAME_CODE_POINTS) return cleaned
            val end = cleaned.offsetByCodePoints(0, MAX_NAME_CODE_POINTS)
            return cleaned.substring(0, end).trim()
        }
    }
}
