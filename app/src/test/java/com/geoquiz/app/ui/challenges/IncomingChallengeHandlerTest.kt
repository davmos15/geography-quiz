package com.geoquiz.app.ui.challenges

import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.domain.challenge.ChallengeLinkParseResult
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.GetCountriesForCapitalQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingChallengeHandlerTest {

    private val repository = mockk<ChallengeRepository>(relaxed = true)
    private val countriesQuiz = mockk<GetCountriesForQuizUseCase>()
    private val capitalsQuiz = mockk<GetCountriesForCapitalQuizUseCase>()
    private val flagsQuiz = mockk<GetCountriesForFlagQuizUseCase>()
    private val handler = IncomingChallengeHandler(repository, countriesQuiz, capitalsQuiz, flagsQuiz)

    private val country = mockk<Country>()

    private val link = ChallengeDeepLink(
        challengeId = "abc-123",
        categoryType = "region",
        categoryValue = "Europe",
        challengerName = "Dav",
        challengerScore = 40,
        challengerTotal = 44,
        challengerTime = 312,
        quizMode = "countries"
    )

    @Test
    fun `invalid parse result is rejected and nothing is saved`() = runTest {
        val outcome = handler.handle(ChallengeLinkParseResult.Invalid("bad id"))
        assertEquals(IncomingChallengeOutcome.Rejected("bad id"), outcome)
        coVerify(exactly = 0) { repository.createIncomingChallenge(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `valid link is saved with the category display name`() = runTest {
        coEvery { countriesQuiz(any()) } returns listOf(country)
        val outcome = handler.handle(
            ChallengeLinkParseResult.Valid(link, QuizCategory.ByRegion("Europe"), scoreVerified = true)
        )
        assertEquals(IncomingChallengeOutcome.Accepted(link, scoreVerified = true), outcome)
        coVerify {
            repository.createIncomingChallenge(
                id = "abc-123",
                categoryType = "region",
                categoryValue = "Europe",
                categoryDisplayName = "Europe",
                quizMode = "countries",
                challengerName = "Dav",
                challengerScore = 40,
                challengerTotal = 44,
                challengerTime = 312
            )
        }
    }

    @Test
    fun `category with no countries is rejected`() = runTest {
        coEvery { countriesQuiz(any()) } returns emptyList()
        val outcome = handler.handle(
            ChallengeLinkParseResult.Valid(
                link.copy(categoryValue = "Atlantis"), QuizCategory.ByRegion("Atlantis"), scoreVerified = false
            )
        )
        assertTrue(outcome is IncomingChallengeOutcome.Rejected)
        coVerify(exactly = 0) { repository.createIncomingChallenge(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `mode picks the matching country source`() = runTest {
        coEvery { capitalsQuiz(any()) } returns listOf(country)
        coEvery { flagsQuiz(any()) } returns listOf(country)

        val capitals = link.copy(quizMode = "capitals")
        assertEquals(
            IncomingChallengeOutcome.Accepted(capitals, scoreVerified = true),
            handler.handle(ChallengeLinkParseResult.Valid(capitals, QuizCategory.ByRegion("Europe"), true))
        )
        val flags = link.copy(categoryType = "flagcolor", categoryValue = "red", quizMode = "flags")
        assertEquals(
            IncomingChallengeOutcome.Accepted(flags, scoreVerified = true),
            handler.handle(ChallengeLinkParseResult.Valid(flags, QuizCategory.FlagSingleColor("red"), true))
        )
        coVerify(exactly = 1) { capitalsQuiz(QuizCategory.ByRegion("Europe")) }
        coVerify(exactly = 1) { flagsQuiz(QuizCategory.FlagSingleColor("red")) }
        coVerify(exactly = 0) { countriesQuiz(any()) }
    }

    @Test
    fun `storage failure is reported as rejected instead of crashing`() = runTest {
        coEvery { countriesQuiz(any()) } returns listOf(country)
        coEvery {
            repository.createIncomingChallenge(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("disk full")
        val outcome = handler.handle(
            ChallengeLinkParseResult.Valid(link, QuizCategory.ByRegion("Europe"), scoreVerified = true)
        )
        assertTrue(outcome is IncomingChallengeOutcome.Rejected)
    }
}
