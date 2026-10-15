package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CurrencyGameMode]'s own wiring: spec and category offers. Per-item logic lives in the other tests. */
class CurrencyGameModeTest {

    private val countriesQuestionGenerator = mockk<CountriesQuestionGenerator>()
    private val countryRepository = mockk<CountryRepository>()
    private val currencyRepository = mockk<CurrencyRepository>()
    private val validateAnswer = mockk<ValidateAnswerUseCase>()
    private val calculateScore = CalculateScoreUseCase()

    private fun mode() = CurrencyGameMode(
        countriesQuestionGenerator,
        countryRepository,
        currencyRepository,
        validateAnswer,
        calculateScore
    )

    @Test
    fun `offers all countries, region, subregion and practice only`() {
        val m = mode()
        assertTrue(m.offers(QuizCategory.AllCountries))
        assertTrue(m.offers(QuizCategory.ByRegion("Europe")))
        assertTrue(m.offers(QuizCategory.BySubregion("Southern Europe")))
        assertTrue(m.offers(QuizCategory.Practice(listOf("FRA"))))

        assertFalse(m.offers(QuizCategory.StartingWithLetter('A')))
        assertFalse(m.offers(QuizCategory.IslandCountries))
        assertFalse(m.offers(QuizCategory.FlagSingleColor("red")))
    }

    @Test
    fun `spec has no hints, every difficulty and the Currencies feature flag`() {
        val spec = CurrencyGameMode.SPEC
        assertEquals(emptySet<Any>(), spec.hintTypes)
        assertEquals(Difficulty.entries.toSet(), spec.supportedDifficulties)
        assertEquals(FeatureFlag.CURRENCIES, spec.featureFlag)
        assertEquals(AnswerType.TYPED_NAME, spec.answerType)
        assertEquals(CurrencyGameMode.MODE_ID, spec.id)
    }

    @Test
    fun `mode id is stable`() {
        assertEquals("currencies", CurrencyGameMode.MODE_ID)
    }
}
