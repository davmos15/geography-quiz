package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.repository.CountryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** The cca3 codes of the countries in a map region preset, from the app's country data. */
class GetRegionMembersUseCase @Inject constructor(
    private val repository: CountryRepository,
) {
    suspend operator fun invoke(preset: RegionPreset): Set<String> =
        repository.getAllCountries().first().filter { preset.includes(it) }.map { it.code }.toSet()
}
