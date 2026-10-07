package com.geoquiz.app.ui.quiz.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.geoquiz.app.ui.components.A11yText
import com.geoquiz.app.ui.components.a11yResources
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
    val description = A11yText.timer(a11yResources(), displaySeconds)

    Text(
        text = formatted,
        style = MaterialTheme.typography.titleMedium,
        color = if (isLow) Red40 else MaterialTheme.colorScheme.onSurface,
        // Deliberately not a live region: it changes every second, so TalkBack reads it
        // only when the user moves focus to it ("Time 3 minutes 12 seconds").
        modifier = modifier.clearAndSetSemantics { contentDescription = description }
    )
}
