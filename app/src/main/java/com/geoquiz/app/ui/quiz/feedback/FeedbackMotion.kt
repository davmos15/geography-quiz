package com.geoquiz.app.ui.quiz.feedback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Total length of the correct-answer pulse, in ms (3.3: at most 300). */
const val PULSE_MILLIS = 260

/** Total length of the wrong-answer shake, in ms (3.3: at most 300). */
const val SHAKE_MILLIS = 280

/**
 * The short answer animations (3.3): a pulse after a correct answer and a horizontal shake after
 * a wrong one. Both draw-only ([graphicsLayer]), so layout and TalkBack are unaffected.
 */
@Stable
class FeedbackMotion internal constructor() {
    internal val scale = Animatable(1f)
    internal val shakeDp = Animatable(0f)
}

/**
 * Starts the animation for each new [signal]. With [reducedMotion] nothing moves: the feedback
 * line just changes (see `rememberReducedMotion`). Near miss and already answered do not move.
 */
@Composable
fun rememberFeedbackMotion(signal: FeedbackSignal, reducedMotion: Boolean): FeedbackMotion {
    val motion = remember { FeedbackMotion() }
    LaunchedEffect(signal.sequence) {
        motion.scale.snapTo(1f)
        motion.shakeDp.snapTo(0f)
        if (reducedMotion) return@LaunchedEffect
        when (signal.event) {
            AnswerFeedbackEvent.CORRECT -> motion.scale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = PULSE_MILLIS
                    1.1f at 100 using FastOutSlowInEasing
                }
            )
            AnswerFeedbackEvent.INCORRECT -> motion.shakeDp.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = SHAKE_MILLIS
                    -10f at 40
                    10f at 90
                    -7f at 140
                    5f at 190
                    -2f at 240
                }
            )
            AnswerFeedbackEvent.NEAR_MISS, AnswerFeedbackEvent.ALREADY_ANSWERED, null -> Unit
        }
    }
    return motion
}

/** Grows briefly after a correct answer. */
fun Modifier.pulseOnCorrect(motion: FeedbackMotion?): Modifier =
    if (motion == null) this else graphicsLayer {
        val scale = motion.scale.value
        scaleX = scale
        scaleY = scale
    }

/** Shakes sideways after a wrong answer. */
fun Modifier.shakeOnWrong(motion: FeedbackMotion?): Modifier =
    if (motion == null) this else graphicsLayer {
        translationX = motion.shakeDp.value.dp.toPx()
    }
