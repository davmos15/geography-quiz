package com.geoquiz.app.ui.quiz

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * How the quiz screen shares its height (3.7). The height is what is left once the system bars
 * and the open keyboard are taken off, so these decisions change when the keyboard opens. They
 * are made in "100% text" dp (the height divided by the font scale), because the header, the
 * answer field and the list rows all grow with the text.
 */

/** Below this height (at 100% text) the quiz title scrolls away with the list. */
internal val HEADER_PINNED_MIN_HEIGHT: Dp = 560.dp

/** Below this height (at 100% text) the recent-answer chips are left out. */
internal val RECENT_ANSWERS_MIN_HEIGHT: Dp = 300.dp

/**
 * What the quiz shows where, for the space it has.
 *
 * @property titleScrollsWithList the title, tier label and pattern banner are the list's first
 *   item instead of pinned above it, so the rows keep usable space (short phones, keyboard open,
 *   large text). The count, timer, strikes, settings and pause stay pinned.
 * @property showRecentAnswers the last three correct answers above the field (typed tiers). They
 *   repeat what the list shows, so they are the first thing left out when space is very short.
 */
internal data class QuizSpace(
    val titleScrollsWithList: Boolean,
    val showRecentAnswers: Boolean
)

/** The [QuizSpace] for [availableHeight] of content height at [fontScale]. */
internal fun quizSpace(availableHeight: Dp, fontScale: Float): QuizSpace {
    val atNormalText = availableHeight / fontScale.coerceAtLeast(1f)
    return QuizSpace(
        titleScrollsWithList = atNormalText < HEADER_PINNED_MIN_HEIGHT,
        showRecentAnswers = atNormalText >= RECENT_ANSWERS_MIN_HEIGHT
    )
}
