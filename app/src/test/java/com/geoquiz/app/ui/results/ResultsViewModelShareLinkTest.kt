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
        signer
    )

    @Test
    fun `shared result link is signed, carries the score and is saved under the same id`() {
        val vm = viewModel()
        val url = vm.createChallengeShareUrl("region", "Europe", "capitals", score = 40, total = 44, time = 312)
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
        val url = viewModel().createChallengeShareUrl("region", "Europe", "countries", null, null, null)
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
        val url = viewModel().createChallengeShareUrl("region", "Europe", "countries", 40, 44, 312)
        val other = ChallengeLinkSigner("another-key-0123456789abcdef".toByteArray())

        val result = ChallengeDeepLink.parse(Uri.parse(url.toString()), other)
        result as ChallengeLinkParseResult.Valid
        assertFalse(result.scoreVerified)
        assertNull(result.link.challengerScore)
    }
}
