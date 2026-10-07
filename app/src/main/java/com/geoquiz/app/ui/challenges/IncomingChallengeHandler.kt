package com.geoquiz.app.ui.challenges

import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.domain.challenge.ChallengeLinkParseResult
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.usecase.GetCountriesForCapitalQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface IncomingChallengeOutcome {
    /** Saved; the app should open the challenge screen for [link]. */
    data class Accepted(val link: ChallengeDeepLink, val scoreVerified: Boolean) : IncomingChallengeOutcome

    /** Nothing was saved; the app should tell the player the link isn't valid. */
    data class Rejected(val reason: String) : IncomingChallengeOutcome
}

/**
 * Turns a parsed challenge link into a saved incoming challenge. On top of the parser's
 * checks it rejects categories with no countries in the current data (an unknown region or
 * flag colour, for example), because the app never offers an empty quiz. Never throws,
 * except for coroutine cancellation.
 */
class IncomingChallengeHandler @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val getCountriesForQuiz: GetCountriesForQuizUseCase,
    private val getCountriesForCapitalQuiz: GetCountriesForCapitalQuizUseCase,
    private val getCountriesForFlagQuiz: GetCountriesForFlagQuizUseCase
) {

    suspend fun handle(parsed: ChallengeLinkParseResult): IncomingChallengeOutcome {
        val valid = when (parsed) {
            is ChallengeLinkParseResult.Invalid -> return IncomingChallengeOutcome.Rejected(parsed.reason)
            is ChallengeLinkParseResult.Valid -> parsed
        }
        val link = valid.link
        return try {
            val mode = QuizMode.fromId(link.quizMode)
            if (countriesFor(valid.category, mode) == 0) {
                return IncomingChallengeOutcome.Rejected("category has no countries")
            }
            challengeRepository.createIncomingChallenge(
                id = link.challengeId,
                categoryType = link.categoryType,
                categoryValue = link.categoryValue,
                categoryDisplayName = valid.category.displayName,
                quizMode = link.quizMode,
                challengerName = link.challengerName,
                challengerScore = link.challengerScore,
                challengerTotal = link.challengerTotal,
                challengerTime = link.challengerTime
            )
            IncomingChallengeOutcome.Accepted(link, valid.scoreVerified)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            IncomingChallengeOutcome.Rejected("failed to save: ${e.javaClass.simpleName}")
        }
    }

    /** Same use case choice as QuizViewModel.loadQuiz. */
    private suspend fun countriesFor(category: QuizCategory, mode: QuizMode): Int = when {
        category.isFlagCategory -> getCountriesForFlagQuiz(category).size
        mode == QuizMode.CAPITALS -> getCountriesForCapitalQuiz(category).size
        else -> getCountriesForQuiz(category).size
    }
}
