package com.geoquiz.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner shapes for the app. Values match the Material 3 baseline, so components look the same
 * as before; new UI should take shapes from `MaterialTheme.shapes` rather than literal radii.
 * Cards use [Shapes.medium] (12 dp), the radius existing cards already set by hand.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
