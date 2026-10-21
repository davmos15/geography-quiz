package com.geoquiz.app.ui.currencies

import com.geoquiz.app.domain.mode.currencies.CurrencyEntry
import com.geoquiz.app.domain.mode.currencies.CurrencyItem
import com.geoquiz.app.domain.mode.currencies.CurrencySymbolRules
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CurrencyRoundState.isCurrentChoice] (D50, extended by task P10-B to ambiguous-symbol
 * single-owner currencies - see [CurrencyEntry.isChoiceOnly]).
 */
class CurrencyRoundStateTest {

    private fun state(entry: CurrencyEntry, difficulty: Difficulty) = CurrencyRoundState(
        category = QuizCategory.AllCountries,
        difficulty = difficulty,
        items = listOf(CurrencyItem(country("ONE", "One", "One City"), entry))
    )

    @Test
    fun `a unique-symbol single-owner currency is typed at Normal and Hard`() {
        val entry = CurrencyEntry("PEN", "Peruvian sol", "S/", listOf("ONE"), shared = false, fact = null)

        assertFalse(state(entry, Difficulty.NORMAL).isCurrentChoice)
        assertFalse(state(entry, Difficulty.HARD).isCurrentChoice)
    }

    @Test
    fun `a unique-symbol single-owner currency is still a choice at Easy`() {
        val entry = CurrencyEntry("PEN", "Peruvian sol", "S/", listOf("ONE"), shared = false, fact = null)

        assertTrue(state(entry, Difficulty.EASY).isCurrentChoice)
    }

    @Test
    fun `a genuinely shared currency is a choice at every tier`() {
        val entry = CurrencyEntry("EUR", "Euro", "€", listOf("ONE", "TWO"), shared = true, fact = null)

        assertTrue(state(entry, Difficulty.NORMAL).isCurrentChoice)
        assertTrue(state(entry, Difficulty.HARD).isCurrentChoice)
    }

    @Test
    fun `a single-owner currency with an ambiguous symbol is a choice at every tier (task P10-B)`() {
        val lek = CurrencyEntry("ALL", "Albanian lek", "L", listOf("ONE"), shared = false, fact = null)
        val lempira = CurrencyEntry("HNL", "Honduran lempira", "L", listOf("TWO"), shared = false, fact = null)
        val classified = CurrencySymbolRules.classify(listOf(lek, lempira)).first { it.code == "ALL" }

        assertFalse("sanity: this currency is not shared", classified.shared)
        assertTrue(state(classified, Difficulty.NORMAL).isCurrentChoice)
        assertTrue(state(classified, Difficulty.HARD).isCurrentChoice)
    }
}
