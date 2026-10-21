package com.geoquiz.app.ui.mode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.ui.graphics.vector.ImageVector
import com.geoquiz.app.domain.mode.ModeIcon

/** The image for a mode's domain icon key (same icons as the bottom bar tabs). */
val ModeIcon.imageVector: ImageVector
    get() = when (this) {
        ModeIcon.GLOBE -> Icons.Default.Public
        ModeIcon.LANDMARK -> Icons.Default.AccountBalance
        ModeIcon.FLAG -> Icons.Default.Flag
        // Shapes: a country's outline, kept distinct from TERRAIN_FEATURE's hills.
        ModeIcon.SILHOUETTE -> Icons.Default.Category
        // A pin dropped on a map: matches "tap the map" better than a plain location marker.
        ModeIcon.MAP_PIN -> Icons.Default.PinDrop
        // A path from one place to another: matches hopping across borders (task 6.1).
        ModeIcon.BORDER_HOP -> Icons.Default.Route
        // A coin: matches currencies (task 7.1) better than the generic globe.
        ModeIcon.CURRENCY -> Icons.Default.Paid
        // Hills, distinct from SILHOUETTE's shapes: matches rivers/mountains/lakes (task 7.2).
        ModeIcon.TERRAIN_FEATURE -> Icons.Default.Landscape
    }
