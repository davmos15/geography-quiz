package com.geoquiz.app.data.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frequency cap for interstitial ads.
 *
 * - At most one interstitial per [QUIZZES_PER_INTERSTITIAL] completed quizzes. Every completed
 *   quiz counts, including one the player gave up on.
 * - Never after a quiz that lasted less than [MIN_QUIZ_SECONDS].
 * - The count only resets when an ad was actually shown ([onInterstitialShown]), so if no ad
 *   was loaded the next eligible quiz can still show one.
 *
 * The count lives in memory only: after a cold start the earliest interstitial is on the
 * [QUIZZES_PER_INTERSTITIAL]th completed quiz. Showing the ad after Results has rendered is the
 * caller's job (see `ResultsScreen`).
 */
@Singleton
class InterstitialPolicy @Inject constructor() {

    private var quizzesSinceLastAd = 0

    /**
     * Counts a freshly completed quiz and returns whether an interstitial may be shown for it.
     * Call once per completed quiz.
     */
    @Synchronized
    fun onQuizCompleted(durationSeconds: Int): Boolean {
        quizzesSinceLastAd++
        return quizzesSinceLastAd >= QUIZZES_PER_INTERSTITIAL && durationSeconds >= MIN_QUIZ_SECONDS
    }

    /** An interstitial was actually shown: start counting again. */
    @Synchronized
    fun onInterstitialShown() {
        quizzesSinceLastAd = 0
    }

    companion object {
        /** At most one interstitial per this many completed quizzes. */
        const val QUIZZES_PER_INTERSTITIAL = 3

        /** No interstitial after a quiz shorter than this. */
        const val MIN_QUIZ_SECONDS = 60
    }
}
