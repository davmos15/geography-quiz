package com.geoquiz.app.domain.mode.tapmap

import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.testutil.TestQuizData
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Tap the map's items (task 5.2): every member of the chosen [QuizCategory.MapRegion], or the
 * exact codes of a [QuizCategory.Practice] set, shuffled deterministically by [QuizRandom].
 */
class TapMapQuestionGeneratorTest {

    private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
    private val members = TestQuizData.THREE + listOf(TestQuizData.PERU)

    private fun generator(seed: Long) = TapMapQuestionGenerator(getCountriesForQuiz, QuizRandom(Random(seed)))

    @Test
    fun `map region - returns exactly the resolved members`() = runTest {
        val category = QuizCategory.MapRegion("europe")
        coEvery { getCountriesForQuiz(category) } returns members

        val items = generator(1).items(category)

        assertEquals(members.toSet(), items.toSet())
        assertEquals(members.size, items.size)
    }

    @Test
    fun `practice - returns exactly the given codes, nothing added or dropped`() = runTest {
        val category = QuizCategory.Practice(listOf("FRA", "DEU"))
        coEvery { getCountriesForQuiz(category) } returns TestQuizData.THREE.filter { it.code in category.codes }

        val items = generator(1).items(category)

        assertEquals(setOf("FRA", "DEU"), items.map { it.code }.toSet())
    }

    @Test
    fun `same seed gives the same order every time`() = runTest {
        val category = QuizCategory.MapRegion("world")
        coEvery { getCountriesForQuiz(category) } returns members

        val first = generator(42).items(category)
        val second = generator(42).items(category)

        assertEquals(first.map { it.code }, second.map { it.code })
    }

    @Test
    fun `shuffles the resolved order (not a no-op)`() = runTest {
        val category = QuizCategory.MapRegion("world")
        coEvery { getCountriesForQuiz(category) } returns members
        val originalOrder = members.map { it.code }

        // Across many seeds, at least one order must differ from the resolved (unshuffled) order.
        val orders = (0..20).map { seed -> generator(seed.toLong()).items(category).map { it.code } }

        assertNotEquals(emptySet<List<String>>(), orders.filter { it != originalOrder }.toSet())
    }
}
