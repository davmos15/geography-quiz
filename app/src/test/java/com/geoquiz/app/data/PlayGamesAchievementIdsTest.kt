package com.geoquiz.app.data

import com.geoquiz.app.domain.model.Achievement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Achievement enum is the single source of truth (2.11); every entry needs a Play Games ID. */
class PlayGamesAchievementIdsTest {

    @Test
    fun everyAchievementHasAPlayGamesId() {
        val missing = Achievement.entries.filter { PlayGamesAchievementIds.getPlayGamesId(it) == null }
        assertTrue("No Play Games ID for $missing", missing.isEmpty())
    }

    @Test
    fun playGamesIdsAreUnique() {
        val ids = Achievement.entries.mapNotNull { PlayGamesAchievementIds.getPlayGamesId(it) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
