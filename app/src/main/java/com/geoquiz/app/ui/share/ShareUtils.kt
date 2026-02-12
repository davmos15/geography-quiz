package com.geoquiz.app.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri

object ShareUtils {
    fun shareResults(
        context: Context,
        categoryName: String,
        score: Int,
        total: Int,
        time: Int,
        deepLink: Uri
    ) {
        val minutes = time / 60
        val seconds = time % 60
        val percentage = if (total > 0) (score.toDouble() / total * 100).toInt() else 0
        val text = "I named $score/$total countries ($percentage%) on \"$categoryName\" " +
            "in ${minutes}m ${seconds}s! Can you beat me?\n$deepLink"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Results"))
    }

    fun shareChallenge(
        context: Context,
        categoryName: String,
        deepLink: Uri
    ) {
        val text = "I challenge you to \"$categoryName\"! Can you name them all?\n$deepLink"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Challenge a Friend"))
    }
}
