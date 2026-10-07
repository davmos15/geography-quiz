package com.geoquiz.app.ui.quiz.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Size for an icon that sits inline with text: [base] at 100% font size, scaled with the
 * system font scale (including Android 14's non-linear scaling) like the text next to it.
 */
@Composable
fun inlineIconSize(base: Dp): Dp = with(LocalDensity.current) { base.value.sp.toDp() }

/** Icon + text, never colour alone. The icon is decorative: the text says it. */
@Composable
internal fun FeedbackLine(
    icon: ImageVector,
    text: String,
    color: Color,
    style: TextStyle,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(inlineIconSize(iconSize))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, color = color, style = style)
    }
}
