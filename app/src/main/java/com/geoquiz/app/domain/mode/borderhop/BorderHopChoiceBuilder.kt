package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.domain.model.Country
import kotlin.random.Random

/**
 * Border hop's Easy options (task P10-C): [OPTION_COUNT] country codes for "which of these
 * borders the current country?", of which exactly one is a valid hop.
 *
 * - The valid option is a land neighbour of the end of the route that lies on a shortest route
 *   to the target (its distance to the target is one less, [BorderHopMoves.closerNeighbours]),
 *   picked at random when there are several.
 * - The others are never neighbours of the current country (so no second option is a hop at
 *   all), never on the route and never the target. They are drawn nearest first, so the right
 *   answer isn't obvious: countries two hops away (a neighbour's neighbour), then countries in
 *   the current country's subregion, then its region, then anywhere. A tier is shuffled before
 *   it is drawn from.
 *
 * The options come back shuffled. Null when no valid hop exists (the target can't be reached)
 * or the route is empty.
 */
object BorderHopChoiceBuilder {

    const val OPTION_COUNT = 4

    data class Choice(val options: List<String>, val validCode: String)

    fun build(
        graph: BorderGraph,
        path: List<String>,
        targetCode: String,
        countries: Collection<Country>,
        random: Random
    ): Choice? {
        val current = path.lastOrNull() ?: return null
        val valid = BorderHopMoves.closerNeighbours(graph, current, targetCode)
            .filter { it !in path }
            .takeIf { it.isNotEmpty() }
            ?.let { it[random.nextInt(it.size)] }
            ?: return null

        val excluded = HashSet<String>().apply {
            addAll(path)
            addAll(graph.neighboursOf(current))
            add(targetCode)
            add(valid)
        }
        val byCode = countries.associateBy { it.code }
        val currentCountry = byCode[current]
        val allCodes = (graph.countries + byCode.keys).filter { it !in excluded }

        val distances = graph.distancesFrom(current)
        val tiers = listOf(
            allCodes.filter { distances[it] == 2 },
            allCodes.filter { currentCountry != null && byCode[it]?.subregion == currentCountry.subregion },
            allCodes.filter { currentCountry != null && byCode[it]?.region == currentCountry.region },
            allCodes
        )
        val distractors = LinkedHashSet<String>()
        for (tier in tiers) {
            for (code in tier.sorted().shuffled(random)) {
                if (distractors.size == OPTION_COUNT - 1) break
                distractors += code
            }
        }
        return Choice(options = (distractors + valid).toList().shuffled(random), validCode = valid)
    }
}
