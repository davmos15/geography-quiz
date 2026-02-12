package com.geoquiz.app.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geoquiz.app.ui.achievements.AchievementsScreen
import com.geoquiz.app.ui.category.CategoryListScreen
import com.geoquiz.app.ui.challenges.ChallengeLeaderboardScreen
import com.geoquiz.app.ui.home.HomeScreen
import com.geoquiz.app.ui.quiz.QuizScreen
import com.geoquiz.app.ui.results.AnswerReviewScreen
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.settings.SettingsScreen
import java.net.URLDecoder

@Composable
fun AppNavigation(intent: Intent? = null) {
    val navController = rememberNavController()

    // Handle deep links for challenges
    LaunchedEffect(intent?.data) {
        val uri = intent?.data ?: return@LaunchedEffect
        if (uri.scheme == "geoquiz" && uri.host == "challenge") {
            val categoryType = uri.getQueryParameter("ct") ?: return@LaunchedEffect
            val categoryValue = uri.getQueryParameter("cv") ?: return@LaunchedEffect
            val challengeId = uri.getQueryParameter("id")
            val route = if (challengeId != null) {
                Screen.Quiz.createRoute(categoryType, categoryValue) + "?challengeId=$challengeId"
            } else {
                Screen.Quiz.createRoute(categoryType, categoryValue)
            }
            navController.navigate(route) {
                popUpTo(Screen.Home.route)
            }
        }
    }

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
                onNavigateToChallenges = {
                    navController.navigate(Screen.Challenges.route)
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
                onQuizComplete = { score, correct, total, time, perfectBonus, categoryName, categoryType, categoryValue ->
                    navController.navigate(
                        Screen.Results.createRoute(score, correct, total, time, perfectBonus, categoryName, categoryType, categoryValue)
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
                navArgument("categoryName") { type = NavType.StringType },
                navArgument("categoryType") { type = NavType.StringType },
                navArgument("categoryValue") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val score = backStackEntry.arguments?.getFloat("score")?.toDouble() ?: 0.0
            val correct = backStackEntry.arguments?.getInt("correct") ?: 0
            val total = backStackEntry.arguments?.getInt("total") ?: 0
            val time = backStackEntry.arguments?.getInt("time") ?: 0
            val perfectBonus = backStackEntry.arguments?.getBoolean("perfectBonus") ?: false
            val rawName = backStackEntry.arguments?.getString("categoryName") ?: ""
            val categoryName = URLDecoder.decode(rawName, "UTF-8")
            val categoryType = backStackEntry.arguments?.getString("categoryType") ?: ""
            val rawValue = backStackEntry.arguments?.getString("categoryValue") ?: ""
            val categoryValue = URLDecoder.decode(rawValue, "UTF-8")

            ResultsScreen(
                score = score,
                correctAnswers = correct,
                totalCountries = total,
                timeElapsedSeconds = time,
                perfectBonus = perfectBonus,
                categoryName = categoryName,
                categoryType = categoryType,
                categoryValue = categoryValue,
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

        composable(Screen.Challenges.route) {
            ChallengeLeaderboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
