package com.geoquiz.app.ui.navigation

import java.net.URLEncoder
import java.net.URLDecoder

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
        "results/{score}/{correct}/{total}/{time}/{perfectBonus}/{categoryName}"
    ) {
        fun createRoute(
            score: Double,
            correct: Int,
            total: Int,
            time: Int,
            perfectBonus: Boolean,
            categoryName: String
        ): String {
            val encoded = URLEncoder.encode(categoryName, "UTF-8")
            return "results/$score/$correct/$total/$time/$perfectBonus/$encoded"
        }
    }

    data object AnswerReview : Screen("answer_review")
}
