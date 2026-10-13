package com.geoquiz.app.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri

object ShareUtils {
    /**
     * The text shared for a finished result: a classic mode's carries a challenge link,
     * a new (non-classic) mode's does not ([deepLink] is null, D35). Pure, so it is tested
     * without a [Context]. [itemsNoun] is the mode's own plural, already resolved by the caller
     * (e.g. `stringResource(modeSpec.labels.itemsNoun)`), so this works for any registered mode.
     */
    fun buildResultsShareText(
        categoryName: String,
        itemsNoun: String,
        score: Int,
        total: Int,
        time: Int,
        deepLink: Uri?
    ): String {
        val minutes = time / 60
        val seconds = time % 60
        val percentage = if (total > 0) (score.toDouble() / total * 100).toInt() else 0
        return "I named $score/$total $itemsNoun ($percentage%) on \"$categoryName\" " +
            "in ${minutes}m ${seconds}s! Can you beat me?" + (deepLink?.let { "\n$it" } ?: "")
    }

    fun shareResults(
        context: Context,
        categoryName: String,
        itemsNoun: String,
        score: Int,
        total: Int,
        time: Int,
        /** Challenge link to append, or null for modes without challenge links (D35). */
        deepLink: Uri?
    ) {
        val text = buildResultsShareText(categoryName, itemsNoun, score, total, time, deepLink)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Results"))
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
        context.startActivity(Intent.createChooser(intent, "Challenge a Friend"))
    }
}
