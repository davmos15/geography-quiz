package com.geoquiz.app.ui.capitals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.ui.home.CategoryGroupInfo
import com.geoquiz.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapitalsHomeScreen(
    onNavigateToCategory: (groupId: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onNavigateToChallenges: () -> Unit,
    onStartQuiz: (categoryType: String, categoryValue: String) -> Unit,
    viewModel: CapitalsHomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = CapitalsAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Capitals Quiz",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToChallenges) {
                        Icon(
                            Icons.Default.Leaderboard,
                            contentDescription = "Challenges"
                        )
                    }
                    IconButton(onClick = onNavigateToAchievements) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "Achievements",
                            tint = GoldColor
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading capitals...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val nonAllGroups = state.categoryGroups.filter { it.group != CategoryGroup.ALL_COUNTRIES }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Featured "All Capitals" card
                item(span = { GridItemSpan(2) }) {
                    ElevatedCard(
                        onClick = { onStartQuiz("all", "_") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            CapitalsAccent,
                                            Color(0xFFFF7043)
                                        )
                                    )
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "All Capitals",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${state.totalCount} capitals",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(nonAllGroups) { info ->
                    CapitalsCategoryTile(
                        info = info,
                        onClick = { onNavigateToCategory(info.group.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CapitalsCategoryTile(
    info: CategoryGroupInfo,
    onClick: () -> Unit
) {
    val colors = getCapitalsCategoryColors(info.group.colorIndex)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = colors.first)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = info.group.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.second
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${info.quizCount} ${if (info.quizCount == 1) "quiz" else "quizzes"}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.second.copy(alpha = 0.6f)
            )
        }
    }
}

private fun getCapitalsCategoryColors(index: Int): Pair<Color, Color> = when (index) {
    0 -> Pair(CategoryOrangeTint, CategoryOrange)     // Regions
    1 -> Pair(CategoryAmberTint, CategoryAmber)        // Subregions
    2 -> Pair(CategoryBrownTint, CategoryBrown)        // Starting Letter
    3 -> Pair(CategoryPinkTint, CategoryPink)          // Ending Letter
    4 -> Pair(CategoryPurpleTint, CategoryPurple)      // Containing Letter
    5 -> Pair(CategoryTealTint, CategoryTeal)          // Name Length
    6 -> Pair(CategoryIndigoTint, CategoryIndigo)      // Letter Patterns
    7 -> Pair(CategoryCyanTint, CategoryCyan)          // Word Patterns
    8 -> Pair(CategoryGreenTint, CategoryGreen)        // Islands
    else -> Pair(CategoryBlueTint, CategoryBlue)
}
