package com.geoquiz.app.domain.repository

import com.geoquiz.app.domain.model.CompletedQuiz

/** Stores the latest finished quiz for the Results and Answer review screens. */
interface CompletedQuizRepository {

    /** Saves [quiz], replacing any earlier result. */
    suspend fun save(quiz: CompletedQuiz)

    /**
     * Saves [quiz] unless a result with the same id is already stored, as one atomic step.
     * Returns false if it was already there (the quiz has been recorded).
     */
    suspend fun saveIfAbsent(quiz: CompletedQuiz): Boolean

    /** The stored result with this [id], or null if it was replaced, cleared or never saved. */
    suspend fun get(id: String): CompletedQuiz?

    /** Deletes the stored result ("Reset all data"). */
    suspend fun clear()
}
