package com.geoquiz.app.ui.quiz.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.geoquiz.app.ui.theme.Red40

@Composable
fun TimerDisplay(
    elapsedSeconds: Int,
    timerSeconds: Int?,
    modifier: Modifier = Modifier
) {
    val displaySeconds = if (timerSeconds != null) {
        (timerSeconds - elapsedSeconds).coerceAtLeast(0)
    } else {
        elapsedSeconds
    }

    val minutes = displaySeconds / 60
    val seconds = displaySeconds % 60
    val formatted = String.format("%02d:%02d", minutes, seconds)

    val isLow = timerSeconds != null && (timerSeconds - elapsedSeconds) < 60

    Text(
        text = formatted,
        style = MaterialTheme.typography.titleMedium,
        color = if (isLow) Red40 else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}
