package com.geoquiz.app.ui.category

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.db.QuizBestScore
import com.geoquiz.app.data.local.preferences.PinnedCategoriesRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.challenge.ChallengeLinkParseResult
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FlagCategoryGroup
import com.geoquiz.app.domain.model.PinnedCategory
import com.geoquiz.app.domain.model.QuizMode
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

/**
 * Challenge links from the category list (signed with the injected signer and saved), and the
 * options it lists, which must be unchanged by the 3.4b extraction into [CategoryOptionsBuilder].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CategoryListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val signer = ChallengeLinkSigner("test-key-0123456789abcdef".toByteArray())
    private val countries = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(emptyList())
    }
    private val bestScores = MutableStateFlow<Map<String, QuizBestScore>>(emptyMap())
    private val stars = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val history = mockk<QuizHistoryRepository> {
        every { bestScoresForMode(any()) } returns bestScores
        every { masteryStarsForMode(any()) } returns stars
    }

    /** Pins held in memory; [PinnedCategoriesRepository.setPinned] updates them like the real one. */
    private val pins = MutableStateFlow<List<PinnedCategory>>(emptyList())
    private val pinnedRepository = mockk<PinnedCategoriesRepository> {
        every { pinnedCategories } returns pins
        coEvery { setPinned(any(), any(), any(), any()) } answers {
            val pin = PinnedCategory(firstArg(), secondArg(), thirdArg())
            pins.value = if (arg<Boolean>(3)) (pins.value - pin) + pin else pins.value - pin
        }
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

    private fun viewModel(quizMode: String, groupId: String = "") = CategoryListViewModel(
        savedStateHandle = SavedStateHandle(mapOf("quizMode" to quizMode, "groupId" to groupId)),
        repository = countries,
        optionsBuilder = CategoryOptionsFixture.builder(),
        quizHistoryRepository = history,
        challengeRepository = challengeRepository,
        playGamesService = playGames,
        settingsRepository = settings,
        pinnedCategoriesRepository = pinnedRepository,
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

    /** The options the category list shows for [groupId], over [CategoryOptionsFixture]. */
    private fun listed(quizMode: String, groupId: String): List<QuizOptionInfo> {
        every { countries.getAllCountries() } returns flowOf(CategoryOptionsFixture.COUNTRIES)
        val vm = viewModel(quizMode, groupId)
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        return state.quizOptions
    }

    private fun legacy(mode: QuizMode, groupId: String) =
        LegacyCategoryOptions(mode, CategoryOptionsFixture.FLAG_COLOURS, CategoryOptionsFixture.FLAG_ELEMENTS)
            .options(groupId, CategoryOptionsFixture.COUNTRIES)

    @Test
    fun `category list options are unchanged for countries, capitals and flags`() {
        val cases = CategoryGroup.entries.flatMap { listOf(QuizMode.COUNTRIES to it.id, QuizMode.CAPITALS to it.id) } +
            FlagCategoryGroup.entries.map { QuizMode.FLAGS to it.id }
        for ((mode, groupId) in cases) {
            assertEquals("$mode/$groupId", legacy(mode, groupId), listed(mode.id, groupId))
        }
    }

    @Test
    fun `group name and description still come from the group`() {
        listed("countries", CategoryGroup.LETTER_PATTERNS.id)
        val vm = viewModel("flags", FlagCategoryGroup.FLAG_ELEMENTS.id)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FlagCategoryGroup.FLAG_ELEMENTS.displayName, vm.uiState.value.groupName)
        assertEquals(FlagCategoryGroup.FLAG_ELEMENTS.description, vm.uiState.value.groupDescription)
    }

    // Live best scores and pins (3.4c)

    private fun loadedRegions(quizMode: String = "countries"): CategoryListViewModel {
        every { countries.getAllCountries() } returns flowOf(CategoryOptionsFixture.COUNTRIES)
        val vm = viewModel(quizMode, CategoryGroup.REGIONS.id)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        return vm
    }

    private fun CategoryListViewModel.option(value: String): QuizOptionInfo =
        uiState.value.quizOptions.single { it.categoryValue == value }

    @Test
    fun `best score updates live when history emits`() {
        val vm = loadedRegions()
        with(vm.option("Africa")) {
            assertFalse(isCompleted)
            assertNull(bestScore)
            assertNull(bestCorrect)
            assertNull(bestTotal)
        }

        // The player finishes an Africa quiz and comes back
        bestScores.value = mapOf("region|Africa" to QuizBestScore("region", "Africa", 3.0, 3, 4))
        stars.value = mapOf("region|Africa" to 1)
        dispatcher.scheduler.advanceUntilIdle()

        with(vm.option("Africa")) {
            assertTrue(isCompleted)
            assertEquals(3.0, bestScore!!, 0.0)
            assertEquals(3, bestCorrect)
            assertEquals(4, bestTotal)
            assertEquals(1, masteryStars)
        }
        assertFalse(vm.option("Europe").isCompleted)

        // A better result replaces it
        bestScores.value = mapOf("region|Africa" to QuizBestScore("region", "Africa", 4.8, 4, 4))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, vm.option("Africa").bestCorrect)
        assertEquals(4.8, vm.option("Africa").bestScore!!, 0.0)
    }

    @Test
    fun `hide completed follows live best scores`() {
        val vm = loadedRegions()
        vm.toggleHideCompleted()
        bestScores.value = mapOf("region|Europe" to QuizBestScore("region", "Europe", 1.0, 1, 4))
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.hideCompleted)
        assertTrue(state.quizOptions.single { it.categoryValue == "Europe" }.isCompleted)
    }

    @Test
    fun `toggling a pin pins and unpins the category in this mode`() {
        val vm = loadedRegions()
        assertFalse(vm.option("Africa").isPinned)

        vm.onTogglePin(vm.option("Africa"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.option("Africa").isPinned)
        assertFalse(vm.option("Europe").isPinned)
        assertEquals(listOf(PinnedCategory("countries", "region", "Africa")), pins.value)

        vm.onTogglePin(vm.option("Africa"))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.option("Africa").isPinned)
        assertEquals(emptyList<PinnedCategory>(), pins.value)
    }

    @Test
    fun `pins of other modes do not mark this mode's rows`() {
        pins.value = listOf(PinnedCategory("capitals", "region", "Africa"), PinnedCategory("countries", "region", "Asia"))
        val vm = loadedRegions("countries")

        assertFalse(vm.option("Africa").isPinned)
        assertTrue(vm.option("Asia").isPinned)

        vm.onTogglePin(vm.option("Africa"))
        dispatcher.scheduler.advanceUntilIdle()
        coVerify { pinnedRepository.setPinned("countries", "region", "Africa", true) }
    }
}
