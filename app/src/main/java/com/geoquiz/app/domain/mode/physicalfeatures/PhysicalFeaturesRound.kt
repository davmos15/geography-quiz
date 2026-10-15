package com.geoquiz.app.domain.mode.physicalfeatures

import kotlin.random.Random

/**
 * A round's items (task 7.2 review): [ROUND_SIZE] random features from the chosen filter, fewer
 * if there are fewer candidates - the same size and "fewer if the filter is smaller" rule Country
 * silhouettes uses (D34, [com.geoquiz.app.domain.mode.silhouettes.SilhouettesQuestionGenerator]),
 * kept consistent across the app's new single-item-at-a-time modes rather than picked separately
 * (the full unfiltered pool can be as large as 41 rivers, well above a comfortable sitting).
 *
 * Unlike Silhouettes there is no [com.geoquiz.app.domain.model.QuizCategory.Practice]
 * short-circuit: that category's codes are validated by a fixed `[A-Z]{3}` regex
 * ([com.geoquiz.app.domain.model.QuizCategory.Practice.parseCodes]) that this mode's hyphenated,
 * lower-case feature ids (e.g. `"aral-sea"`) can never match, so "Practise the ones you missed"
 * can carry no real ids here - see the task 7.2 report for the open question this leaves for the
 * Results screen (same reasoning as US states and Canadian provinces not supporting Answer
 * review, D43).
 */
object PhysicalFeaturesRound {
    const val ROUND_SIZE = 15

    fun items(candidates: List<PhysicalFeature>, random: Random): List<PhysicalFeature> =
        candidates.shuffled(random).take(ROUND_SIZE)
}
