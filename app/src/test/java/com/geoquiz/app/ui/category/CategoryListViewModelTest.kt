package com.geoquiz.app.ui.category

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.challenge.ChallengeLinkParseResult
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.testutil.TestGameModes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Challenge links from the category list: signed with the injected signer and saved. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CategoryListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val signer = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray())
    private val countries = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(emptyList())
    }
    private val history = mockk<QuizHistoryRepository> {
        coEvery { getAllBestScoresForMode(any()) } returns emptyList()
        every { masteryStarsForMode(any()) } returns flowOf(emptyMap())
    }
    private val challengeRepository = mockk<ChallengeRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService> {
        every { playerName } returns MutableStateFlow("Sam")
    }
    private val settings = mockk<SettingsRepository> {
        every { difficulty } returns flowOf(Difficulty.DEFAULT)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(quizMode: String) = CategoryListViewModel(
        savedStateHandle = SavedStateHandle(mapOf("quizMode" to quizMode, "groupId" to "")),
        repository = countries,
        flagColorDao = mockk<FlagColorDao>(relaxed = true),
        flagElementDao = mockk<FlagElementDao>(relaxed = true),
        quizHistoryRepository = history,
        challengeRepository = challengeRepository,
        playGamesService = playGames,
        settingsRepository = settings,
        challengeLinkSigner = signer,
        gameModes = TestGameModes.registry()
    )

    private fun parse(url: Uri): ChallengeLinkParseResult.Valid =
        ChallengeDeepLink.parse(Uri.parse(url.toString()), signer) as ChallengeLinkParseResult.Valid

    @Test
    fun `challenge link is signed for this mode and saved under the same id`() {
        val vm = viewModel("flags")
        val result = parse(vm.createChallengeShareUrl("flagcombo", "red+white"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(result.scoreVerified)
        with(result.link) {
            assertEquals("flagcombo", categoryType)
            assertEquals("red+white", categoryValue)
            assertEquals("Sam", challengerName)
            assertNull(challengerScore)
            assertNull(challengerTotal)
            assertNull(challengerTime)
            assertEquals("flags", quizMode)
        }
        coVerify(exactly = 1) {
            challengeRepository.createOutgoingChallenge(
                id = result.link.challengeId,
                categoryType = "flagcombo",
                categoryValue = "red+white",
                categoryDisplayName = any(),
                quizMode = "flags",
                challengerName = "Sam",
                score = null,
                total = null,
                time = null
            )
        }
    }

    @Test
    fun `each challenge gets a fresh id`() {
        val vm = viewModel("countries")
        val first = parse(vm.createChallengeShareUrl("region", "Europe"))
        val second = parse(vm.createChallengeShareUrl("region", "Europe"))
        assertNotEquals(first.link.challengeId, second.link.challengeId)
    }

    @Test
    fun `link does not verify under a different key`() {
        val url = viewModel("countries").createChallengeShareUrl("region", "Europe")
        val other = ChallengeLinkSigner("another-key-0123456789abcdef".toByteArray())

        val result = ChallengeDeepLink.parse(Uri.parse(url.toString()), other)
        result as ChallengeLinkParseResult.Valid
        assertFalse(result.scoreVerified)
    }
}
