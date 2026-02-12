package com.geoquiz.app.domain.repository

import com.geoquiz.app.domain.model.Country
import kotlinx.coroutines.flow.Flow

interface CountryRepository {
    fun getAllCountries(): Flow<List<Country>>
    suspend fun getCountryCount(): Int
    suspend fun findCountryByAnswer(input: String): Country?
    suspend fun ensureSeeded()
}
