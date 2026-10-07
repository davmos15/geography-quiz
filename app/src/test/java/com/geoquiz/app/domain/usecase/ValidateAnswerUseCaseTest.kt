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

class ValidateAnswerUseCaseTest {

    private lateinit var validateAnswer: ValidateAnswerUseCase
    private lateinit var repository: CountryRepository
    private val normalize = NormalizeInputUseCase()

    private val france = Country("FRA", "France", "French Republic", "Europe", "Western Europe", 6, "Paris")
    private val germany = Country("DEU", "Germany", "Federal Republic of Germany", "Europe", "Western Europe", 7, "Berlin")
    private val japan = Country("JPN", "Japan", "Japan", "Asia", "Eastern Asia", 5, "Tokyo")
    private val iceland = Country("ISL", "Iceland", "Iceland", "Europe", "Northern Europe", 7, "Reykjavik")
    private val ireland = Country("IRL", "Ireland", "Republic of Ireland", "Europe", "Northern Europe", 7, "Dublin")
    private val brazil = Country("BRA", "Brazil", "Federative Republic of Brazil", "Americas", "South America", 6, "Brasília")
    private val allCountries = listOf(france, germany, japan, iceland, ireland, brazil)

    private val quiz = Quiz(
        category = QuizCategory.AllCountries,
        countries = listOf(france, germany, japan, iceland, ireland)
    )

    @Before
    fun setUp() {
        repository = mockk()
        // Exact match on the normalised name, like the real alias table.
        coEvery { repository.findCountryByAnswer(any()) } answers {
            val input = normalize(firstArg<String>())
            allCountries.firstOrNull { normalize(it.name) == input }
        }
        coEvery { repository.getCountryAnswerAliases() } returns
            allCountries.map { AnswerAlias(normalize(it.name), it) }
        validateAnswer = ValidateAnswerUseCase(repository, normalize)
    }

    private suspend fun check(
        allowFuzzy: Boolean,
        answered: Set<String>,
        vararg cases: Pair<String, AnswerResult>
    ) {
        val state = QuizState(quiz = quiz, answeredCountries = answered)
        for ((input, expected) in cases) {
            assertEquals("'$input' (allowFuzzy=$allowFuzzy)", expected, validateAnswer(input, state, allowFuzzy))
        }
    }

    @Test
    fun `exact answers behave the same in both modes`() = runTest {
        for (allowFuzzy in listOf(true, false)) {
            check(
                allowFuzzy, answered = setOf("DEU"),
                "France" to AnswerResult.Correct("France"),
                "  FRANCE " to AnswerResult.Correct("France"),
                "Germany" to AnswerResult.AlreadyAnswered,
                "Brazil" to AnswerResult.Incorrect,      // valid country, not in this quiz
                "Atlantis" to AnswerResult.Incorrect,
                "   " to AnswerResult.Incorrect,
                // Exactly another country's name: that country, never a typo of this one.
                "Iceland" to AnswerResult.Correct("Iceland"),
                "Ireland" to AnswerResult.Correct("Ireland")
            )
        }
    }

    @Test
    fun `normal mode accepts one typo on a long name`() = runTest {
        check(
            allowFuzzy = true, answered = setOf("DEU"),
            "Germnay" to AnswerResult.AlreadyAnswered,              // swap, already answered
            "Icelnad" to AnswerResult.Correct("Iceland", viaTypo = true),
            "Irelandd" to AnswerResult.Correct("Ireland", viaTypo = true),
            "Gremany" to AnswerResult.AlreadyAnswered
        )
        check(
            allowFuzzy = true, answered = emptySet(),
            "Germanyy" to AnswerResult.Correct("Germany", viaTypo = true),
            "Gerrmany" to AnswerResult.Correct("Germany", viaTypo = true)
        )
    }

    @Test
    fun `normal mode typo of a country outside the quiz is Incorrect`() = runTest {
        val outside = Country("ARG", "Argentina", "Argentine Republic", "Americas", "South America", 9, "Buenos Aires")
        coEvery { repository.getCountryAnswerAliases() } returns
            (allCountries + outside).map { AnswerAlias(normalize(it.name), it) }
        check(allowFuzzy = true, answered = emptySet(), "Argentinia" to AnswerResult.Incorrect)
    }

    @Test
    fun `hard mode typo of a country outside the quiz is Incorrect`() = runTest {
        val outside = Country("ARG", "Argentina", "Argentine Republic", "Americas", "South America", 9, "Buenos Aires")
        coEvery { repository.getCountryAnswerAliases() } returns
            (allCountries + outside).map { AnswerAlias(normalize(it.name), it) }
        check(allowFuzzy = false, answered = emptySet(), "Argentinia" to AnswerResult.Incorrect)
    }

    @Test
    fun `hard mode never accepts a typo but gives a NearMiss`() = runTest {
        check(
            allowFuzzy = false, answered = setOf("DEU"),
            "Icelnad" to AnswerResult.NearMiss,
            "Irelandd" to AnswerResult.NearMiss,
            "Germnay" to AnswerResult.AlreadyAnswered   // nothing to hint: already answered
        )
    }

    @Test
    fun `ambiguous typo with no candidate in the quiz is Incorrect`() = runTest {
        val noIslands = QuizState(quiz = Quiz(QuizCategory.AllCountries, listOf(france, germany, japan)))
        for (allowFuzzy in listOf(true, false)) {
            assertEquals(AnswerResult.Incorrect, validateAnswer("Ixeland", noIslands, allowFuzzy))
        }
    }

    @Test
    fun `typo equally close to two countries is a NearMiss in both modes`() = runTest {
        for (allowFuzzy in listOf(true, false)) {
            check(allowFuzzy, answered = emptySet(), "Ixeland" to AnswerResult.NearMiss)
        }
    }

    @Test
    fun `short names and two typos are Incorrect in both modes`() = runTest {
        for (allowFuzzy in listOf(true, false)) {
            check(
                allowFuzzy, answered = emptySet(),
                "Frnace" to AnswerResult.Incorrect,   // France has only 6 letters
                "Japn" to AnswerResult.Incorrect,
                "Grmny" to AnswerResult.Incorrect,
                "Ilecnad" to AnswerResult.Incorrect
            )
        }
    }

    @Test
    fun `typo matching is skipped when there is an exact match`() = runTest {
        validateAnswer("France", QuizState(quiz = quiz), allowFuzzy = true)
        coVerify(exactly = 0) { repository.getCountryAnswerAliases() }
    }
}
