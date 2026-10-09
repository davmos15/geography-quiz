package com.geoquiz.app.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * True when decorative motion should be skipped: the system animator duration scale is 0
 * (Developer options "Animator duration scale: off", or Accessibility "Remove animations",
 * which sets it to 0), or animators are disabled for the process.
 */
fun shouldReduceMotion(animatorDurationScale: Float, animatorsEnabled: Boolean): Boolean =
    !animatorsEnabled || animatorDurationScale <= 0f

/** Reads the system settings for [shouldReduceMotion]. */
fun isReducedMotion(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return shouldReduceMotion(scale, ValueAnimator.areAnimatorsEnabled())
}

/**
 * Whether to skip decorative motion (answer pulses and shakes, later celebrations): show the
 * end state without animating. Re-read each time the screen resumes, so a change made in
 * system settings applies when the player comes back.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(isReducedMotion(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduced = isReducedMotion(context) }
    return reduced
}
