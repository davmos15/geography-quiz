package com.geoquiz.app.domain.mode

import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagColorEntity
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.data.local.db.FlagElementEntity
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.GetCountriesForCapitalQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.testutil.TestGameModes
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classic generators must return exactly what QuizViewModel.loadQuiz chose before the mode
 * framework (3.1). [legacyChoice] is that code, kept verbatim; both run on the real use cases
 * over the same small data set.
 */
class ClassicQuestionGeneratorsTest {

    private val countries: List<Country> = listOf(
        country("FRA", "France", "Paris"),
        country("AUT", "Austria", "Vienna"),
        country("ALB", "Albania", "Tirana"),
        country("DJI", "Djibouti", "Djibouti", region = "Africa"),
        country("MEX", "Mexico", "Mexico City", region = "Americas"),
        country("ATA", "Antarctica", "", region = "Antarctic"), // no capital: never in Capitals
        country("JPN", "Japan", "Tokyo", region = "Asia")
    )

    private val repository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(countries)
    }
    private val flagColorDao = mockk<FlagColorDao> {
        coEvery { getAllMappings() } returns listOf(
            FlagColorEntity("FRA", "red"), FlagColorEntity("FRA", "white"), FlagColorEntity("FRA", "blue"),
            FlagColorEntity("AUT", "red"), FlagColorEntity("AUT", "white"),
            FlagColorEntity("ALB", "red"), FlagColorEntity("ALB", "black"),
            FlagColorEntity("JPN", "red"), FlagColorEntity("JPN", "white")
        )
    }
    private val flagElementDao = mockk<FlagElementDao> {
        coEvery { getAllMappings() } returns listOf(
            FlagElementEntity("ALB", "eagle"), FlagElementEntity("MEX", "eagle"), FlagElementEntity("DJI", "star")
        )
    }

    private val getCountriesForQuiz = GetCountriesForQuizUseCase(repository)
    private val getCountriesForCapitalQuiz = GetCountriesForCapitalQuizUseCase(repository)
    private val getCountriesForFlagQuiz = GetCountriesForFlagQuizUseCase(repository, flagColorDao, flagElementDao)

    private val registry = TestGameModes.registry(
        getCountriesForQuiz = getCountriesForQuiz,
        getCountriesForCapitalQuiz = getCountriesForCapitalQuiz,
        getCountriesForFlagQuiz = getCountriesForFlagQuiz
    )

    /** QuizViewModel.loadQuiz before 3.1, verbatim apart from the names. */
    private suspend fun legacyChoice(quizMode: QuizMode, category: QuizCategory): List<Country> {
        val isFlagSpecificCategory = category is QuizCategory.FlagSingleColor ||
            category is QuizCategory.FlagColorCombo ||
            category is QuizCategory.FlagColorCount ||
            category is QuizCategory.FlagElement
        return when {
            isFlagSpecificCategory -> getCountriesForFlagQuiz(category)
            quizMode == QuizMode.CAPITALS -> getCountriesForCapitalQuiz(category)
            else -> getCountriesForQuiz(category)
        }
    }

    private val categories: List<QuizCategory> = listOf(
        QuizCategory.AllCountries,
        QuizCategory.StartingWithLetter('A'),
        QuizCategory.EndingWithLetter('o'),
        QuizCategory.ByRegion("Europe"),
        QuizCategory.ByWordCount(2, "Two words"),
        QuizCategory.EndingInVowel, // country names only: empty in Capitals
        QuizCategory.CapitalMatchesCountry, // the capital-only category
        QuizCategory.FlagSingleColor("red"),
        QuizCategory.FlagColorCombo(listOf("red", "white")),
        QuizCategory.FlagColorCount(2),
        QuizCategory.FlagElement("eagle")
    )

    @Test
    fun `every classic mode returns the legacy list for every category`() = runTest {
        for (mode in QuizMode.entries) {
            for (category in categories) {
                assertEquals(
                    "${mode.id} / $category",
                    legacyChoice(mode, category),
                    registry[mode].generator.items(category)
                )
            }
        }
    }

    @Test
    fun `a flag category uses the flag filter in Countries and Flags mode`() = runTest {
        val red = QuizCategory.FlagSingleColor("red")
        val expected = listOf("FRA", "AUT", "ALB", "JPN")
        assertEquals(expected, registry[QuizMode.COUNTRIES].generator.items(red).map { it.code })
        assertEquals(expected, registry[QuizMode.FLAGS].generator.items(red).map { it.code })
    }

    @Test
    fun `the capital-only category matches capitals named after the country`() = runTest {
        val items = registry[QuizMode.CAPITALS].generator.items(QuizCategory.CapitalMatchesCountry)
        assertEquals(listOf("DJI", "MEX"), items.map { it.code })
    }

    @Test
    fun `Capitals leaves out countries without a capital and filters on the capital name`() = runTest {
        val all = registry[QuizMode.CAPITALS].generator.items(QuizCategory.AllCountries)
        assertTrue(all.none { it.code == "ATA" })
        val startingWithT = registry[QuizMode.CAPITALS].generator.items(QuizCategory.StartingWithLetter('T'))
        assertEquals(listOf("ALB", "JPN"), startingWithT.map { it.code })
    }
}
