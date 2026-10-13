package com.geoquiz.app.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geoquiz.app.BuildConfig
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.ui.achievements.AchievementsScreen
import com.geoquiz.app.ui.category.CategoryListScreen
import com.geoquiz.app.ui.challenges.ChallengeAcceptScreen
import com.geoquiz.app.ui.challenges.ChallengeLeaderboardScreen
import com.geoquiz.app.ui.components.rememberWindowLayout
import com.geoquiz.app.ui.credits.CreditsScreen
import com.geoquiz.app.ui.credits.OpenSourceLicencesScreen
import com.geoquiz.app.ui.debug.DebugMenuScreen
import com.geoquiz.app.ui.debug.MapPreviewScreen
import com.geoquiz.app.ui.play.PlayScreen
import com.geoquiz.app.ui.play.PlayViewModel
import com.geoquiz.app.ui.quiz.QuizScreen
import com.geoquiz.app.ui.results.AnswerReviewScreen
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.settings.SettingsScreen
import com.geoquiz.app.ui.stats.StatsScreen

/** A display cutout on the left or right edge. */
private val HORIZONTAL_CUTOUT: WindowInsets
    @Composable get() = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)

/** The system bar on the start edge, which the rail pads for. */
private val RAIL_SIDE_INSETS: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Start)

/** Switches to a top-level tab, keeping each tab's own back stack and state. */
internal fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Returns to Play (always at the bottom of the back stack), showing [quizMode]'s groups when it
 * is given. The mode is handed to Play through its back stack entry's `savedStateHandle`.
 */
internal fun NavHostController.returnToPlay(quizMode: String? = null) {
    if (quizMode != null) {
        runCatching { getBackStackEntry(Screen.Play.route) }.getOrNull()
            ?.savedStateHandle?.set(Screen.Play.RESULT_SHOW_MODE, quizMode)
    }
    if (!popBackStack(Screen.Play.route, inclusive = false)) {
        navigateToTab(Screen.Play.route)
    }
}

/**
 * Hands a mode left by [returnToPlay] in Play's [entry] to [selectMode] once, then clears it, so
 * a later recomposition or return to the tab doesn't switch the mode again.
 */
@Composable
internal fun ConsumeRequestedPlayMode(entry: NavBackStackEntry, selectMode: (String) -> Unit) {
    val requestedMode by entry.savedStateHandle
        .getStateFlow<String?>(Screen.Play.RESULT_SHOW_MODE, null)
        .collectAsStateWithLifecycle()
    LaunchedEffect(requestedMode) {
        requestedMode?.let { mode ->
            selectMode(mode)
            entry.savedStateHandle.remove<String>(Screen.Play.RESULT_SHOW_MODE)
        }
    }
}

@Composable
fun AppNavigation(challengeDeepLink: ChallengeDeepLink? = null) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Handle deep links for challenges
    LaunchedEffect(challengeDeepLink) {
        val deepLink = challengeDeepLink ?: return@LaunchedEffect
        try {
            val route = Screen.ChallengeAccept.createRoute(deepLink.challengeId)
            navController.navigate(route) {
                popUpTo(Screen.Play.route)
            }
        } catch (_: Exception) {
            // Malformed deep link, silently ignore
        }
    }

    // 3.7: a navigation rail on the start side from Medium width (tablets, phones in landscape),
    // the bottom bar otherwise; both only on the four top-level routes.
    val chrome = navigationChrome(currentRoute, rememberWindowLayout())
    val showRail = chrome == NavigationChrome.Rail
    val isSelected: (String) -> Boolean = { route ->
        currentDestination?.hierarchy?.any { it.route == route } == true
    }
    val onSelect: (String) -> Unit = { route -> navController.navigateToTab(route) }

    // A camera cutout at the side (phones in landscape) keeps the rail and every screen clear
    // of it. windowInsetsPadding consumes the cutout, so nothing inside pads for it again.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(HORIZONTAL_CUTOUT)
            .consumeWindowInsets(HORIZONTAL_CUTOUT)
    ) {
        if (showRail) {
            AppNavigationRail(isSelected = isSelected, onSelect = onSelect)
        }
        // No insets of its own: every screen's Scaffold pads for the system bars (and the quiz for
        // the keyboard). The bar's height and the rail's side are consumed below, so a screen
        // doesn't pad for the navigation bar or the start edge a second time.
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (chrome == NavigationChrome.BottomBar) {
                    AppBottomBar(isSelected = isSelected, onSelect = onSelect)
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Play.route,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .then(if (showRail) Modifier.consumeWindowInsets(RAIL_SIDE_INSETS) else Modifier)
            ) {
                composable(Screen.Play.route) { entry ->
                    val playViewModel: PlayViewModel = hiltViewModel()
                    // A mode handed back by returnToPlay (e.g. "Home" on Results)
                    ConsumeRequestedPlayMode(entry, playViewModel::selectMode)
                    PlayScreen(
                        onOpenCategory = { modeId, groupId ->
                            navController.navigate(Screen.CategoryList.createRoute(modeId, groupId))
                        },
                        onStartQuiz = { modeId, categoryType, categoryValue ->
                            navController.navigate(Screen.Quiz.createRoute(modeId, categoryType, categoryValue))
                        },
                        onOpenMode = { _ ->
                            // Phase 5 adds each new mode's entry route here. No non-classic mode is
                            // registered yet, so the "New modes" grid is never shown.
                        },
                        viewModel = playViewModel
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
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
                            onNavigateBack = { navController.popBackStack() },
                            onOpenMapPreview = { navController.navigate(Screen.MapPreview.route) }
                        )
                    }
                    composable(Screen.MapPreview.route) {
                        MapPreviewScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(Screen.Achievements.route) {
                    AchievementsScreen()
                }

                composable(Screen.Stats.route) {
                    StatsScreen(
                        // Achievements is a tab, so the link switches to it rather than stacking it.
                        onNavigateToAchievements = {
                            navController.navigateToTab(Screen.Achievements.route)
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
                        onStartQuiz = { categoryType, categoryValue, difficulty ->
                            navController.navigate(
                                Screen.Quiz.createRoute(quizMode, categoryType, categoryValue, difficulty = difficulty)
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
                        },
                        navArgument(Screen.Quiz.ARG_DIFFICULTY) {
                            type = NavType.StringType
                            defaultValue = null
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
                        onNavigateHome = { navController.returnToPlay(quizMode) }
                    )
                }

                composable(
                    route = Screen.Results.route,
                    arguments = listOf(
                        navArgument(Screen.Results.ARG_RESULT_ID) { type = NavType.StringType }
                    )
                ) {
                    ResultsScreen(
                        onPlayAgain = { quizMode, categoryType, categoryValue, difficulty ->
                            navController.navigate(
                                Screen.Quiz.createRoute(quizMode, categoryType, categoryValue, difficulty = difficulty)
                            ) {
                                popUpTo(Screen.Results.route) { inclusive = true }
                            }
                        },
                        onGoHome = { quizMode -> navController.returnToPlay(quizMode) },
                        onViewAnswers = { resultId ->
                            navController.navigate(Screen.AnswerReview.createRoute(resultId))
                        },
                        // 3.5c: same back stack behaviour as Play Again.
                        onPractiseMissed = { quizMode, categoryType, categoryValue, difficulty ->
                            navController.navigate(
                                Screen.Quiz.createRoute(quizMode, categoryType, categoryValue, difficulty = difficulty)
                            ) {
                                popUpTo(Screen.Results.route) { inclusive = true }
                            }
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
                        onGoHome = { navController.returnToPlay() }
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
                        onDecline = { navController.returnToPlay() }
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
}
