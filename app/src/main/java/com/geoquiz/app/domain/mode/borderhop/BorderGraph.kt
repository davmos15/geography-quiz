package com.geoquiz.app.domain.mode.borderhop

import kotlin.random.Random

/**
 * The land-border adjacency graph behind Border hop (task 6.1, D42/D44): 197 countries keyed by
 * cca3, built from `data/borders.json` (mledoze/countries ODbL 1.0, cross-checked against Natural
 * Earth; see `data/SOURCES.md`). "Island" means a country with no land-border edge at all (40 of
 * them, listed in `data/borders_report.md`): never a valid start or target. The graph has 43
 * connected components; the UK-Ireland and Haiti-Dominican Republic pairs are two-country
 * components (one edge each), so they are reachable but only ever give a 1-hop puzzle, which the
 * 2-6 hop rule excludes on its own.
 *
 * Pure domain logic, no Android or coroutine dependency, so it is cheap to unit test with a small
 * hand-built graph. [com.geoquiz.app.data.borderhop.BorderGraphRepository] loads the real one from
 * the bundled asset.
 */
class BorderGraph(private val adjacency: Map<String, List<String>>) {

    /** Every country code this graph knows about (197 in the bundled asset). */
    val countries: Set<String> get() = adjacency.keys

    /** [code]'s land-border neighbours, or an empty list if it has none or is unknown. */
    fun neighboursOf(code: String): List<String> = adjacency[code].orEmpty()

    /** True when [code] has no land border at all (never a valid start or target, D44). */
    fun isIsland(code: String): Boolean = neighboursOf(code).isEmpty()

    /**
     * BFS shortest-path length in hops between [from] and [to] (0 if they are the same country),
     * or null if either is unknown to the graph or they are not in the same connected component.
     */
    fun shortestPathLength(from: String, to: String): Int? {
        if (from !in adjacency || to !in adjacency) return null
        if (from == to) return 0
        val visited = mutableSetOf(from)
        var frontier = listOf(from)
        var hops = 0
        while (frontier.isNotEmpty()) {
            hops++
            val next = mutableListOf<String>()
            for (code in frontier) {
                for (neighbour in neighboursOf(code)) {
                    if (neighbour == to) return hops
                    if (visited.add(neighbour)) next += neighbour
                }
            }
            frontier = next
        }
        return null
    }

    /**
     * One shortest path between [from] and [to] (inclusive of both ends), or null if either is
     * unknown or they are not connected. When several shortest paths exist, which one comes back
     * is unspecified but deterministic for the same graph.
     */
    fun shortestPath(from: String, to: String): List<String>? {
        if (from !in adjacency || to !in adjacency) return null
        if (from == to) return listOf(from)
        val parent = mutableMapOf<String, String>()
        val visited = mutableSetOf(from)
        var frontier = listOf(from)
        while (frontier.isNotEmpty()) {
            val next = mutableListOf<String>()
            for (code in frontier) {
                for (neighbour in neighboursOf(code)) {
                    if (!visited.add(neighbour)) continue
                    parent[neighbour] = code
                    if (neighbour == to) return buildPath(parent, from, to)
                    next += neighbour
                }
            }
            frontier = next
        }
        return null
    }

    private fun buildPath(parent: Map<String, String>, from: String, to: String): List<String> {
        val path = mutableListOf(to)
        var current = to
        while (current != from) {
            current = parent.getValue(current)
            path += current
        }
        return path.asReversed()
    }

    /**
     * Distance in hops from [from] to every country reachable from it (BFS), [from] itself at 0.
     * Used by [randomPuzzle] (one BFS per candidate start, rather than a BFS per pair) and by the
     * [com.geoquiz.app.domain.mode.HintType.SHOW_VALID_HOP] hint (BFS from the target once per
     * hint use).
     */
    fun distancesFrom(start: String): Map<String, Int> {
        if (start !in adjacency) return emptyMap()
        val distances = mutableMapOf(start to 0)
        var frontier = listOf(start)
        var hops = 0
        while (frontier.isNotEmpty()) {
            hops++
            val next = mutableListOf<String>()
            for (code in frontier) {
                for (neighbour in neighboursOf(code)) {
                    if (neighbour in distances) continue
                    distances[neighbour] = hops
                    next += neighbour
                }
            }
            frontier = next
        }
        return distances
    }

    /**
     * Picks a random (start, target) pair from [candidates] whose shortest-path distance falls in
     * [hopRange], never an island (D44, the islands listed in `data/borders_report.md`) and never
     * a pair from a component too small to reach that many hops. Tries every candidate as a
     * possible start (in a random order) and returns as soon as one has a valid target; null if
     * none does (e.g. [candidates] is confined to a component smaller than [hopRange] needs).
     */
    fun randomPuzzle(candidates: Collection<String>, hopRange: IntRange, random: Random): BorderHopPuzzle? {
        val pool = candidates.filter { it in adjacency && !isIsland(it) }.distinct()
        if (pool.size < 2) return null
        val starts = pool.shuffled(random)
        for (start in starts) {
            val distances = distancesFrom(start)
            val targets = pool.filter { it != start && (distances[it] ?: -1) in hopRange }
            if (targets.isNotEmpty()) {
                val target = targets[random.nextInt(targets.size)]
                return BorderHopPuzzle(start, target, distances.getValue(target))
            }
        }
        return null
    }

    /**
     * Deterministic, exhaustive version of the question [randomPuzzle] answers with a random
     * pair: does any puzzle at all exist for [candidates] in [hopRange]? Used by the setup screen
     * (task 9.2 review) to decide whether a category is worth offering at a difficulty tier,
     * before a round is actually generated. Same exclusions as [randomPuzzle] (never an island,
     * D44; a candidate unknown to the graph is dropped), but checks every candidate as a possible
     * start rather than stopping at a random sample, so it never misses a feasible pair.
     * [randomPuzzle] already tries every candidate as a start (in a shuffled order) before giving
     * up, so for the same [candidates]/[hopRange] it returns null for every [Random] seed iff this
     * returns false.
     */
    fun hasPuzzle(candidates: Collection<String>, hopRange: IntRange): Boolean {
        val pool = candidates.filter { it in adjacency && !isIsland(it) }.distinct()
        if (pool.size < 2) return false
        return pool.any { start ->
            val distances = distancesFrom(start)
            pool.any { target -> target != start && (distances[target] ?: -1) in hopRange }
        }
    }
}

/**
 * A generated round (D44): hop from [startCode] to [targetCode] in as close to [parHops] (the
 * shortest-path length when the round began) hops as possible.
 */
data class BorderHopPuzzle(val startCode: String, val targetCode: String, val parHops: Int)
