package com.geoquiz.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.geoquiz.app.testutil.TestQuizData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompletedQuizRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: CompletedQuizRepositoryImpl

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "last_result.preferences_pb")
        }
        repository = CompletedQuizRepositoryImpl(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `saved result round-trips by id`() = runTest {
        val quiz = TestQuizData.completedQuiz(
            incorrectGuessStrings = listOf("Narnia", "Atlantis"),
            challengeId = "challenge-9"
        )

        repository.save(quiz)

        assertEquals(quiz, repository.get(quiz.id))
    }

    @Test
    fun `a nullable challenge id and empty lists round-trip`() = runTest {
        val quiz = TestQuizData.completedQuiz(answeredCodes = emptyList(), challengeId = null)

        repository.save(quiz)

        assertEquals(quiz, repository.get(quiz.id))
    }

    @Test
    fun `unknown id returns null`() = runTest {
        repository.save(TestQuizData.completedQuiz(id = "a"))

        assertNull(repository.get("b"))
    }

    @Test
    fun `nothing saved returns null`() = runTest {
        assertNull(repository.get("a"))
    }

    @Test
    fun `saveIfAbsent saves exactly once per id even when called concurrently`() = runTest {
        val quiz = TestQuizData.completedQuiz(id = "same")

        val results = (1..8).map { async(Dispatchers.IO) { repository.saveIfAbsent(quiz) } }.awaitAll()

        assertEquals(1, results.count { it })
        assertEquals(quiz, repository.get("same"))
        // A different id replaces it.
        assertTrue(repository.saveIfAbsent(TestQuizData.completedQuiz(id = "next")))
    }

    @Test
    fun `only the latest result is kept`() = runTest {
        repository.save(TestQuizData.completedQuiz(id = "first"))
        repository.save(TestQuizData.completedQuiz(id = "second"))

        assertNull(repository.get("first"))
        assertEquals("second", repository.get("second")?.id)
    }

    @Test
    fun `clear removes the result`() = runTest {
        repository.save(TestQuizData.completedQuiz(id = "a"))

        repository.clear()

        assertNull(repository.get("a"))
        assertTrue(store.data.first().asMap().isEmpty())
    }

    @Test
    fun `a corrupt record reads as missing instead of crashing`() = runTest {
        store.edit { it[CompletedQuizRepositoryImpl.LAST_RESULT_KEY] = "{not json" }

        assertNull(repository.get("a"))
    }

    @Test
    fun `unknown fields from a newer app version are ignored`() = runTest {
        repository.save(TestQuizData.completedQuiz(id = "a"))
        val json = store.data.first()[CompletedQuizRepositoryImpl.LAST_RESULT_KEY]!!
        store.edit {
            it[CompletedQuizRepositoryImpl.LAST_RESULT_KEY] = json.dropLast(1) + ""","extra":1}"""
        }

        assertEquals("a", repository.get("a")?.id)
    }
}
