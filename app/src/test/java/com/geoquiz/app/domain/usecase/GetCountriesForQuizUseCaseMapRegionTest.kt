package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [QuizCategory.MapRegion] (task 5.0, Tap the map): resolved via [GetRegionMembersUseCase],
 * which can span several of the app's subregions (North America) - something [QuizCategory.ByRegion]
 * and [QuizCategory.BySubregion] cannot express on their own.
 */
class GetCountriesForQuizUseCaseMapRegionTest {

    private fun country(code: String, region: String, subregion: String) = Country(
        code = code,
        name = code,
        officialName = code,
        region = region,
        subregion = subregion,
        nameLength = code.length,
        capital = "$code City"
    )

    private val france = country("FRA", "Europe", "Western Europe")
    private val canada = country("CAN", "Americas", "North America")
    private val mexico = country("MEX", "Americas", "Central America")
    private val jamaica = country("JAM", "Americas", "Caribbean")
    private val peru = country("PER", "Americas", "South America")
    private val japan = country("JPN", "Asia", "Eastern Asia")

    private val countries = listOf(france, canada, mexico, jamaica, peru, japan)

    private val repository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(countries)
    }

    private val useCase = GetCountriesForQuizUseCase(repository, GetRegionMembersUseCase(repository))

    @Test
    fun `world includes every country`() = runTest {
        val items = useCase(QuizCategory.MapRegion("world"))
        assertEquals(countries.map { it.code }.toSet(), items.map { it.code }.toSet())
    }

    @Test
    fun `north america spans three subregions`() = runTest {
        val items = useCase(QuizCategory.MapRegion("north_america"))
        assertEquals(setOf("CAN", "MEX", "JAM"), items.map { it.code }.toSet())
    }

    @Test
    fun `europe is a region`() = runTest {
        val items = useCase(QuizCategory.MapRegion("europe"))
        assertEquals(listOf("FRA"), items.map { it.code })
    }

    @Test
    fun `south america is a subregion`() = runTest {
        val items = useCase(QuizCategory.MapRegion("south_america"))
        assertEquals(listOf("PER"), items.map { it.code })
    }

    @Test
    fun `an unknown preset id resolves to no countries`() = runTest {
        val items = useCase(QuizCategory.MapRegion("atlantis"))
        assertEquals(emptyList<String>(), items.map { it.code })
    }
}
