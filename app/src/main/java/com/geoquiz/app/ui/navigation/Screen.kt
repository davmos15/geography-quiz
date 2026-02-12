package com.geoquiz.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object CountriesHome : Screen("countries_home")
    data object CapitalsHome : Screen("capitals_home")
    data object FlagsHome : Screen("flags_home")

    data object Settings : Screen("settings")

    data object Achievements : Screen("achievements")

    data object CategoryList : Screen("category/{quizMode}/{groupId}") {
        fun createRoute(quizMode: String, groupId: String): String = "category/$quizMode/$groupId"
    }

    data object Quiz : Screen("quiz/{quizMode}/{categoryType}/{categoryValue}") {
        fun createRoute(quizMode: String, categoryType: String, categoryValue: String): String {
            val encoded = URLEncoder.encode(categoryValue, "UTF-8")
            return "quiz/$quizMode/$categoryType/$encoded"
        }
    }

    data object Results : Screen(
        "results/{quizMode}/{score}/{correct}/{total}/{time}/{perfectBonus}/{categoryName}/{categoryType}/{categoryValue}"
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
            categoryValue: String
        ): String {
            val encodedName = URLEncoder.encode(categoryName, "UTF-8")
            val encodedValue = URLEncoder.encode(categoryValue, "UTF-8")
            return "results/$quizMode/$score/$correct/$total/$time/$perfectBonus/$encodedName/$categoryType/$encodedValue"
        }
    }

    data object AnswerReview : Screen("answer_review")

    data object Challenges : Screen("challenges")
}
