package com.geoquiz.app.ui.achievements

import com.geoquiz.app.ui.components.WrappingTopAppBar
import androidx.compose.ui.res.stringResource
import com.geoquiz.app.R
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Immutable
import com.geoquiz.app.domain.model.AchievementTier
import com.geoquiz.app.ui.theme.GeoColors
import com.geoquiz.app.ui.theme.geoColors
import com.geoquiz.app.ui.components.ReadableWidthFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: AchievementsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AchievementsContent(state = state, onNavigateBack = onNavigateBack)
}

/**
 * Stateless Achievements screen: renders [state] only, so previews and screenshot tests can
 * show locked and unlocked tier cards without a ViewModel. [onNavigateBack] null hides the back
 * arrow (bottom navigation tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsContent(
    state: AchievementsUiState,
    onNavigateBack: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            WrappingTopAppBar(
                title = stringResource(R.string.nav_achievements),
                // No back arrow when shown as a bottom navigation tab
                navigationIcon = onNavigateBack?.let { back ->
                    @Composable {
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                }
            )
        }
    ) { padding ->
        ReadableWidthFrame(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { sideInset ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp + sideInset, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    val unlockedCount = state.achievements.count { it.unlocked }
                    Text(
                        text = "$unlockedCount / ${state.achievements.size} unlocked",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(state.achievements) { info ->
                    AchievementCard(info)
                }
            }
        }
    }
}

/** Colours of an unlocked achievement card for one tier, from [GeoColors]. */
@Immutable
internal data class TierCardColours(val container: Color, val accent: Color, val onContainer: Color)

internal fun GeoColors.tierCardColours(tier: AchievementTier): TierCardColours = when (tier) {
    AchievementTier.GOLD -> TierCardColours(tierGoldContainer, tierGold, onTierGoldContainer)
    AchievementTier.SILVER -> TierCardColours(tierSilverContainer, tierSilver, onTierSilverContainer)
    AchievementTier.BRONZE -> TierCardColours(tierBronzeContainer, tierBronze, onTierBronzeContainer)
}

@Composable
private fun AchievementCard(info: AchievementDisplayInfo) {
    val tierColours = MaterialTheme.geoColors.tierCardColours(info.achievement.tier)
    // Locked cards: the lock icon marks the state, so text keeps full contrast (no alpha).
    val lockedContent = MaterialTheme.colorScheme.onSurfaceVariant

    val containerColor = if (info.unlocked) {
        tierColours.container
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (info.unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                contentDescription = null,
                tint = if (info.unlocked) tierColours.accent else lockedContent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.achievement.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (info.unlocked) tierColours.onContainer else lockedContent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = info.achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (info.unlocked) tierColours.onContainer else lockedContent
                )
            }
            if (info.unlocked) {
                Text(
                    text = info.achievement.tier.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tierColours.accent
                )
            }
        }
    }
}
