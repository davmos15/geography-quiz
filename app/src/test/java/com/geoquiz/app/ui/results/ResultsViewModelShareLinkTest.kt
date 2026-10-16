package com.geoquiz.app.ui.results

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.service.AdManager
import com.geoquiz.app.data.service.InterstitialPolicy
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.challenge.ChallengeLinkParseResult
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Share links from Results: signed with the injected signer and saved as outgoing challenges. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ResultsViewModelShareLinkTest {

    private val dispatcher = StandardTestDispatcher()
    private val signer = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray())
    private val playGames = mockk<PlayGamesAchievementService> {
        every { playerName } returns MutableStateFlow("Sam")
    }
    private val challengeRepository = mockk<ChallengeRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ResultsViewModel(
        SavedStateHandle(),
        playGames,
        challengeRepository,
        FakeCompletedQuizRepository(),
        mockk<AdManager>(relaxed = true),
        mockk<InterstitialPolicy>(relaxed = true),
        signer,
        com.geoquiz.app.testutil.TestGameModes.registry()
    )

    @Test
    fun `shared result link is signed, carries the score and is saved under the same id`() {
        val vm = viewModel()
        // Classic mode: never null (see "a new mode's share link is null" below).
        val url = vm.createChallengeShareUrl("region", "Europe", "capitals", score = 40, total = 44, time = 312)!!
        dispatcher.scheduler.advanceUntilIdle()

        val result = ChallengeDeepLink.parse(Uri.parse(url.toString()), signer)
        result as ChallengeLinkParseResult.Valid
        assertTrue(result.scoreVerified)
        with(result.link) {
            assertEquals("region", categoryType)
            assertEquals("Europe", categoryValue)
            assertEquals("Sam", challengerName)
            assertEquals(40, challengerScore)
            assertEquals(44, challengerTotal)
            assertEquals(312, challengerTime)
            assertEquals("capitals", quizMode)
        }
        coVerify(exactly = 1) {
            challengeRepository.createOutgoingChallenge(
                id = result.link.challengeId,
                categoryType = "region",
                categoryValue = "Europe",
                categoryDisplayName = any(),
                quizMode = "capitals",
                challengerName = "Sam",
                score = 40,
                total = 44,
                time = 312
            )
        }
    }

    @Test
    fun `plain challenge link has no score`() {
        val url = viewModel().createChallengeShareUrl("region", "Europe", "countries", null, null, null)!!
        dispatcher.scheduler.advanceUntilIdle()

        val result = ChallengeDeepLink.parse(Uri.parse(url.toString()), signer)
        result as ChallengeLinkParseResult.Valid
        assertTrue(result.scoreVerified)
        assertNull(result.link.challengerScore)
        coVerify(exactly = 1) {
            challengeRepository.createOutgoingChallenge(
                id = result.link.challengeId,
                categoryType = "region",
                categoryValue = "Europe",
                categoryDisplayName = any(),
                quizMode = "countries",
                challengerName = "Sam",
                score = null,
                total = null,
                time = null
            )
        }
    }

    @Test
    fun `link does not verify under a different key`() {
        val url = viewModel().createChallengeShareUrl("region", "Europe", "countries", 40, 44, 312)!!
        val other = ChallengeLinkSigner("another-key-0123456789abcdef".toByteArray())

        val result = ChallengeDeepLink.parse(Uri.parse(url.toString()), other)
        result as ChallengeLinkParseResult.Valid
        assertFalse(result.scoreVerified)
        assertNull(result.link.challengerScore)
    }

    // D35: new (non-classic) modes never save an outgoing challenge or sign a link.

    @Test
    fun `a new mode's share link is null and no outgoing challenge is saved`() {
        val url = viewModel().createChallengeShareUrl(
            "region", "Europe", "silhouettes", score = 40, total = 44, time = 312
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(url)
        coVerify(exactly = 0) {
            challengeRepository.createOutgoingChallenge(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `tap_map's share link is also null`() {
        val url = viewModel().createChallengeShareUrl(
            "map_region", "world", "tap_map", score = null, total = null, time = null
        )

        assertNull(url)
    }

    private fun loadedViewModel(quizModeId: String, handle: SavedStateHandle = SavedStateHandle()): ResultsViewModel {
        val quiz = com.geoquiz.app.testutil.TestQuizData.completedQuiz(quizModeId = quizModeId)
        handle[com.geoquiz.app.ui.navigation.Screen.Results.ARG_RESULT_ID] = quiz.id
        return ResultsViewModel(
            handle,
            playGames,
            challengeRepository,
            FakeCompletedQuizRepository(quiz),
            mockk<AdManager>(relaxed = true),
            mockk<InterstitialPolicy>(relaxed = true),
            signer,
            com.geoquiz.app.testutil.TestGameModes.registry()
        ).also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun `opening the share preview twice records one outgoing challenge`() {
        val vm = loadedViewModel("countries")

        vm.openSharePreview()
        val link = vm.shareLink.value
        vm.closeSharePreview()
        vm.openSharePreview()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.sharePreviewOpen.value)
        assertTrue(link != null)
        assertEquals(link, vm.shareLink.value)
        coVerify(exactly = 1) {
            challengeRepository.createOutgoingChallenge(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `the share preview and its link survive recreation`() {
        val handle = SavedStateHandle()
        val first = loadedViewModel("countries", handle)
        first.openSharePreview()
        val link = first.shareLink.value

        val recreated = loadedViewModel("countries", handle)
        recreated.openSharePreview()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(recreated.sharePreviewOpen.value)
        assertEquals(link, recreated.shareLink.value)
        coVerify(exactly = 1) {
            challengeRepository.createOutgoingChallenge(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a new mode's share preview has no challenge link and records nothing`() {
        val vm = loadedViewModel("silhouettes")

        vm.openSharePreview()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.sharePreviewOpen.value)
        assertNull(vm.shareLink.value)
        coVerify(exactly = 0) {
            challengeRepository.createOutgoingChallenge(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }
}
