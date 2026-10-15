package com.geoquiz.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    /** The Play tab (start destination): classic-mode switch, category groups and new modes. */
    data object Play : Screen("play") {
        /**
         * Key in Play's back stack entry `savedStateHandle` holding a classic mode id to show
         * when the player returns to Play (e.g. "Home" on Results).
         */
        const val RESULT_SHOW_MODE = "play_show_mode"
    }

    data object Settings : Screen("settings")

    data object DebugMenu : Screen("debug_menu")

    /** Debug builds only: world map renderer preview (task 4.2). */
    data object MapPreview : Screen("debug_map_preview")

    data object Credits : Screen("credits")

    data object OpenSourceLicences : Screen("open_source_licences")

    data object Achievements : Screen("achievements")

    data object Stats : Screen("stats")

    data object CategoryList : Screen("category/{quizMode}/{groupId}") {
        fun createRoute(quizMode: String, groupId: String): String = "category/$quizMode/$groupId"
    }

    /**
     * A quiz. `difficulty` is an optional [com.geoquiz.app.domain.model.Difficulty.id]; without
     * it (old callers, challenges, "Resume quiz") the quiz uses the resume save's tier or the
     * remembered default (see `QuizViewModel`).
     */
    data object Quiz : Screen(
        "quiz/{quizMode}/{categoryType}/{categoryValue}?challengeId={challengeId}&difficulty={difficulty}"
    ) {
        const val ARG_DIFFICULTY = "difficulty"

        fun createRoute(
            quizMode: String,
            categoryType: String,
            categoryValue: String,
            challengeId: String? = null,
            difficulty: String? = null
        ): String {
            val encoded = Uri.encode(categoryValue)
            val base = "quiz/$quizMode/$categoryType/$encoded"
            val query = listOfNotNull(
                challengeId?.let { "challengeId=$it" },
                difficulty?.let { "$ARG_DIFFICULTY=${Uri.encode(it)}" }
            )
            return if (query.isEmpty()) base else base + query.joinToString("&", prefix = "?")
        }
    }

    /** Results for a stored [com.geoquiz.app.domain.model.CompletedQuiz]. */
    data object Results : Screen("results/{resultId}") {
        const val ARG_RESULT_ID = "resultId"
        fun createRoute(resultId: String): String = "results/${Uri.encode(resultId)}"
    }

    data object AnswerReview : Screen("answer_review/{resultId}") {
        const val ARG_RESULT_ID = "resultId"
        fun createRoute(resultId: String): String = "answer_review/${Uri.encode(resultId)}"
    }

    data object ChallengeAccept : Screen("challenge_accept/{challengeId}") {
        fun createRoute(challengeId: String): String = "challenge_accept/$challengeId"
    }

    data object Challenges : Screen("challenges")

    /**
     * A new mode's own setup screen, opened from Play's "New modes" grid
     * ([com.geoquiz.app.domain.mode.GameModeRegistry]). Dispatches by [modeId] to that mode's own
     * setup composable (task 5.0); classic modes never navigate here.
     */
    data object ModeSetup : Screen("mode_setup/{modeId}") {
        const val ARG_MODE_ID = "modeId"
        fun createRoute(modeId: String): String = "mode_setup/$modeId"
    }

    /**
     * Country silhouettes (task 5.1 builds the real screen). `difficulty` is an optional
     * [com.geoquiz.app.domain.model.Difficulty.id], as in [Quiz].
     */
    data object Silhouettes : Screen("silhouettes/{categoryType}/{categoryValue}?difficulty={difficulty}") {
        const val ARG_DIFFICULTY = "difficulty"

        fun createRoute(categoryType: String, categoryValue: String, difficulty: String? = null): String {
            val encoded = Uri.encode(categoryValue)
            val base = "silhouettes/$categoryType/$encoded"
            return difficulty?.let { "$base?$ARG_DIFFICULTY=${Uri.encode(it)}" } ?: base
        }
    }

    /**
     * Tap the map (task 5.2 builds the real screen). [variant] is one of
     * `TapMapGameMode.VARIANT_FIND` or `VARIANT_NAME_ALL`. `difficulty` is optional, as in [Quiz].
     */
    data object TapMap : Screen("tap_map/{variant}/{categoryType}/{categoryValue}?difficulty={difficulty}") {
        const val ARG_DIFFICULTY = "difficulty"

        fun createRoute(
            variant: String,
            categoryType: String,
            categoryValue: String,
            difficulty: String? = null
        ): String {
            val encoded = Uri.encode(categoryValue)
            val base = "tap_map/$variant/$categoryType/$encoded"
            return difficulty?.let { "$base?$ARG_DIFFICULTY=${Uri.encode(it)}" } ?: base
        }
    }

    /**
     * Border hop (task 6.1): its own screen picks a fresh start/target pair from
     * `categoryType`/`categoryValue` every time it is navigated to (no fixed pair in the route,
     * unlike [Silhouettes]' fixed 15-country round). `difficulty` is optional, as in [Quiz].
     */
    data object BorderHop : Screen("border_hop/{categoryType}/{categoryValue}?difficulty={difficulty}") {
        const val ARG_DIFFICULTY = "difficulty"

        fun createRoute(categoryType: String, categoryValue: String, difficulty: String? = null): String {
            val encoded = Uri.encode(categoryValue)
            val base = "border_hop/$categoryType/$encoded"
            return difficulty?.let { "$base?$ARG_DIFFICULTY=${Uri.encode(it)}" } ?: base
        }
    }

    /**
     * US states and Canadian provinces (task 6.2). [variant] is one of
     * `UsCaRegionsGameMode.VARIANT_TAP`, `VARIANT_NAME_ALL` or `VARIANT_CHOICE`. `categoryType`
     * is always `"region"` and `categoryValue` one of the three fixed labels
     * (`UsCaRegionsCategory`), except for a "Practise the ones you missed" round, where
     * `categoryType` is `"practice"` and `categoryValue` the missed admin1 codes
     * (`QuizCategory.Practice`'s usual shape). `difficulty` is optional, as in [Quiz].
     */
    data object UsCaRegions : Screen("us_ca_regions/{variant}/{categoryType}/{categoryValue}?difficulty={difficulty}") {
        const val ARG_DIFFICULTY = "difficulty"

        fun createRoute(
            variant: String,
            categoryType: String,
            categoryValue: String,
            difficulty: String? = null
        ): String {
            val encoded = Uri.encode(categoryValue)
            val base = "us_ca_regions/$variant/$categoryType/$encoded"
            return difficulty?.let { "$base?$ARG_DIFFICULTY=${Uri.encode(it)}" } ?: base
        }
    }
}
