package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.Country

/**
 * Typo tolerance for answers: finds aliases one edit away from the input, where an
 * edit is an insertion, deletion, substitution or swap of two adjacent characters
 * (optimal string alignment Damerau–Levenshtein distance).
 *
 * Only names longer than [MIN_LENGTH_EXCLUSIVE] characters take part, on both sides,
 * so short names ("Iran"/"Iraq", "Mali"/"Male") are never matched loosely.
 * Inputs are expected to be normalised already.
 */
object FuzzyAnswerMatcher {

    const val MAX_DISTANCE = 1
    const val MIN_LENGTH_EXCLUSIVE = 6

    sealed class Match {
        data object None : Match()
        data class Unique(val country: Country) : Match()
        data class Ambiguous(val countries: Set<Country>) : Match()
    }

    /** Distinct countries with an alias within [MAX_DISTANCE] of [normalizedInput]. */
    fun match(normalizedInput: String, aliases: List<AnswerAlias>): Match {
        if (normalizedInput.length <= MIN_LENGTH_EXCLUSIVE) return Match.None
        val countries = LinkedHashMap<String, Country>()
        for (alias in aliases) {
            val candidate = alias.normalizedAlias
            if (candidate.length <= MIN_LENGTH_EXCLUSIVE) continue
            if (alias.country.code in countries) continue
            if (distance(normalizedInput, candidate, MAX_DISTANCE) <= MAX_DISTANCE) {
                countries[alias.country.code] = alias.country
            }
        }
        return when (countries.size) {
            0 -> Match.None
            1 -> Match.Unique(countries.values.single())
            else -> Match.Ambiguous(countries.values.toSet())
        }
    }

    /**
     * Optimal string alignment distance between [a] and [b]. Stops early and returns
     * `max + 1` as soon as the distance is known to exceed [max].
     */
    fun distance(a: String, b: String, max: Int = Int.MAX_VALUE - 1): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val cols = b.length + 1
        var prevPrev = IntArray(cols)
        var prev = IntArray(cols) { it }
        var current = IntArray(cols)

        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    prev[j] + 1,        // deletion
                    current[j - 1] + 1, // insertion
                    prev[j - 1] + cost  // substitution
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, prevPrev[j - 2] + 1) // transposition
                }
                current[j] = value
                if (value < rowMin) rowMin = value
            }
            // Row minima never decrease (a swap from two rows back costs at least as
            // much as the substitution path through this row), so once a whole row
            // exceeds max the final distance does too.
            if (rowMin > max) return max + 1
            val recycled = prevPrev
            prevPrev = prev
            prev = current
            current = recycled
        }
        return minOf(prev[b.length], max + 1)
    }
}
