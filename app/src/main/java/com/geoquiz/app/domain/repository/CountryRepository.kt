package com.geoquiz.app.domain.repository

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.Country
import kotlinx.coroutines.flow.Flow

interface CountryRepository {
    fun getAllCountries(): Flow<List<Country>>
    suspend fun getCountryCount(): Int

    /**
     * The countries with these codes (cca3), in the order given. Unknown codes are skipped and
     * duplicates are returned once.
     */
    suspend fun getCountriesByCodes(codes: List<String>): List<Country>

    suspend fun findCountryByAnswer(input: String): Country?
    suspend fun findCountryByCapitalAnswer(input: String): Country?

    /** Every country-name alias with its country, for typo matching. Loaded once, then cached. */
    suspend fun getCountryAnswerAliases(): List<AnswerAlias>

    /** Every capital alias with its country, for typo matching. Loaded once, then cached. */
    suspend fun getCapitalAnswerAliases(): List<AnswerAlias>
}
