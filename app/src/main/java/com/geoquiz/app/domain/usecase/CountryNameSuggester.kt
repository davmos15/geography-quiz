package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.Country
import javax.inject.Inject

/**
 * Ranks country names for an autocomplete list (Border hop at Normal, task P10-C). Pure: the
 * caller passes every country alias ([com.geoquiz.app.domain.repository.CountryRepository.getCountryAnswerAliases])
 * and gets back at most [limit] distinct countries, best first.
 *
 * The input goes through [NormalizeInputUseCase] (the same canonical form the stored aliases
 * use, so diacritics, case, hyphens and a leading "the" never matter). Each country takes the
 * best rank of any of its aliases:
 *
 * 1. [Rank.EXACT]: an alias equals the input ("uk" finds United Kingdom).
 * 2. [Rank.PREFIX]: an alias starts with the input ("ger" finds Germany).
 * 3. [Rank.WORD_PREFIX]: a later word of an alias starts with the input ("kingdom" finds United
 *    Kingdom).
 * 4. [Rank.FUZZY]: from [FUZZY_MIN_LENGTH] characters on, the input is within one edit
 *    ([FuzzyAnswerMatcher.distance]) of an alias's first `input.length` characters, or of the
 *    whole alias ("germny" finds Germany, "brazl" finds Brazil).
 *
 * Ties go to the country whose own name matched (rather than only an alternative spelling),
 * then alphabetical by name, so the list is stable while typing.
 */
class CountryNameSuggester @Inject constructor(
    private val normalizeInput: NormalizeInputUseCase
) {

    enum class Rank { EXACT, PREFIX, WORD_PREFIX, FUZZY }

    /**
     * Each country's own normalised name, for [aliases] by reference (P10-R2 L3): [suggest] is
     * called on every keystroke with the same aliases list for a whole round, so this is
     * computed once per list rather than re-normalising every alias's country name on every
     * call. Recomputed if a different list (by reference) comes in, e.g. a fresh round.
     */
    private var ownNameCache: OwnNameCache? = null

    private class OwnNameCache(val aliases: List<AnswerAlias>, val ownNames: Map<String, String>)

    fun suggest(input: String, aliases: List<AnswerAlias>, limit: Int = DEFAULT_LIMIT): List<Country> {
        if (limit <= 0) return emptyList()
        val normalized = normalizeInput(input)
        if (normalized.isEmpty()) return emptyList()

        val ownNames = ownNamesFor(aliases)
        val best = LinkedHashMap<String, Candidate>()
        for (alias in aliases) {
            val rank = rankOf(normalized, alias.normalizedAlias) ?: continue
            val isOwnName = alias.normalizedAlias == ownNames[alias.country.code]
            val candidate = Candidate(alias.country, rank, isOwnName)
            val previous = best[alias.country.code]
            if (previous == null || candidate.isBetterThan(previous)) best[alias.country.code] = candidate
        }
        return best.values
            .sortedWith(
                compareBy<Candidate> { it.rank }
                    .thenBy { if (it.ownName) 0 else 1 }
                    .thenBy { it.country.name }
            )
            .take(limit)
            .map { it.country }
    }

    private fun ownNamesFor(aliases: List<AnswerAlias>): Map<String, String> {
        ownNameCache?.let { if (it.aliases === aliases) return it.ownNames }
        val ownNames = aliases.asSequence()
            .map { it.country }
            .distinctBy { it.code }
            .associate { it.code to normalizeInput(it.name) }
        ownNameCache = OwnNameCache(aliases, ownNames)
        return ownNames
    }

    /** How [alias] matches [input] (both normalised), or null if it doesn't. */
    internal fun rankOf(input: String, alias: String): Rank? = when {
        alias == input -> Rank.EXACT
        alias.startsWith(input) -> Rank.PREFIX
        alias.split(' ').drop(1).any { it.startsWith(input) } -> Rank.WORD_PREFIX
        input.length >= FUZZY_MIN_LENGTH && isOneEditAway(input, alias) -> Rank.FUZZY
        else -> null
    }

    private fun isOneEditAway(input: String, alias: String): Boolean {
        val max = FuzzyAnswerMatcher.MAX_DISTANCE
        if (FuzzyAnswerMatcher.distance(input, alias, max) <= max) return true
        // Still typing: compare with the alias's start, one character either way, so a typo or a
        // missing letter part-way through a long name still finds it.
        for (length in (input.length - 1)..(input.length + 1)) {
            if (length < 1 || length > alias.length) continue
            if (FuzzyAnswerMatcher.distance(input, alias.substring(0, length), max) <= max) return true
        }
        return false
    }

    private data class Candidate(val country: Country, val rank: Rank, val ownName: Boolean) {
        fun isBetterThan(other: Candidate): Boolean =
            rank < other.rank || (rank == other.rank && ownName && !other.ownName)
    }

    companion object {
        const val DEFAULT_LIMIT = 5

        /** Below this many (normalised) characters only exact and prefix matches count. */
        const val FUZZY_MIN_LENGTH = 4
    }
}
