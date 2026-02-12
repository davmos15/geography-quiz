package com.geoquiz.app.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geoquiz.app.data.local.preferences.settingsDataStore
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.achievements.AchievementsScreen
import com.geoquiz.app.ui.capitals.CapitalsHomeScreen
import com.geoquiz.app.ui.category.CategoryListScreen
import com.geoquiz.app.ui.challenges.ChallengeLeaderboardScreen
import com.geoquiz.app.ui.flags.FlagsHomeScreen
import com.geoquiz.app.ui.home.HomeScreen
import com.geoquiz.app.ui.quiz.QuizScreen
import com.geoquiz.app.ui.results.AnswerReviewScreen
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.map
import java.net.URLDecoder

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun AppNavigation(intent: Intent? = null) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val context = LocalContext.current
    val showFlagsFlow = remember { context.settingsDataStore.data.map { it[androidx.datastore.preferences.core.booleanPreferencesKey("show_flags")] ?: false } }
    val showFlags by showFlagsFlow.collectAsStateWithLifecycle(initialValue = false)

    val bottomNavItems = remember(showFlags) {
        buildList {
            add(BottomNavItem("Countries", Icons.Default.Public, Screen.CountriesHome.route))
            add(BottomNavItem("Capitals", Icons.Default.AccountBalance, Screen.CapitalsHome.route))
            if (showFlags) {
                add(BottomNavItem("Flags", Icons.Default.Flag, Screen.FlagsHome.route))
            }
        }
    }

    val homeRoutes = remember(showFlags) {
        buildSet {
            add(Screen.CountriesHome.route)
            add(Screen.CapitalsHome.route)
            if (showFlags) add(Screen.FlagsHome.route)
        }
    }

    // Clamp selectedTab if flags tab was hidden while selected
    LaunchedEffect(bottomNavItems.size) {
        if (selectedTab >= bottomNavItems.size) {
            selectedTab = 0
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Handle deep links for challenges
    LaunchedEffect(intent?.data) {
        val uri = intent?.data ?: return@LaunchedEffect
        if (uri.scheme == "geoquiz" && uri.host == "challenge") {
            val categoryType = uri.getQueryParameter("ct") ?: return@LaunchedEffect
            val categoryValue = uri.getQueryParameter("cv") ?: return@LaunchedEffect
            val quizMode = uri.getQueryParameter("mode") ?: "countries"
            val challengeId = uri.getQueryParameter("id")
            val route = if (challengeId != null) {
                Screen.Quiz.createRoute(quizMode, categoryType, categoryValue) + "?challengeId=$challengeId"
            } else {
                Screen.Quiz.createRoute(quizMode, categoryType, categoryValue)
            }
            navController.navigate(route) {
                popUpTo(Screen.CountriesHome.route)
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in homeRoutes || currentRoute == null) {
                NavigationBar {
                    bottomNavItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selectedTab == index,
                            onClick = {
                                if (selectedTab != index) {
                                    selectedTab = index
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.CountriesHome.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.CountriesHome.route) {
                HomeScreen(
                    quizMode = QuizMode.COUNTRIES,
                    onNavigateToCategory = { groupId ->
                        navController.navigate(Screen.CategoryList.createRoute("countries", groupId))
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
                            Screen.Quiz.createRoute("countries", categoryType, categoryValue)
                        )
                    }
                )
            }

            composable(Screen.CapitalsHome.route) {
                CapitalsHomeScreen(
                    onNavigateToCategory = { groupId ->
                        navController.navigate(Screen.CategoryList.createRoute("capitals", groupId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToAchievements = {
                        navController.navigate(Screen.Achievements.route)
                    },
                    onStartQuiz = { categoryType, categoryValue ->
                        navController.navigate(
                            Screen.Quiz.createRoute("capitals", categoryType, categoryValue)
                        )
                    }
                )
            }

            composable(Screen.FlagsHome.route) {
                FlagsHomeScreen(
                    onNavigateToCategory = { groupId ->
                        navController.navigate(Screen.CategoryList.createRoute("flags", groupId))
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToAchievements = {
                        navController.navigate(Screen.Achievements.route)
                    },
                    onStartQuiz = { categoryType, categoryValue ->
                        navController.navigate(
                            Screen.Quiz.createRoute("flags", categoryType, categoryValue)
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
                    navArgument("quizMode") { type = NavType.StringType },
                    navArgument("groupId") { type = NavType.StringType }
                )
            ) {
                val quizMode = it.arguments?.getString("quizMode") ?: "countries"
                CategoryListScreen(
                    quizMode = quizMode,
                    onNavigateBack = { navController.popBackStack() },
                    onStartQuiz = { categoryType, categoryValue ->
                        navController.navigate(
                            Screen.Quiz.createRoute(quizMode, categoryType, categoryValue)
                        )
                    }
                )
            }

            composable(
                route = Screen.Quiz.route,
                arguments = listOf(
                    navArgument("quizMode") { type = NavType.StringType },
                    navArgument("categoryType") { type = NavType.StringType },
                    navArgument("categoryValue") { type = NavType.StringType }
                )
            ) {
                val quizMode = it.arguments?.getString("quizMode") ?: "countries"
                QuizScreen(
                    onQuizComplete = { score, correct, total, time, perfectBonus, categoryName, categoryType, categoryValue ->
                        navController.navigate(
                            Screen.Results.createRoute(quizMode, score, correct, total, time, perfectBonus, categoryName, categoryType, categoryValue)
                        ) {
                            popUpTo(Screen.Quiz.route) { inclusive = true }
                        }
                    },
                    onNavigateHome = {
                        val homeRoute = when (quizMode) {
                            "capitals" -> Screen.CapitalsHome.route
                            "flags" -> Screen.FlagsHome.route
                            else -> Screen.CountriesHome.route
                        }
                        navController.popBackStack(homeRoute, inclusive = false)
                    }
                )
            }

            composable(
                route = Screen.Results.route,
                arguments = listOf(
                    navArgument("quizMode") { type = NavType.StringType },
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
                val quizMode = backStackEntry.arguments?.getString("quizMode") ?: "countries"
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
                    quizMode = quizMode,
                    onPlayAgain = {
                        val homeRoute = when (quizMode) {
                            "capitals" -> Screen.CapitalsHome.route
                            "flags" -> Screen.FlagsHome.route
                            else -> Screen.CountriesHome.route
                        }
                        navController.popBackStack(homeRoute, inclusive = false)
                    },
                    onGoHome = {
                        val homeRoute = when (quizMode) {
                            "capitals" -> Screen.CapitalsHome.route
                            "flags" -> Screen.FlagsHome.route
                            else -> Screen.CountriesHome.route
                        }
                        navController.popBackStack(homeRoute, inclusive = false)
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
}
