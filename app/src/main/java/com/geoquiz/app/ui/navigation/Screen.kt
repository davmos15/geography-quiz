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

    data object Quiz : Screen("quiz/{quizMode}/{categoryType}/{categoryValue}?challengeId={challengeId}") {
        fun createRoute(quizMode: String, categoryType: String, categoryValue: String, challengeId: String? = null): String {
            val encoded = Uri.encode(categoryValue)
            val base = "quiz/$quizMode/$categoryType/$encoded"
            return if (challengeId != null) "$base?challengeId=$challengeId" else base
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
