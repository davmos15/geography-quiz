package com.geoquiz.app.domain.model

import android.net.Uri

data class ChallengeDeepLink(
    val challengeId: String,
    val categoryType: String,
    val categoryValue: String,
    val challengerName: String,
    val challengerScore: Int?,
    val challengerTotal: Int?,
    val challengerTime: Int?,
    val quizMode: String = "countries"
) {
    /** Deep link URI for the app's intent filter (geoquiz://challenge) */
    fun toUri(): Uri {
        return Uri.Builder()
            .scheme("geoquiz")
            .authority("challenge")
            .appendQueryParameter("id", challengeId)
            .appendQueryParameter("ct", categoryType)
            .appendQueryParameter("cv", categoryValue)
            .appendQueryParameter("name", challengerName)
            .appendQueryParameter("mode", quizMode)
            .apply {
                challengerScore?.let { appendQueryParameter("score", it.toString()) }
                challengerTotal?.let { appendQueryParameter("total", it.toString()) }
                challengerTime?.let { appendQueryParameter("time", it.toString()) }
            }
            .build()
    }

    /** HTTPS URL for sharing via messaging apps (redirects to the app) */
    fun toShareUrl(): Uri {
        return Uri.Builder()
            .scheme("https")
            .authority("geoquiz-app.netlify.app")
            .path("/challenge.html")
            .appendQueryParameter("id", challengeId)
            .appendQueryParameter("ct", categoryType)
            .appendQueryParameter("cv", categoryValue)
            .appendQueryParameter("name", challengerName)
            .appendQueryParameter("mode", quizMode)
            .apply {
                challengerScore?.let { appendQueryParameter("score", it.toString()) }
                challengerTotal?.let { appendQueryParameter("total", it.toString()) }
                challengerTime?.let { appendQueryParameter("time", it.toString()) }
            }
            .build()
    }

    companion object {
        fun fromUri(uri: Uri): ChallengeDeepLink? {
            // Accept both geoquiz://challenge and https://geoquiz-app.netlify.app/challenge.html
            val isCustomScheme = uri.scheme == "geoquiz" && uri.host == "challenge"
            val isHttpsScheme = uri.scheme == "https" && uri.host == "geoquiz-app.netlify.app"
            if (!isCustomScheme && !isHttpsScheme) return null

            val id = uri.getQueryParameter("id") ?: return null
            val ct = uri.getQueryParameter("ct") ?: return null
            val cv = uri.getQueryParameter("cv") ?: return null
            val name = uri.getQueryParameter("name") ?: "Someone"

            return ChallengeDeepLink(
                challengeId = id,
                categoryType = ct,
                categoryValue = cv,
                challengerName = name,
                challengerScore = uri.getQueryParameter("score")?.toIntOrNull(),
                challengerTotal = uri.getQueryParameter("total")?.toIntOrNull(),
                challengerTime = uri.getQueryParameter("time")?.toIntOrNull(),
                quizMode = uri.getQueryParameter("mode") ?: "countries"
            )
        }
    }
}
