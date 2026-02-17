package com.geoquiz.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object CountriesHome : Screen("countries_home")
    data object CapitalsHome : Screen("capitals_home")
    data object FlagsHome : Screen("flags_home")

    data object Settings : Screen("settings")

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

    data object Results : Screen(
        "results/{quizMode}/{score}/{correct}/{total}/{time}/{perfectBonus}/{categoryName}/{categoryType}/{categoryValue}/{incorrectGuesses}/{challengeId}"
    ) {
        fun createRoute(
            quizMode: String,
            score: Double,
            correct: Int,
            total: Int,
            time: Int,
            perfectBonus: Boolean,
            categoryName: String,
            categoryType: String,
            categoryValue: String,
            incorrectGuesses: Int,
            challengeId: String? = null
        ): String {
            val encodedName = Uri.encode(categoryName)
            val encodedValue = Uri.encode(categoryValue)
            return "results/$quizMode/$score/$correct/$total/$time/$perfectBonus/$encodedName/$categoryType/$encodedValue/$incorrectGuesses/${challengeId ?: "_"}"
        }
    }

    data object AnswerReview : Screen("answer_review")

    data object ChallengeAccept : Screen("challenge_accept/{challengeId}") {
        fun createRoute(challengeId: String): String = "challenge_accept/$challengeId"
    }

    data object Challenges : Screen("challenges")
}
