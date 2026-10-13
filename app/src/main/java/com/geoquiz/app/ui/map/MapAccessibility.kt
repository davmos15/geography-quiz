package com.geoquiz.app.ui.map

import android.content.Context
import android.content.res.Resources
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.geoquiz.app.R
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.ui.components.A11yText

/**
 * The map's one spoken content description (task 4.6), e.g. "Map of Europe. 12 of 46 countries
 * found, 2 wrong." Only [MapFeatureState.Found] and [MapFeatureState.Wrong] are counted; the
 * wrong clause is left out when nothing is wrong yet. [total] is the number of features a mode
 * is asking about (not necessarily `featureStates.size`, which only holds non-default entries).
 *
 * The join itself - the space after the region clause, the comma before "wrong", the full stop -
 * lives in [R.string.a11y_map_description_found] / [R.string.a11y_map_description_found_wrong]
 * (placeholders only), not hardcoded here, so a translation can reorder or repunctuate it. The
 * found/wrong counts still go through their own plurals ([A11yText.mapFound], [A11yText.mapWrong]).
 */
fun mapContentDescription(
    res: Resources,
    regionLabel: String,
    featureStates: Map<String, MapFeatureState>,
    total: Int,
): String {
    val found = featureStates.values.count { it == MapFeatureState.Found }
    val wrong = featureStates.values.count { it == MapFeatureState.Wrong }
    val mapOf = res.getString(R.string.a11y_map_of, regionLabel)
    val foundClause = A11yText.mapFound(res, found, total)
    return if (wrong > 0) {
        res.getString(R.string.a11y_map_description_found_wrong, mapOf, foundClause, A11yText.mapWrong(res, wrong))
    } else {
        res.getString(R.string.a11y_map_description_found, mapOf, foundClause)
    }
}

/**
 * True while TalkBack, Switch Access or another screen-reader-style service that relies on
 * touch exploration is on, so a map screen can default to [MapListAlternative] instead of
 * [MapCanvas] (task 4.6). Re-read on resume, like [com.geoquiz.app.ui.components.rememberReducedMotion].
 */
@Composable
fun rememberIsTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember(context) { mutableStateOf(context.isTouchExplorationEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = context.isTouchExplorationEnabled() }
    return enabled
}

private fun Context.isTouchExplorationEnabled(): Boolean {
    val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    return manager?.isTouchExplorationEnabled == true
}
