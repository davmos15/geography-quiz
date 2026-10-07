package com.geoquiz.app.ui.results

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.testutil.TestQuizData.AUSTRIA
import com.geoquiz.app.testutil.TestQuizData.FRANCE
import com.geoquiz.app.testutil.TestQuizData.GERMANY
import com.geoquiz.app.testutil.TestQuizData.PERU
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnswerReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val completedQuizzes = FakeCompletedQuizRepository()
    private val countryRepository = mockk<CountryRepository>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { showFlags } returns flowOf(true)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { countryRepository.getCountriesByCodes(any()) } answers {
            val all = listOf(FRANCE, GERMANY, AUSTRIA, PERU).associateBy { it.code }
            firstArg<List<String>>().mapNotNull { all[it] }
        }
        every { countryRepository.getAllCountries() } returns flowOf(listOf(AUSTRIA, FRANCE, GERMANY, PERU))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(resultId: String?): AnswerReviewViewModel {
        val handle = SavedStateHandle(if (resultId != null) mapOf("resultId" to resultId) else emptyMap())
        val vm = AnswerReviewViewModel(handle, completedQuizzes, countryRepository, settingsRepository)
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    private fun AnswerReviewViewModel.loaded() = uiState.value as AnswerReviewUiState.Loaded

    @Test
    fun `loads the quiz countries by code, sorted by name, with answers marked`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(
            id = "r1",
            countryCodes = listOf("FRA", "DEU", "AUT"),
            answeredCodes = listOf("FRA", "AUT")
        )

        val state = viewModel("r1").loaded()

        assertEquals(listOf(AUSTRIA, FRANCE, GERMANY), state.countries)
        assertEquals(setOf("FRA", "AUT"), state.answeredCodes)
        assertEquals(2, state.answeredCount)
        assertEquals("All Countries", state.categoryName)
        assertEquals(QuizMode.COUNTRIES, state.quizMode)
        assertEquals(QuizCategory.AllCountries, state.category)
        coVerify(exactly = 1) { countryRepository.getCountriesByCodes(listOf("FRA", "DEU", "AUT")) }
    }

    @Test
    fun `capitals mode sorts by capital`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(id = "r1", quizModeId = "capitals")

        val state = viewModel("r1").loaded()

        // Berlin, Paris, Vienna
        assertEquals(listOf(GERMANY, FRANCE, AUSTRIA), state.countries)
        assertEquals(QuizMode.CAPITALS, state.quizMode)
    }

    @Test
    fun `a letter category round-trips for the review hints`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(
            id = "r1",
            categoryType = "startletter",
            categoryValue = "A",
            countryCodes = listOf("AUT")
        )

        val state = viewModel("r1").loaded()

        assertEquals(QuizCategory.StartingWithLetter('A'), state.category)
    }

    @Test
    fun `incorrect guesses are matched to any country by name or capital`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(
            id = "r1",
            incorrectGuessStrings = listOf(" peru ", "LIMA", "Narnia")
        )

        val state = viewModel("r1").loaded()

        assertEquals(
            listOf(
                IncorrectGuess(" peru ", PERU),
                IncorrectGuess("LIMA", PERU),
                IncorrectGuess("Narnia", null)
            ),
            state.incorrectGuesses
        )
    }

    @Test
    fun `a missing result shows the missing state`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(id = "newer")

        assertEquals(AnswerReviewUiState.Missing, viewModel("r1").uiState.value)
    }

    @Test
    fun `no id in the route shows the missing state`() {
        assertEquals(AnswerReviewUiState.Missing, viewModel(null).uiState.value)
    }

    @Test
    fun `findMatchingCountry ignores case and outer spaces`() {
        val all = listOf(FRANCE, PERU)
        assertEquals(FRANCE, AnswerReviewViewModel.findMatchingCountry("  FRANCE ", all))
        assertEquals(FRANCE, AnswerReviewViewModel.findMatchingCountry("paris", all))
        assertNull(AnswerReviewViewModel.findMatchingCountry("Fran", all))
    }
}
