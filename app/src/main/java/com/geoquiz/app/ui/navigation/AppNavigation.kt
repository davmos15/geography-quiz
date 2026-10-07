package com.geoquiz.app.ui.navigation

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geoquiz.app.BuildConfig
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.achievements.AchievementsScreen
import com.geoquiz.app.ui.capitals.CapitalsHomeScreen
import com.geoquiz.app.ui.category.CategoryListScreen
import com.geoquiz.app.ui.challenges.ChallengeAcceptScreen
import com.geoquiz.app.ui.challenges.ChallengeLeaderboardScreen
import com.geoquiz.app.ui.credits.CreditsScreen
import com.geoquiz.app.ui.credits.OpenSourceLicencesScreen
import com.geoquiz.app.ui.debug.DebugMenuScreen
import com.geoquiz.app.ui.flags.FlagsHomeScreen
import com.geoquiz.app.ui.home.HomeScreen
import com.geoquiz.app.ui.quiz.QuizScreen
import com.geoquiz.app.ui.results.AnswerReviewScreen
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.settings.SettingsScreen
import com.geoquiz.app.ui.stats.StatsScreen

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val bottomNavItems = listOf(
    BottomNavItem("Countries", Icons.Default.Public, Screen.CountriesHome.route),
    BottomNavItem("Capitals", Icons.Default.AccountBalance, Screen.CapitalsHome.route),
    BottomNavItem("Flags", Icons.Default.Flag, Screen.FlagsHome.route)
)

private val homeRoutes = setOf(
    Screen.CountriesHome.route,
    Screen.CapitalsHome.route,
    Screen.FlagsHome.route
)

@Composable
fun AppNavigation(challengeDeepLink: ChallengeDeepLink? = null) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Handle deep links for challenges
    LaunchedEffect(challengeDeepLink) {
        val deepLink = challengeDeepLink ?: return@LaunchedEffect
        try {
            val route = Screen.ChallengeAccept.createRoute(deepLink.challengeId)
            navController.navigate(route) {
                popUpTo(Screen.CountriesHome.route)
            }
        } catch (_: Exception) {
            // Malformed deep link — silently ignore
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
                    onNavigateToStats = {
                        navController.navigate(Screen.Stats.route)
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
                    onNavigateToStats = {
                        navController.navigate(Screen.Stats.route)
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
                    onNavigateToStats = {
                        navController.navigate(Screen.Stats.route)
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
                    onNavigateBack = { navController.popBackStack() },
                    onOpenCredits = { navController.navigate(Screen.Credits.route) },
                    onOpenDebugMenu = if (BuildConfig.DEBUG) {
                        { navController.navigate(Screen.DebugMenu.route) }
                    } else {
                        null
                    }
                )
            }

            composable(Screen.Credits.route) {
                CreditsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSourceLicences = { navController.navigate(Screen.OpenSourceLicences.route) }
                )
            }

            composable(Screen.OpenSourceLicences.route) {
                OpenSourceLicencesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            if (BuildConfig.DEBUG) {
                composable(Screen.DebugMenu.route) {
                    DebugMenuScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            composable(Screen.Achievements.route) {
                AchievementsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Stats.route) {
                StatsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAchievements = {
                        navController.navigate(Screen.Achievements.route)
                    },
                    onNavigateToChallenges = {
                        navController.navigate(Screen.Challenges.route)
                    }
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
                    navArgument("categoryValue") { type = NavType.StringType },
                    navArgument("challengeId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    }
                )
            ) {
                val quizMode = it.arguments?.getString("quizMode") ?: "countries"
                QuizScreen(
                    onQuizComplete = { resultId ->
                        navController.navigate(Screen.Results.createRoute(resultId)) {
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
                    navArgument(Screen.Results.ARG_RESULT_ID) { type = NavType.StringType }
                )
            ) {
                ResultsScreen(
                    onPlayAgain = { quizMode, categoryType, categoryValue ->
                        navController.navigate(
                            Screen.Quiz.createRoute(quizMode, categoryType, categoryValue)
                        ) {
                            popUpTo(Screen.Results.route) { inclusive = true }
                        }
                    },
                    onGoHome = { quizMode ->
                        navController.popBackStack(homeRouteFor(quizMode), inclusive = false)
                    },
                    onViewAnswers = { resultId ->
                        navController.navigate(Screen.AnswerReview.createRoute(resultId))
                    }
                )
            }

            composable(
                route = Screen.AnswerReview.route,
                arguments = listOf(
                    navArgument(Screen.AnswerReview.ARG_RESULT_ID) { type = NavType.StringType }
                )
            ) {
                AnswerReviewScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onGoHome = {
                        navController.popBackStack(Screen.CountriesHome.route, inclusive = false)
                    }
                )
            }

            composable(
                route = Screen.ChallengeAccept.route,
                arguments = listOf(
                    navArgument("challengeId") { type = NavType.StringType }
                )
            ) {
                ChallengeAcceptScreen(
                    onAccept = { quizMode, categoryType, categoryValue, challengeId ->
                        navController.navigate(
                            Screen.Quiz.createRoute(quizMode, categoryType, categoryValue, challengeId)
                        ) {
                            popUpTo(Screen.ChallengeAccept.route) { inclusive = true }
                        }
                    },
                    onDecline = {
                        navController.popBackStack(Screen.CountriesHome.route, inclusive = false)
                    }
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

/** The home tab for a quiz mode id ("countries", "capitals" or "flags"). */
private fun homeRouteFor(quizMode: String): String = when (quizMode) {
    "capitals" -> Screen.CapitalsHome.route
    "flags" -> Screen.FlagsHome.route
    else -> Screen.CountriesHome.route
}
