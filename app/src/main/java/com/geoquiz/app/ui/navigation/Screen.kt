package com.geoquiz.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object CountriesHome : Screen("countries_home")
    data object CapitalsHome : Screen("capitals_home")
    data object FlagsHome : Screen("flags_home")

    data object Settings : Screen("settings")

    data object DebugMenu : Screen("debug_menu")

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
}
