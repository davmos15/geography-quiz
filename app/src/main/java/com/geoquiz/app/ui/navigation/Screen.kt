package com.geoquiz.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Home : Screen("home")

    data object Settings : Screen("settings")

    data object Achievements : Screen("achievements")

    data object CategoryList : Screen("category/{groupId}") {
        fun createRoute(groupId: String): String = "category/$groupId"
    }

    data object Quiz : Screen("quiz/{categoryType}/{categoryValue}") {
        fun createRoute(categoryType: String, categoryValue: String): String {
            val encoded = URLEncoder.encode(categoryValue, "UTF-8")
            return "quiz/$categoryType/$encoded"
        }
    }

    data object Results : Screen(
        "results/{score}/{correct}/{total}/{time}/{perfectBonus}/{categoryName}/{categoryType}/{categoryValue}"
    ) {
        fun createRoute(
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
            return "results/$score/$correct/$total/$time/$perfectBonus/$encodedName/$categoryType/$encodedValue"
        }
    }

    data object AnswerReview : Screen("answer_review")

    data object Challenges : Screen("challenges")
}
