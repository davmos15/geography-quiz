package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.domain.usecase.resolveAnswer
import javax.inject.Inject

/**
 * Typed answers for Normal/Hard (task 7.2, D51): reuses [resolveAnswer]'s shared typo rules
 * (also used by the classic Countries/Capitals validators and
 * [com.geoquiz.app.domain.mode.usca.UsCaRegionsAnswerValidator]) but builds its own aliases from
 * this round's items, never touching Room or [com.geoquiz.app.domain.repository.CountryRepository]
 * (this mode has no alias table, D51 brief: "keep it simple, in-memory"). The only reason a
 * [Country] value appears here at all is that [resolveAnswer] takes one as its identity carrier -
 * the same role [UsCaRegionsAnswerValidator] and [com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode]
 * already reuse [QuizState] for (synthetic [Country] via [PhysicalFeature.toQuizCountry]).
 *
 * A feature's own `name` and [PhysicalFeature.aliases] (e.g. "Lake Nyasa" for Lake Malawi) are
 * both exact aliases; typo tolerance ([com.geoquiz.app.domain.model.Difficulty.allowsTypos],
 * on in Normal, off in Hard, D12) applies to both exactly like every other mode.
 */
class PhysicalFeatureAnswerValidator @Inject constructor(
    private val normalizeInput: NormalizeInputUseCase
) {
    /** @param aliasesByCode this round's [PhysicalFeature.aliases], keyed by feature id. */
    suspend fun validate(
        input: String,
        state: QuizState,
        allowFuzzy: Boolean,
        aliasesByCode: Map<String, List<String>>
    ): AnswerResult {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return AnswerResult.Incorrect

        fun matchesExactly(country: Country): Boolean {
            if (normalizeInput(country.name) == normalized) return true
            return aliasesByCode[country.code].orEmpty().any { normalizeInput(it) == normalized }
        }

        val countries = state.quiz.countries
        val exact = countries.firstOrNull(::matchesExactly)
        val aliases = countries.flatMap { country ->
            buildList {
                add(AnswerAlias(normalizeInput(country.name), country))
                aliasesByCode[country.code].orEmpty().forEach { add(AnswerAlias(normalizeInput(it), country)) }
            }
        }
        return resolveAnswer(normalized, state, allowFuzzy, exactMatch = { exact }, aliases = { aliases })
    }
}
