package com.geoquiz.app.ui.results

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.db.ChallengeEntity
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.service.AdManager
import com.geoquiz.app.data.service.InterstitialPolicy
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.testutil.TestQuizData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val adManager = mockk<AdManager>(relaxed = true)
    private val policy = mockk<InterstitialPolicy>(relaxed = true)
    private val activity = mockk<Activity>()
    private val signer = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(resultId: String?): ResultsViewModel =
        viewModel(SavedStateHandle(if (resultId != null) mapOf("resultId" to resultId) else emptyMap()))

    private fun viewModel(handle: SavedStateHandle): ResultsViewModel {
        val vm = ResultsViewModel(handle, playGames, challengeRepository, completedQuizzes, adManager, policy, signer)
        dispatcher.scheduler.advanceUntilIdle()
        return vm
    }

    /** A copy of the handle's contents, as restored after process death. */
    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

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

    @Test
    fun `a fresh result asks the policy once with the quiz duration`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(id = "r1").copy(timeSeconds = 75)
        every { policy.onQuizCompleted(75) } returns true

        val vm = viewModel("r1")

        verify(exactly = 1) { policy.onQuizCompleted(75) }
        assertTrue(vm.interstitialDue.value)
    }

    @Test
    fun `no interstitial due when the policy says no`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(id = "r1").copy(timeSeconds = 30)
        every { policy.onQuizCompleted(any()) } returns false

        val vm = viewModel("r1")
        vm.showDueInterstitial(activity)

        assertFalse(vm.interstitialDue.value)
        verify(exactly = 0) { adManager.showInterstitial(any(), any(), any()) }
    }

    @Test
    fun `Results restored after process death does not ask the policy again`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(id = "r1").copy(timeSeconds = 120)
        every { policy.onQuizCompleted(any()) } returns true
        val handle = SavedStateHandle(mapOf("resultId" to "r1"))
        viewModel(handle)

        val restored = viewModel(afterProcessDeath(handle))

        verify(exactly = 1) { policy.onQuizCompleted(any()) }
        assertFalse(restored.interstitialDue.value)
        restored.showDueInterstitial(activity)
        verify(exactly = 0) { adManager.showInterstitial(any(), any(), any()) }
    }

    @Test
    fun `a missing result never asks the policy`() {
        viewModel("gone")

        verify(exactly = 0) { policy.onQuizCompleted(any()) }
    }

    @Test
    fun `the due interstitial is shown once and only a shown ad resets the cap`() {
        completedQuizzes.stored = TestQuizData.completedQuiz(id = "r1").copy(timeSeconds = 120)
        every { policy.onQuizCompleted(any()) } returns true
        val onShown = slot<() -> Unit>()
        every { adManager.showInterstitial(activity, capture(onShown), any()) } returns Unit
        val vm = viewModel("r1")

        vm.showDueInterstitial(activity)
        vm.showDueInterstitial(activity)

        verify(exactly = 1) { adManager.showInterstitial(activity, any(), any()) }
        assertFalse(vm.interstitialDue.value)
        verify(exactly = 0) { policy.onInterstitialShown() }

        onShown.captured()

        verify(exactly = 1) { policy.onInterstitialShown() }
    }
}
