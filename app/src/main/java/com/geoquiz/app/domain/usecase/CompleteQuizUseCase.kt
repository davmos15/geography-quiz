package com.geoquiz.app.domain.usecase

import com.geoquiz.app.data.PlayGamesLeaderboardIds
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CompletedQuizRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Finishes a quiz: scores it with its mode's scoring rule, saves the [CompletedQuiz] that Results and Answer review read,
 * clears the "Resume quiz" save, and records achievements, history and leaderboard scores.
 *
 * Easy quizzes are recorded in history (so they show in stats and mastery stars) but unlock no
 * achievements and submit no leaderboard scores (D16, [Difficulty.countsForAchievements]).
 *
 * Idempotent per [Request.resultId]: if a result with that id is already stored, it is returned
 * and nothing is recorded again. The caller keeps the id across process death (in its
 * `SavedStateHandle`), so a quiz is never recorded twice.
 *
 * The result is saved before the side effects run, so a crash in between can lose an
 * achievement or history row but can never record the quiz twice. The work runs to the end
 * even if the caller's scope is cancelled (for example, the screen is destroyed meanwhile).
 */
class CompleteQuizUseCase @Inject constructor(
    private val gameModes: GameModeRegistry,
    private val completedQuizRepository: CompletedQuizRepository,
    private val savedQuizRepository: SavedQuizRepository,
    private val achievementRepository: AchievementRepository,
    private val quizHistoryRepository: QuizHistoryRepository,
    private val playGamesService: PlayGamesAchievementService
) {

    data class Request(
        val resultId: String,
        val state: QuizState,
        val timeElapsedSeconds: Int,
        val quizMode: QuizMode,
        /** Category keys as given in the quiz route; history rows have always used these. */
        val routeCategoryType: String,
        val routeCategoryValue: String,
        val difficulty: Difficulty,
        val challengeId: String?
    )

    data class Outcome(
        val completedQuiz: CompletedQuiz,
        /** Achievements unlocked by this quiz; empty when it had already been recorded. */
        val newAchievements: List<Achievement>,
        /** False when the result already existed and nothing was recorded. */
        val newlyRecorded: Boolean
    )

    suspend operator fun invoke(request: Request): Outcome = withContext(NonCancellable) {
        completedQuizRepository.get(request.resultId)?.let { existing ->
            return@withContext Outcome(existing, emptyList(), newlyRecorded = false)
        }

        val state = request.state
        val category = state.quiz.category
        val result = gameModes[request.quizMode].scoring
            .score(state.copy(timeElapsedSeconds = request.timeElapsedSeconds))
        val completed = CompletedQuiz(
            id = request.resultId,
            quizModeId = request.quizMode.id,
            categoryType = category.typeKey,
            categoryValue = category.valueKey,
            categoryName = category.displayName,
            countryCodes = state.quiz.countries.map { it.code },
            answeredCodes = state.quiz.countries.map { it.code }.filter { it in state.answeredCountries },
            incorrectGuessStrings = state.incorrectGuessStrings,
            correct = result.correctAnswers,
            total = result.totalCountries,
            timeSeconds = result.timeElapsedSeconds,
            score = result.score,
            perfectBonus = result.perfectBonus,
            incorrectGuesses = result.incorrectGuesses,
            hardMode = request.difficulty == Difficulty.HARD,
            challengeId = request.challengeId,
            completedAtMillis = System.currentTimeMillis(),
            difficultyId = request.difficulty.id
        )

        // Atomic check-and-save: a second run with the same id (e.g. a recreated screen racing
        // the first run) records nothing.
        if (!completedQuizRepository.saveIfAbsent(completed)) {
            val existing = completedQuizRepository.get(request.resultId) ?: completed
            return@withContext Outcome(existing, emptyList(), newlyRecorded = false)
        }
        savedQuizRepository.clearSavedQuiz()

        val counts = request.difficulty.countsForAchievements
        val newlyUnlocked = if (counts) {
            achievementRepository.onQuizCompleted(
                category = category,
                correctAnswers = result.correctAnswers,
                totalCountries = result.totalCountries,
                timeElapsedSeconds = result.timeElapsedSeconds,
                quizMode = request.quizMode,
                incorrectGuesses = result.incorrectGuesses,
                hardMode = request.difficulty == Difficulty.HARD
            )
        } else {
            emptyList()
        }
        newlyUnlocked.forEach { playGamesService.unlockAchievement(it) }

        quizHistoryRepository.recordQuizResult(
            quizMode = request.quizMode.id,
            categoryType = request.routeCategoryType,
            categoryValue = request.routeCategoryValue,
            correctAnswers = result.correctAnswers,
            totalQuestions = result.totalCountries,
            incorrectGuesses = result.incorrectGuesses,
            score = result.score,
            timeElapsedSeconds = result.timeElapsedSeconds,
            perfectBonus = result.perfectBonus,
            difficulty = request.difficulty
        )

        if (!counts) return@withContext Outcome(completed, newlyUnlocked, newlyRecorded = true)

        val overallTotal = quizHistoryRepository.getTotalCorrectAnswersSync()
        playGamesService.submitScore(PlayGamesLeaderboardIds.OVERALL, overallTotal)
        PlayGamesLeaderboardIds.forMode(request.quizMode.id)?.let { leaderboardId ->
            val modeTotal = quizHistoryRepository.getTotalCorrectAnswersForModeSync(request.quizMode.id)
            playGamesService.submitScore(leaderboardId, modeTotal)
        }

        Outcome(completed, newlyUnlocked, newlyRecorded = true)
    }
}
