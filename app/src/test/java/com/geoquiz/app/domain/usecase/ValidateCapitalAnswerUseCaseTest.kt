package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ValidateCapitalAnswerUseCaseTest {

    private lateinit var validateCapital: ValidateCapitalAnswerUseCase
    private lateinit var repository: CountryRepository
    private val normalize = NormalizeInputUseCase()

    private val jamaica = Country("JAM", "Jamaica", "Jamaica", "Americas", "Caribbean", 7, "Kingston")
    private val stVincent = Country("VCT", "Saint Vincent and the Grenadines", "Saint Vincent and the Grenadines", "Americas", "Caribbean", 32, "Kingstown")
    private val grenada = Country("GRD", "Grenada", "Grenada", "Americas", "Caribbean", 7, "St. George's")
    private val australia = Country("AUS", "Australia", "Commonwealth of Australia", "Oceania", "Australia and New Zealand", 9, "Canberra")
    private val france = Country("FRA", "France", "French Republic", "Europe", "Western Europe", 6, "Paris")
    private val norway = Country("NOR", "Norway", "Kingdom of Norway", "Europe", "Northern Europe", 6, "Oslo")
    private val allCountries = listOf(jamaica, stVincent, grenada, australia, france, norway)

    private val quiz = Quiz(
        category = QuizCategory.AllCountries,
        countries = listOf(jamaica, stVincent, grenada, australia, france)
    )

    @Before
    fun setUp() {
        repository = mockk()
        coEvery { repository.findCountryByCapitalAnswer(any()) } answers {
            val input = normalize(firstArg<String>())
            allCountries.firstOrNull { normalize(it.capital) == input }
        }
        coEvery { repository.getCapitalAnswerAliases() } returns
            allCountries.map { AnswerAlias(normalize(it.capital), it) }
        validateCapital = ValidateCapitalAnswerUseCase(repository, normalize)
    }

    private suspend fun check(
        allowFuzzy: Boolean,
        answered: Set<String>,
        vararg cases: Pair<String, AnswerResult>
    ) {
        val state = QuizState(quiz = quiz, answeredCountries = answered)
        for ((input, expected) in cases) {
            assertEquals("'$input' (allowFuzzy=$allowFuzzy)", expected, validateCapital(input, state, allowFuzzy))
        }
    }

    @Test
    fun `exact capitals behave the same in both modes`() = runTest {
        for (allowFuzzy in listOf(true, false)) {
            check(
                allowFuzzy, answered = setOf("FRA"),
                "Kingston" to AnswerResult.Correct("Jamaica"),
                "Kingstown" to AnswerResult.Correct("Saint Vincent and the Grenadines"),
                "Saint George's" to AnswerResult.Correct("Grenada"),
                "St Georges" to AnswerResult.Correct("Grenada"),
                "paris" to AnswerResult.AlreadyAnswered,
                "Oslo" to AnswerResult.Incorrect,    // not in this quiz
                "Atlantis" to AnswerResult.Incorrect,
                "" to AnswerResult.Incorrect
            )
        }
    }

    @Test
    fun `normal mode accepts one typo on a long capital`() = runTest {
        check(
            allowFuzzy = true, answered = emptySet(),
            "Canbera" to AnswerResult.Correct("Australia", viaTypo = true),
            "Canberar" to AnswerResult.Correct("Australia", viaTypo = true),
            "Kingstowm" to AnswerResult.Correct("Saint Vincent and the Grenadines", viaTypo = true),
            // One edit from "kingston", two from "kingstown".
            "Kingstn" to AnswerResult.Correct("Jamaica", viaTypo = true)
        )
        check(allowFuzzy = true, answered = setOf("AUS"), "Canbera" to AnswerResult.AlreadyAnswered)
    }

    @Test
    fun `hard mode gives a NearMiss for a typo`() = runTest {
        check(
            allowFuzzy = false, answered = emptySet(),
            "Canbera" to AnswerResult.NearMiss,
            "Kingstowm" to AnswerResult.NearMiss
        )
    }

    @Test
    fun `typo between Kingston and Kingstown is a NearMiss in both modes`() = runTest {
        for (allowFuzzy in listOf(true, false)) {
            check(allowFuzzy, answered = emptySet(), "Kingstonw" to AnswerResult.NearMiss)
        }
    }

    @Test
    fun `short capitals are never matched loosely`() = runTest {
        check(
            allowFuzzy = true, answered = emptySet(),
            "Pariss" to AnswerResult.Incorrect,
            "Olso" to AnswerResult.Incorrect
        )
    }

    @Test
    fun `typo matching uses capital aliases, not country names`() = runTest {
        validateCapital("Australiaa", QuizState(quiz = quiz), allowFuzzy = true)
        coVerify(exactly = 0) { repository.getCountryAnswerAliases() }
        assertEquals(
            AnswerResult.Incorrect,
            validateCapital("Australiaa", QuizState(quiz = quiz), allowFuzzy = true)
        )
    }
}
