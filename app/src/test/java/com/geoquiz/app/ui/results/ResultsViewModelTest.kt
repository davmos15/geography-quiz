package com.geoquiz.app.ui.results

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.db.ChallengeEntity
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.testutil.TestQuizData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val playGames = mockk<PlayGamesAchievementService> {
        every { playerName } returns MutableStateFlow("Sam")
    }
    private val challengeRepository = mockk<ChallengeRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(resultId: String?): ResultsViewModel {
        val handle = SavedStateHandle(if (resultId != null) mapOf("resultId" to resultId) else emptyMap())
        val vm = ResultsViewModel(handle, playGames, challengeRepository, completedQuizzes)
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `loads the stored result by id`() {
        val quiz = TestQuizData.completedQuiz(id = "r1")
        completedQuizzes.stored = quiz

        val vm = viewModel("r1")

        assertEquals(ResultsUiState.Loaded(quiz), vm.uiState.value)
    }

    @Test
    fun `a result that no longer exists shows the missing state`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(id = "newer")

        val vm = viewModel("r1")

        assertEquals(ResultsUiState.Missing, vm.uiState.value)
        coVerify(exactly = 0) { challengeRepository.updateMyResult(any(), any(), any(), any()) }
    }

    @Test
    fun `no id in the route shows the missing state`() {
        val vm = viewModel(null)

        assertEquals(ResultsUiState.Missing, vm.uiState.value)
    }

    @Test
    fun `an accepted challenge gets this result and is shown for comparison`() {
        val quiz = TestQuizData.completedQuiz(id = "r1", challengeId = "c1")
        completedQuizzes.stored = quiz
        val challenge = mockk<ChallengeEntity>()
        coEvery { challengeRepository.getChallengeById("c1") } returns challenge

        val vm = viewModel("r1")

        coVerify(exactly = 1) { challengeRepository.updateMyResult("c1", quiz.correct, quiz.total, quiz.timeSeconds) }
        assertEquals(challenge, vm.challengeResult.value)
    }

    @Test
    fun `recreating Results writes the same challenge values again`() {
        // updateMyResult is an UPDATE with the same values, so a second run changes nothing.
        val quiz = TestQuizData.completedQuiz(id = "r1", challengeId = "c1")
        completedQuizzes.stored = quiz

        viewModel("r1")
        viewModel("r1")

        coVerify(exactly = 2) { challengeRepository.updateMyResult("c1", quiz.correct, quiz.total, quiz.timeSeconds) }
    }

    @Test
    fun `no challenge, no challenge update`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(id = "r1", challengeId = null)

        val vm = viewModel("r1")

        coVerify(exactly = 0) { challengeRepository.updateMyResult(any(), any(), any(), any()) }
        assertNull(vm.challengeResult.value)
    }
}
