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
 *
 * Two task 7.2 review fixes:
 * - **H1**: data names are inconsistent about generic physical-geography words ("Amazon" but
 *   "Colorado River"; "Lake Baikal" but "Qinghai Lake"; "Mount Everest"). [GENERIC_WORDS] are
 *   stripped from both the typed input and the stored name/alias before the *exact* comparison
 *   (see [strip]), so "Everest"/"Kilimanjaro"/"Baikal"/"Titicaca"/"Murray"/"Colorado" and
 *   "Amazon River"/"Nile River" are all accepted. Typo tolerance (below) is deliberately left
 *   comparing the un-stripped form: a typo landing inside a generic word itself (e.g. "Nile
 *   Rive") needs the whole name to stay one edit away, which stripping would break ("nile" vs
 *   "nile rive" is nowhere near one edit).
 * - **M1**: a typo is only ever resolved against [targetId] (the feature currently highlighted),
 *   never any other round feature - matching a typo against every one of this round's items (as
 *   this validator used to) could call an unrelated feature's typo "Close" even though the
 *   player was never asked about it. Exact matches (including an already-answered feature's
 *   name) still check every round feature, same as before: M1 is about typo tolerance only.
 */
class PhysicalFeatureAnswerValidator @Inject constructor(
    private val normalizeInput: NormalizeInputUseCase
) {
    /**
     * @param aliasesByCode this round's [PhysicalFeature.aliases], keyed by feature id.
     * @param targetId the feature currently highlighted (M1: typo tolerance is scoped to it).
     */
    suspend fun validate(
        input: String,
        state: QuizState,
        allowFuzzy: Boolean,
        aliasesByCode: Map<String, List<String>>,
        targetId: String
    ): AnswerResult {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return AnswerResult.Incorrect
        val strippedInput = stripGenericWords(normalized)

        fun matchesExactly(country: Country): Boolean {
            if (stripGenericWords(normalizeInput(country.name)) == strippedInput) return true
            return aliasesByCode[country.code].orEmpty().any { stripGenericWords(normalizeInput(it)) == strippedInput }
        }

        val countries = state.quiz.countries
        val exact = countries.firstOrNull(::matchesExactly)
        // M1: fuzzy/near-miss candidates are this round's CURRENT target only, never the whole
        // round - see the class doc.
        val target = countries.firstOrNull { it.code == targetId }
        val aliases = target?.let { country ->
            buildList {
                add(AnswerAlias(normalizeInput(country.name), country))
                aliasesByCode[country.code].orEmpty().forEach { add(AnswerAlias(normalizeInput(it), country)) }
            }
        }.orEmpty()
        return resolveAnswer(normalized, state, allowFuzzy, exactMatch = { exact }, aliases = { aliases })
    }

    /** [normalizeInput] plus dropping any [GENERIC_WORDS] (H1, see the class doc); never
     * strips a name down to nothing (a name that is only generic words, none in this mode's
     * data, stays as its normalised self rather than matching every other blank input). */
    private fun stripGenericWords(normalized: String): String {
        val stripped = normalized.split(" ").filterNot { it in GENERIC_WORDS }.joinToString(" ")
        return stripped.ifBlank { normalized }
    }

    private companion object {
        val GENERIC_WORDS = setOf("river", "lake", "mount", "mountains", "range")
    }
}
