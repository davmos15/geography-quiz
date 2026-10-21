package com.geoquiz.app.ui.quiz.feedback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.geoquiz.app.ui.components.rememberReducedMotion
import com.geoquiz.app.ui.theme.geoColors
import kotlin.random.Random

/** Total length of the Results perfect-score confetti burst, in ms. */
private const val CONFETTI_DURATION_MILLIS = 2200

/** A few dozen particles reads as a burst without being a performance concern. */
private const val PARTICLE_COUNT = 36

/**
 * One particle's fixed, randomised path, chosen once per burst and then driven purely by a
 * 0..1 [progress][Animatable]: falls with a gravity-like ease-in, drifts sideways, spins and
 * fades out.
 */
private data class ConfettiParticle(
    val startXFraction: Float,
    val fallFraction: Float,
    val driftFraction: Float,
    val sizeDp: Float,
    val colorIndex: Int,
    val rotationDegrees: Float,
    /** Fraction of the whole burst before this particle starts, so they don't all move in lockstep. */
    val startFraction: Float
)

private fun randomParticles(colorCount: Int): List<ConfettiParticle> {
    val random = Random(System.nanoTime())
    return List(PARTICLE_COUNT) {
        ConfettiParticle(
            startXFraction = random.nextFloat(),
            fallFraction = 0.55f + random.nextFloat() * 0.45f,
            driftFraction = (random.nextFloat() - 0.5f) * 0.3f,
            sizeDp = 5f + random.nextFloat() * 5f,
            colorIndex = random.nextInt(colorCount),
            rotationDegrees = (random.nextFloat() - 0.5f) * 720f,
            startFraction = random.nextFloat() * 0.3f
        )
    }
}

/**
 * An original falling-confetti burst (task 8.5) drawn with Compose [Canvas] only: a few dozen
 * small coloured rectangles, in [com.geoquiz.app.ui.theme.GeoColors] tones only (never a
 * hard-coded colour), that fall, drift, spin and fade out over about two seconds. No animation
 * library and no bundled artwork.
 *
 * Skipped entirely (nothing drawn, no animation started) when [rememberReducedMotion] is on: the
 * same "skip, don't shorten" precedent as the answer pulse/shake in `FeedbackMotion`.
 *
 * Starts its one burst as soon as it enters composition (a plain [remember], not keyed on
 * anything): callers that want it to run exactly once per result, not on every recomposition or
 * screen rotation, must only compose this inside a condition that itself only becomes true once
 * per result, as `ResultsScreen` does for a perfect score.
 */
@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier) {
    if (rememberReducedMotion()) return

    val colors = with(MaterialTheme.geoColors) {
        listOf(correct, wrong, nearMiss, tierGold, mapStart, mapEnd)
    }
    val particles = remember { randomParticles(colors.size) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(CONFETTI_DURATION_MILLIS, easing = LinearEasing))
    }

    // progress.value is read inside the Canvas draw lambda below, not here: reading an
    // Animatable's value during composition (rather than in the draw phase) would recompose this
    // whole composable on every animation frame instead of only re-drawing it (review fix).
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        particles.forEach { particle ->
            if (t < particle.startFraction) return@forEach
            val span = (1f - particle.startFraction).coerceAtLeast(0.01f)
            val localT = ((t - particle.startFraction) / span).coerceIn(0f, 1f)
            val alpha = 1f - localT
            if (alpha <= 0f) return@forEach

            val x = (particle.startXFraction + particle.driftFraction * localT) * size.width
            // localT * localT: a simple gravity-like ease-in fall, not a constant speed.
            val y = particle.fallFraction * localT * localT * size.height
            val particleSizePx = particle.sizeDp.dp.toPx()
            rotate(degrees = particle.rotationDegrees * localT, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[particle.colorIndex].copy(alpha = alpha),
                    topLeft = Offset(x - particleSizePx / 2f, y - particleSizePx / 2f),
                    size = Size(particleSizePx, particleSizePx)
                )
            }
        }
    }
}
