package com.geoquiz.app.ui.quiz.feedback

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Plays the haptic for an answer. Behind an interface so the quiz screen can be tested. */
fun interface HapticFeedbackPlayer {
    fun play(event: AnswerFeedbackEvent)
}

/**
 * The [HapticFeedbackConstants] value for [event] on Android [sdkInt], or null for no haptic.
 *
 * - Correct: a light confirm tick (`CONFIRM` on API 30+, `KEYBOARD_TAP` before).
 * - Incorrect (including a Hard strike and a wrong Easy pick): a distinct, stronger reject
 *   (`REJECT` on API 30+, `LONG_PRESS` before).
 * - Near miss and already answered: none. Neither costs anything, and a buzz would read as a
 *   wrong answer; the feedback line says what happened.
 *
 * CONFIRM and REJECT are compile-time constants, used only when [sdkInt] is 30 or more.
 */
@SuppressLint("InlinedApi")
fun hapticConstantFor(event: AnswerFeedbackEvent, sdkInt: Int): Int? = when (event) {
    AnswerFeedbackEvent.CORRECT ->
        if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP
    AnswerFeedbackEvent.INCORRECT ->
        if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
    AnswerFeedbackEvent.NEAR_MISS, AnswerFeedbackEvent.ALREADY_ANSWERED -> null
}

/**
 * Haptics through [View.performHapticFeedback], without `FLAG_IGNORE_GLOBAL_SETTING`, so the
 * system "Touch feedback" / "Vibration & haptics" setting is respected. No VIBRATE permission
 * is needed.
 */
class ViewHapticFeedbackPlayer(
    private val view: View,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) : HapticFeedbackPlayer {
    override fun play(event: AnswerFeedbackEvent) {
        val constant = hapticConstantFor(event, sdkInt) ?: return
        view.performHapticFeedback(constant)
    }
}

/** A [ViewHapticFeedbackPlayer] on the current compose view. */
@Composable
fun rememberHapticFeedbackPlayer(): HapticFeedbackPlayer {
    val view = LocalView.current
    return remember(view) { ViewHapticFeedbackPlayer(view) }
}
