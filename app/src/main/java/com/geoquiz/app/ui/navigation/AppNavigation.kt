package com.geoquiz.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.ui.achievements.AchievementsScreen
import com.geoquiz.app.ui.category.CategoryListScreen
import com.geoquiz.app.ui.home.HomeScreen
import com.geoquiz.app.ui.quiz.QuizScreen
import com.geoquiz.app.ui.results.AnswerReviewScreen
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.settings.SettingsScreen
import java.net.URLDecoder

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCategory = { groupId ->
                    navController.navigate(Screen.CategoryList.createRoute(groupId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAchievements = {
                    navController.navigate(Screen.Achievements.route)
                },
                onStartQuiz = { categoryType, categoryValue ->
                    navController.navigate(
                        Screen.Quiz.createRoute(categoryType, categoryValue)
                    )
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Achievements.route) {
            AchievementsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CategoryList.route,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType }
            )
        ) {
            CategoryListScreen(
                onNavigateBack = { navController.popBackStack() },
                onStartQuiz = { categoryType, categoryValue ->
                    navController.navigate(
                        Screen.Quiz.createRoute(categoryType, categoryValue)
                    )
                }
            )
        }

        composable(
            route = Screen.Quiz.route,
            arguments = listOf(
                navArgument("categoryType") { type = NavType.StringType },
                navArgument("categoryValue") { type = NavType.StringType }
            )
        ) {
            QuizScreen(
                onQuizComplete = { score, correct, total, time, perfectBonus, categoryName ->
                    navController.navigate(
                        Screen.Results.createRoute(score, correct, total, time, perfectBonus, categoryName)
                    ) {
                        popUpTo(Screen.Quiz.route) { inclusive = true }
                    }
                },
                onNavigateHome = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        composable(
            route = Screen.Results.route,
            arguments = listOf(
                navArgument("score") { type = NavType.FloatType },
                navArgument("correct") { type = NavType.IntType },
                navArgument("total") { type = NavType.IntType },
                navArgument("time") { type = NavType.IntType },
                navArgument("perfectBonus") { type = NavType.BoolType },
                navArgument("categoryName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val score = backStackEntry.arguments?.getFloat("score")?.toDouble() ?: 0.0
            val correct = backStackEntry.arguments?.getInt("correct") ?: 0
            val total = backStackEntry.arguments?.getInt("total") ?: 0
            val time = backStackEntry.arguments?.getInt("time") ?: 0
            val perfectBonus = backStackEntry.arguments?.getBoolean("perfectBonus") ?: false
            val rawName = backStackEntry.arguments?.getString("categoryName") ?: ""
            val categoryName = URLDecoder.decode(rawName, "UTF-8")

            ResultsScreen(
                score = score,
                correctAnswers = correct,
                totalCountries = total,
                timeElapsedSeconds = time,
                perfectBonus = perfectBonus,
                categoryName = categoryName,
                onPlayAgain = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onGoHome = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onViewAnswers = {
                    navController.navigate(Screen.AnswerReview.route)
                }
            )
        }

        composable(Screen.AnswerReview.route) {
            AnswerReviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
