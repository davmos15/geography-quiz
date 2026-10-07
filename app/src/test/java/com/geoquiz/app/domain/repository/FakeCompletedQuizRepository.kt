package com.geoquiz.app.domain.repository

import com.geoquiz.app.domain.model.CompletedQuiz

/** In-memory [CompletedQuizRepository] that keeps only the latest result, like the real one. */
class FakeCompletedQuizRepository(var stored: CompletedQuiz? = null) : CompletedQuizRepository {
    var saveCount = 0
        private set
    var clearCount = 0
        private set

    override suspend fun save(quiz: CompletedQuiz) {
        saveCount++
        stored = quiz
    }

    override suspend fun saveIfAbsent(quiz: CompletedQuiz): Boolean {
        if (stored?.id == quiz.id) return false
        save(quiz)
        return true
    }

    override suspend fun get(id: String): CompletedQuiz? = stored?.takeIf { it.id == id }

    override suspend fun clear() {
        clearCount++
        stored = null
    }
}
