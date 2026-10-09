package com.geoquiz.app.domain.model

import android.net.Uri
import com.geoquiz.app.domain.challenge.ChallengeLinkInput
import com.geoquiz.app.domain.challenge.ChallengeLinkParseResult
import com.geoquiz.app.domain.challenge.ChallengeLinkParser
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner

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
    /** Signed deep link URI for the app's intent filter (geoquiz://challenge) */
    fun toUri(signer: ChallengeLinkSigner): Uri =
        Uri.Builder()
            .scheme(ChallengeLinkParser.CUSTOM_SCHEME)
            .authority(ChallengeLinkParser.CUSTOM_HOST)
            .appendParams(signer)
            .build()

    /**
     * Signed HTTPS URL for sharing via messaging apps. It opens the app directly through a
     * verified App Link, or the web page that hands over to the app.
     */
    fun toShareUrl(signer: ChallengeLinkSigner): Uri =
        Uri.Builder()
            .scheme(ChallengeLinkParser.HTTPS)
            .authority(ChallengeLinkParser.WEB_HOST)
            .path(ChallengeLinkParser.WEB_SHARE_PATH)
            .appendParams(signer)
            .build()

    private fun Uri.Builder.appendParams(signer: ChallengeLinkSigner): Uri.Builder = apply {
        ChallengeLinkParser(signer).encode(this@ChallengeDeepLink).forEach { (key, value) ->
            appendQueryParameter(key, value)
        }
    }

    companion object {
        /** Validates an incoming link; never throws. */
        fun parse(uri: Uri, signer: ChallengeLinkSigner): ChallengeLinkParseResult =
            ChallengeLinkParser(signer).parse(uri.toChallengeLinkInput())

        /** The first value of each query parameter (later duplicates are ignored). */
        private fun Uri.toChallengeLinkInput(): ChallengeLinkInput {
            val query = try {
                buildMap {
                    for (name in queryParameterNames) {
                        getQueryParameter(name)?.let { put(name, it) }
                    }
                }
            } catch (_: RuntimeException) {
                emptyMap() // opaque or malformed URI: the parser reports it as invalid
            }
            return ChallengeLinkInput(scheme = scheme, host = host, path = path, query = query)
        }
    }
}
