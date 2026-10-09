package com.geoquiz.app.domain.challenge

import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.model.ChallengeDeepLink
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
 * flag colour, for example), because the app never offers an empty quiz. The countries come
 * from the mode's own generator in [GameModeRegistry], so this matches what the quiz will ask.
 * Never throws, except for coroutine cancellation.
 */
class AcceptIncomingChallengeUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository,
    private val gameModes: GameModeRegistry
) {

    suspend operator fun invoke(parsed: ChallengeLinkParseResult): IncomingChallengeOutcome {
        val valid = when (parsed) {
            is ChallengeLinkParseResult.Invalid -> return IncomingChallengeOutcome.Rejected(parsed.reason)
            is ChallengeLinkParseResult.Valid -> parsed
        }
        val link = valid.link
        return try {
            // The parser only accepts known modes, so a miss here means a registry gap.
            val mode = gameModes.find(link.quizMode)
                ?: return IncomingChallengeOutcome.Rejected("unknown mode")
            if (mode.generator.items(valid.category).isEmpty()) {
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
}
