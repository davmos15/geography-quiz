package com.geoquiz.app.ui.mode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.graphics.vector.ImageVector
import com.geoquiz.app.domain.mode.ModeIcon

/** The image for a mode's domain icon key (same icons as the bottom bar tabs). */
val ModeIcon.imageVector: ImageVector
    get() = when (this) {
        ModeIcon.GLOBE -> Icons.Default.Public
        ModeIcon.LANDMARK -> Icons.Default.AccountBalance
        ModeIcon.FLAG -> Icons.Default.Flag
    }
