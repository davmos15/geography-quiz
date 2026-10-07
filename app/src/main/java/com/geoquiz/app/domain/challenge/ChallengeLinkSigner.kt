package com.geoquiz.app.domain.challenge

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The challenge link fields exactly as they appear in the link's query string.
 * Signing works on these raw strings, so a verifier compares byte for byte what was sent.
 * A null field means the parameter is absent, which signs differently from an empty value.
 */
data class ChallengeLinkFields(
    val id: String?,
    val categoryType: String?,
    val categoryValue: String?,
    val name: String?,
    val mode: String?,
    val score: String?,
    val total: String?,
    val time: String?
)

/**
 * HMAC-SHA256 over a canonical, length-prefixed encoding of every challenge field.
 *
 * The key ships inside the APK, so this deters casual score editing in a shared link;
 * it is not a defence against someone who extracts the key from the app.
 */
class ChallengeLinkSigner(key: ByteArray) {

    private val keySpec: SecretKeySpec

    init {
        require(key.size >= MIN_KEY_BYTES) { "Challenge HMAC key is too short" }
        keySpec = SecretKeySpec(key.copyOf(), ALGORITHM)
    }

    /** Signature for [fields], base64url without padding. */
    fun sign(fields: ChallengeLinkFields): String =
        BASE64_URL_ENCODER.encodeToString(mac(fields))

    /** True only if [signature] is a well-formed base64url HMAC of [fields] under this key. */
    fun verify(fields: ChallengeLinkFields, signature: String?): Boolean {
        if (signature.isNullOrEmpty() || signature.length > MAX_SIGNATURE_CHARS) return false
        val given = try {
            BASE64_URL_DECODER.decode(signature)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return MessageDigest.isEqual(given, mac(fields))
    }

    private fun mac(fields: ChallengeLinkFields): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(keySpec)
        return mac.doFinal(canonical(fields).toByteArray(Charsets.UTF_8))
    }

    companion object {
        /** Value of the `v` query parameter for links signed by this class. */
        const val VERSION = "1"

        private const val ALGORITHM = "HmacSHA256"
        private const val MIN_KEY_BYTES = 16
        private const val MAX_SIGNATURE_CHARS = 64
        private val BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val BASE64_URL_DECODER = Base64.getUrlDecoder()

        /**
         * `geoquiz-challenge|v1|` followed by each field as `<length>:<value>;` in a fixed
         * order, or `-;` when absent. Length prefixes make the encoding unambiguous whatever
         * characters a field contains.
         */
        fun canonical(fields: ChallengeLinkFields): String = buildString {
            append("geoquiz-challenge|v").append(VERSION).append('|')
            listOf(
                fields.id, fields.categoryType, fields.categoryValue, fields.name,
                fields.mode, fields.score, fields.total, fields.time
            ).forEach { value ->
                if (value == null) {
                    append("-;")
                } else {
                    append(value.length).append(':').append(value).append(';')
                }
            }
        }

        /** Builds a signer from a base64url key (padding optional). */
        fun fromBase64Url(key: String): ChallengeLinkSigner =
            ChallengeLinkSigner(BASE64_URL_DECODER.decode(key.trim()))
    }
}
