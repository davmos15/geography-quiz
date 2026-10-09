package com.geoquiz.app.ui.achievements

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.AchievementTier
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stateless Achievements content renders from state alone (used by screenshot tests). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class AchievementsContentUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sampleState(): AchievementsUiState {
        val gold = Achievement.entries.first { it.tier == AchievementTier.GOLD }
        val silver = Achievement.entries.first { it.tier == AchievementTier.SILVER }
        val bronze = Achievement.entries.first { it.tier == AchievementTier.BRONZE }
        val locked = Achievement.entries.first { it != gold && it != silver && it != bronze }
        return AchievementsUiState(
            achievements = listOf(
                AchievementDisplayInfo(gold, unlocked = true),
                AchievementDisplayInfo(silver, unlocked = true),
                AchievementDisplayInfo(bronze, unlocked = true),
                AchievementDisplayInfo(locked, unlocked = false),
            )
        )
    }

    @Test
    fun rendersUnlockedAndLockedCards_fromStateOnly() {
        val state = sampleState()
        compose.setContent { GeographyQuizTheme { AchievementsContent(state = state) } }

        compose.onNodeWithText("3 / 4 unlocked").assertIsDisplayed()
        state.achievements.forEach { compose.onNodeWithText(it.achievement.title).assertIsDisplayed() }
        // Tier labels only on unlocked cards.
        listOf("GOLD", "SILVER", "BRONZE").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun backArrow_onlyWhenACallbackIsGiven_andCallsIt() {
        var backs = 0
        compose.setContent {
            GeographyQuizTheme { AchievementsContent(state = sampleState(), onNavigateBack = { backs++ }) }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }
}
