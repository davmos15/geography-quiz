package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.usecase.FuzzyAnswerMatcher

/**
 * What one Border hop guess does to the route (task P10-C). The player names a country (typed at
 * Normal/Hard, picked at Easy); [BorderHopMoves.judge] decides against the route so far.
 */
sealed interface BorderHopMove {
    /** The typed text is not any country's name (no strike). */
    data object NotRecognised : BorderHopMove

    /** [code] is already on the route (the start, the current country or a hop made); no strike. */
    data class AlreadyOnRoute(val code: String) : BorderHopMove

    /** [code] has no land border with [fromCode], the end of the route: a wrong guess (a strike at Hard). */
    data class NotNeighbour(val code: String, val fromCode: String) : BorderHopMove

    /** [code] borders the end of the route: it is added; [reachesTarget] when it is the target. */
    data class Hop(val code: String, val reachesTarget: Boolean) : BorderHopMove
}

object BorderHopMoves {

    /**
     * The country typed as [normalizedInput] (already through
     * [com.geoquiz.app.domain.usecase.NormalizeInputUseCase]), from every country's [aliases], not
     * only a quiz set: any country may be named, and a non-neighbour is then a wrong guess rather
     * than "not recognised". An exact alias always wins. Otherwise, only when [allowFuzzy]
     * (Normal; Hard is exact spelling, D12's spirit), a single country one edit away
     * ([FuzzyAnswerMatcher]) is accepted; two or more equally close countries stay unrecognised.
     *
     * [FuzzyAnswerMatcher] only fuzzes names over [FuzzyAnswerMatcher.MIN_LENGTH_EXCLUSIVE]
     * characters (both sides), so a typo in a short name like Brazil ("Brazl") would otherwise
     * never be forgiven. [SHORT_NAME_MIN_LENGTH] to [FuzzyAnswerMatcher.MIN_LENGTH_EXCLUSIVE]
     * characters get the same one-edit tolerance here instead (P10-R2 L2), gated to a minimum
     * input length so very short inputs (3 characters or fewer) don't start matching almost
     * anything; still a single unambiguous country, exactly as the general matcher requires.
     */
    fun resolve(normalizedInput: String, aliases: List<AnswerAlias>, allowFuzzy: Boolean): Country? {
        if (normalizedInput.isBlank()) return null
        aliases.firstOrNull { it.normalizedAlias == normalizedInput }?.let { return it.country }
        if (!allowFuzzy) return null
        (FuzzyAnswerMatcher.match(normalizedInput, aliases) as? FuzzyAnswerMatcher.Match.Unique)?.let { return it.country }
        if (normalizedInput.length < SHORT_NAME_MIN_LENGTH || normalizedInput.length > FuzzyAnswerMatcher.MIN_LENGTH_EXCLUSIVE) {
            return null
        }
        return shortNameTypoMatch(normalizedInput, aliases)
    }

    /** The one country (if exactly one) whose alias is within [FuzzyAnswerMatcher.MAX_DISTANCE]
     * of a short [normalizedInput]; null if none or more than one (ambiguous). */
    private fun shortNameTypoMatch(normalizedInput: String, aliases: List<AnswerAlias>): Country? {
        val matches = LinkedHashMap<String, Country>()
        for (alias in aliases) {
            if (alias.country.code in matches) continue
            val distance = FuzzyAnswerMatcher.distance(normalizedInput, alias.normalizedAlias, FuzzyAnswerMatcher.MAX_DISTANCE)
            if (distance <= FuzzyAnswerMatcher.MAX_DISTANCE) matches[alias.country.code] = alias.country
        }
        return matches.values.singleOrNull()
    }

    /** Below this many characters, a typo is never forgiven, even at Normal (P10-R2 L2). */
    private const val SHORT_NAME_MIN_LENGTH = 4

    /** What naming [code] does to a route currently [path] (start first) heading for [targetCode]. */
    fun judge(graph: BorderGraph, path: List<String>, targetCode: String, code: String): BorderHopMove {
        if (code in path) return BorderHopMove.AlreadyOnRoute(code)
        val current = path.last()
        if (code !in graph.neighboursOf(current)) return BorderHopMove.NotNeighbour(code, current)
        return BorderHopMove.Hop(code, reachesTarget = code == targetCode)
    }

    /**
     * Every neighbour of [currentCode] that is one hop closer to [targetCode] (so on a shortest
     * route from here), in the graph's neighbour order. Empty if the target can't be reached.
     * Used by the "Show a valid hop" hint and the Easy options ([BorderHopChoiceBuilder]).
     */
    fun closerNeighbours(graph: BorderGraph, currentCode: String, targetCode: String): List<String> {
        val distances = graph.distancesFrom(targetCode)
        val currentDistance = distances[currentCode] ?: return emptyList()
        return graph.neighboursOf(currentCode).filter { distances[it] == currentDistance - 1 }
    }
}
