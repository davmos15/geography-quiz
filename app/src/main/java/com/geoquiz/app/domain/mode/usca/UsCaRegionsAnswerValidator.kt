package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.domain.usecase.resolveAnswer
import javax.inject.Inject

/**
 * Name all's answer check (task 6.2, D43): reuses [resolveAnswer]'s shared typo rules (also used
 * by the classic Countries/Capitals validators) but builds its own aliases from this round's
 * items, never touching [com.geoquiz.app.domain.repository.CountryRepository] or Room (D43:
 * "operating on admin1 feature ids/names directly rather than Country" - the only reason a
 * [Country] value appears here at all is that [resolveAnswer] takes one as its identity carrier,
 * the same role [com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode] already reuses
 * [QuizState] for, task 6.2 review).
 *
 * Each region accepts its full English name and, as an equally valid alias, its postal
 * abbreviation straight off the bundled admin1 feature - no new alias data file (task 6.2
 * brief). A postal code is never matched by typo (two letters are under
 * [com.geoquiz.app.domain.usecase.FuzzyAnswerMatcher.MIN_LENGTH_EXCLUSIVE]); it only ever counts
 * as an exact match, consistent with D12's rule that typo tolerance applies to names, not codes.
 */
class UsCaRegionsAnswerValidator @Inject constructor(
    private val normalizeInput: NormalizeInputUseCase
) {
    /**
     * @param allowFuzzy accept answers one typo away (off in Hard mode, same as every other mode).
     * @param postalByCode this round's postal abbreviations, keyed by admin1 id (feature id).
     */
    suspend fun validate(
        input: String,
        state: QuizState,
        allowFuzzy: Boolean,
        postalByCode: Map<String, String>
    ): AnswerResult {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return AnswerResult.Incorrect

        fun matchesExactly(country: Country): Boolean {
            if (normalizeInput(country.name) == normalized) return true
            val postal = postalByCode[country.code]
            return !postal.isNullOrBlank() && normalizeInput(postal) == normalized
        }

        val countries = state.quiz.countries
        val exact = countries.firstOrNull(::matchesExactly)
        val aliases = countries.flatMap { country ->
            buildList {
                add(AnswerAlias(normalizeInput(country.name), country))
                postalByCode[country.code]?.takeIf { it.isNotBlank() }
                    ?.let { add(AnswerAlias(normalizeInput(it), country)) }
            }
        }
        return resolveAnswer(normalized, state, allowFuzzy, exactMatch = { exact }, aliases = { aliases })
    }
}
