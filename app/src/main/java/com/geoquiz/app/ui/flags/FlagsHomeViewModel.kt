package com.geoquiz.app.ui.flags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.domain.model.FlagCategoryGroup
import com.geoquiz.app.domain.repository.CountryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlagCategoryGroupInfo(
    val group: FlagCategoryGroup,
    val quizCount: Int
)

data class FlagsHomeUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val categoryGroups: List<FlagCategoryGroupInfo> = emptyList()
)

@HiltViewModel
class FlagsHomeViewModel @Inject constructor(
    private val repository: CountryRepository,
    private val flagColorDao: FlagColorDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlagsHomeUiState())
    val uiState: StateFlow<FlagsHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val allCountries = repository.getAllCountries().first()

            // Single query: load all mappings at once
            val allMappings = flagColorDao.getAllMappings()

            // Build in-memory maps
            val colorToCountries = mutableMapOf<String, MutableSet<String>>()
            val countryToColors = mutableMapOf<String, MutableSet<String>>()
            for (mapping in allMappings) {
                colorToCountries.getOrPut(mapping.color) { mutableSetOf() }.add(mapping.countryCca3)
                countryToColors.getOrPut(mapping.countryCca3) { mutableSetOf() }.add(mapping.color)
            }

            val colors = colorToCountries.keys.sorted()

            // Count 2-color combos with >= 2 countries
            var twoColorCount = 0
            for (i in colors.indices) {
                val codes1 = colorToCountries[colors[i]] ?: continue
                for (j in i + 1 until colors.size) {
                    val codes2 = colorToCountries[colors[j]] ?: continue
                    if (codes1.intersect(codes2).size >= 2) twoColorCount++
                }
            }

            // Count 3-color combos with >= 2 countries
            var threeColorCount = 0
            for (i in colors.indices) {
                val codes1 = colorToCountries[colors[i]] ?: continue
                for (j in i + 1 until colors.size) {
                    val codes2 = colorToCountries[colors[j]] ?: continue
                    val inter12 = codes1.intersect(codes2)
                    if (inter12.size < 2) continue
                    for (k in j + 1 until colors.size) {
                        val codes3 = colorToCountries[colors[k]] ?: continue
                        if (inter12.intersect(codes3).size >= 2) threeColorCount++
                    }
                }
            }

            // Count distinct color counts
            val colorCountSet = countryToColors.values.map { it.size }.toSet()

            val groups = listOf(
                FlagCategoryGroupInfo(FlagCategoryGroup.FLAG_SINGLE_COLOR, colors.size),
                FlagCategoryGroupInfo(FlagCategoryGroup.FLAG_TWO_COLOR_COMBO, twoColorCount),
                FlagCategoryGroupInfo(FlagCategoryGroup.FLAG_THREE_COLOR_COMBO, threeColorCount),
                FlagCategoryGroupInfo(FlagCategoryGroup.FLAG_COLOR_COUNT, colorCountSet.size)
            ).filter { it.quizCount > 0 }

            _uiState.value = FlagsHomeUiState(
                isLoading = false,
                totalCount = allCountries.size,
                categoryGroups = groups
            )
        }
    }
}
