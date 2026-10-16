package com.geoquiz.app.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.geoquiz.app.R

object ShareUtils {
    /**
     * The text shared for a finished result: a classic mode's carries a challenge link,
     * a new (non-classic) mode's does not ([deepLink] is null, D35) - it gets [date] (the share
     * card's own [ShareCardContent.date], when the caller has one) and the Play Store link
     * instead (8.3), since it has nothing else that identifies which result this is. Pure, so it
     * is tested without a [Context]. [itemsNoun] is the mode's own plural, already resolved by
     * the caller (e.g. `stringResource(modeSpec.labels.itemsNoun)`), so this works for any
     * registered mode.
     */
    fun buildResultsShareText(
        categoryName: String,
        itemsNoun: String,
        score: Int,
        total: Int,
        time: Int,
        deepLink: Uri?,
        date: String? = null
    ): String {
        val minutes = time / 60
        val seconds = time % 60
        val percentage = if (total > 0) (score.toDouble() / total * 100).toInt() else 0
        val sentence = "I named $score/$total $itemsNoun ($percentage%) on \"$categoryName\" " +
            "in ${minutes}m ${seconds}s! Can you beat me?"
        return if (deepLink != null) {
            "$sentence\n$deepLink"
        } else {
            buildString {
                append(sentence)
                if (date != null) {
                    append('\n')
                    append(date)
                }
                append('\n')
                append(ShareCardContent.PLAY_STORE_URL)
            }
        }
    }

    fun shareResults(
        context: Context,
        categoryName: String,
        itemsNoun: String,
        score: Int,
        total: Int,
        time: Int,
        /** Challenge link to append, or null for modes without challenge links (D35). */
        deepLink: Uri?,
        /** The result's own date (8.3's [ShareCardContent.date]), for a mode with no [deepLink]. */
        date: String? = null
    ) {
        val text = buildResultsShareText(categoryName, itemsNoun, score, total, time, deepLink, date)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_chooser_title_text))
        )
    }

    /** The text shared for an outgoing challenge, always with its link. Pure, as above. */
    fun buildChallengeShareText(categoryName: String, deepLink: Uri): String =
        "I challenge you to \"$categoryName\"! Can you name them all?\n$deepLink"

    fun shareChallenge(
        context: Context,
        categoryName: String,
        deepLink: Uri
    ) {
        val text = buildChallengeShareText(categoryName, deepLink)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_chooser_title_challenge))
        )
    }
}
