package com.geoquiz.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinnedCategoryTest {

    @Test
    fun `a classic mode and an offered category is a valid pin`() {
        val pin = PinnedCategory.validOrNull("countries", "region", "Africa")

        assertEquals(PinnedCategory("countries", "region", "Africa"), pin)
    }

    @Test
    fun `a category not offered in the mode is rejected`() {
        assertNull(PinnedCategory.validOrNull("countries", "flagcolor", "red"))
        assertNull(PinnedCategory.validOrNull("flags", "region", "Africa"))
    }

    @Test
    fun `a practice set is never a pin`() {
        assertNull(PinnedCategory.validOrNull("countries", "practice", "FRA+DEU"))
    }

    @Test
    fun `a malformed category is rejected`() {
        assertNull(PinnedCategory.validOrNull("countries", "region", ""))
        assertNull(PinnedCategory.validOrNull("countries", "nonsense", "x"))
    }

    // D35: new modes have no pins (Phase 5 task 5.0). validOrNull only recognises QuizMode ids.

    @Test
    fun `silhouettes and tap_map are rejected, even for a category they will offer`() {
        assertNull(PinnedCategory.validOrNull("silhouettes", "all", "_"))
        assertNull(PinnedCategory.validOrNull("silhouettes", "region", "Africa"))
        assertNull(PinnedCategory.validOrNull("tap_map", "all", "_"))
        assertNull(PinnedCategory.validOrNull("tap_map", "region", "Africa"))
    }

    @Test
    fun `an unknown mode id is rejected`() {
        assertNull(PinnedCategory.validOrNull("not-a-mode", "all", "_"))
    }

    @Test
    fun `round trips through its key`() {
        val pin = PinnedCategory("capitals", "startletter", "B")

        val restored = PinnedCategory.fromKeyOrNull(pin.key)

        assertEquals(pin, restored)
    }

    @Test
    fun `a key for a rejected mode or category does not round trip`() {
        assertNull(PinnedCategory.fromKeyOrNull("silhouettes|all|_"))
        assertNull(PinnedCategory.fromKeyOrNull("countries|practice|FRA"))
        assertNull(PinnedCategory.fromKeyOrNull("not|enough"))
    }
}
