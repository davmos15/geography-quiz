package com.geoquiz.app.ui.whatsnew

import com.geoquiz.app.domain.model.FeatureFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [whatsNewSections] (P10-F): the "New ways to play" list is filtered to enabled flags only. */
class WhatsNewContentTest {

    @Test
    fun `every mode shows when every flag is enabled`() {
        val sections = whatsNewSections(FeatureFlag.entries.toSet())

        val newModes = sections.first { it.headingRes == com.geoquiz.app.R.string.whats_new_section_new_modes }
        assertEquals(FeatureFlag.entries.size, newModes.items.size)
    }

    @Test
    fun `a mode disappears when its flag is off`() {
        val enabled = FeatureFlag.entries.toSet() - FeatureFlag.BORDER_HOP

        val newModes = newWaysToPlaySection(enabled)

        assertFalse(newModes.items.any { it.titleRes == com.geoquiz.app.R.string.whats_new_mode_border_hop_title })
        assertEquals(FeatureFlag.entries.size - 1, newModes.items.size)
    }

    @Test
    fun `no flags enabled drops the new modes section entirely`() {
        val sections = whatsNewSections(emptySet())

        assertTrue(sections.none { it.headingRes == com.geoquiz.app.R.string.whats_new_section_new_modes })
        // "Changes" is never flag-gated, so it always stays.
        assertTrue(sections.any { it.headingRes == com.geoquiz.app.R.string.whats_new_section_changes })
    }

    @Test
    fun `changes section is never empty and never flag-gated`() {
        val changes = changesSection()

        assertTrue(changes.items.isNotEmpty())
    }

    @Test
    fun `daily challenge item is gated on its own flag`() {
        val withoutDaily = newWaysToPlaySection(FeatureFlag.entries.toSet() - FeatureFlag.DAILY_CHALLENGE)
        val withDaily = newWaysToPlaySection(FeatureFlag.entries.toSet())

        assertFalse(
            withoutDaily.items.any { it.titleRes == com.geoquiz.app.R.string.whats_new_mode_daily_challenge_title }
        )
        assertTrue(
            withDaily.items.any { it.titleRes == com.geoquiz.app.R.string.whats_new_mode_daily_challenge_title }
        )
    }
}
