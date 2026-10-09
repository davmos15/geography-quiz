package com.geoquiz.app.domain.usecase

import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.testutil.TestQuizData
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each mode's item filter returns exactly a practice set's countries, in its order (3.5c). */
class GetCountriesForPracticeTest {

    private val japan = TestQuizData.country("JPN", "Japan", "Tokyo", region = "Asia")
    private val all = TestQuizData.THREE + listOf(TestQuizData.PERU, japan)

    private val repository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(all)
    }
    private val flagColorDao = mockk<FlagColorDao> { coEvery { getAllMappings() } returns emptyList() }
    private val flagElementDao = mockk<FlagElementDao> { coEvery { getAllMappings() } returns emptyList() }

    /** Quiz order that differs from the repository's order on purpose. */
    private val practice = QuizCategory.Practice(listOf("JPN", "FRA", "PER"))
    private val expected = listOf("JPN", "FRA", "PER")

    @Test
    fun `countries returns exactly the practice codes in order`() = runTest {
        val items = GetCountriesForQuizUseCase(repository)(practice)
        assertEquals(expected, items.map { it.code })
    }

    @Test
    fun `capitals returns exactly the practice codes in order`() = runTest {
        val items = GetCountriesForCapitalQuizUseCase(repository)(practice)
        assertEquals(expected, items.map { it.code })
    }

    @Test
    fun `flags returns exactly the practice codes in order`() = runTest {
        val items = GetCountriesForFlagQuizUseCase(repository, flagColorDao, flagElementDao)(practice)
        assertEquals(expected, items.map { it.code })
    }

    @Test
    fun `unknown and repeated codes are skipped`() = runTest {
        val withJunk = QuizCategory.Practice(listOf("DEU", "XXX", "DEU", "AUT"))

        assertEquals(listOf("DEU", "AUT"), GetCountriesForQuizUseCase(repository)(withJunk).map { it.code })
        assertEquals(listOf("DEU", "AUT"), GetCountriesForCapitalQuizUseCase(repository)(withJunk).map { it.code })
    }

    @Test
    fun `capitals skips a practice country without a capital`() = runTest {
        val noCapital = TestQuizData.country("ATA", "Antarctica", "")
        val repo = mockk<CountryRepository> { every { getAllCountries() } returns flowOf(all + noCapital) }

        val items = GetCountriesForCapitalQuizUseCase(repo)(QuizCategory.Practice(listOf("ATA", "FRA")))

        assertEquals(listOf("FRA"), items.map { it.code })
    }
}
