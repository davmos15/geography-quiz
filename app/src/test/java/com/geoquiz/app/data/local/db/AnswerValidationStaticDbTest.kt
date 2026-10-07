package com.geoquiz.app.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.repository.CountryRepositoryImpl
import com.geoquiz.app.di.DatabaseModule
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.FuzzyAnswerMatcher
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Answer validation against the real shipped aliases (`assets/databases/static.db`):
 * every alias is accepted for its own country, and typo tolerance never hands an
 * answer to a different country. Expected results are worked out here by brute force
 * with an independent edit-distance implementation.
 */
@RunWith(RobolectricTestRunner::class)
class AnswerValidationStaticDbTest {

    private data class Row(val alias: String, val normalized: String, val cca3: String)

    private enum class Table(val sqlName: String) { COUNTRY("aliases"), CAPITAL("capital_aliases") }

    private lateinit var database: StaticDatabase
    private lateinit var repository: CountryRepositoryImpl
    private lateinit var validateAnswer: ValidateAnswerUseCase
    private lateinit var validateCapital: ValidateCapitalAnswerUseCase
    private lateinit var countries: Map<String, Country>
    private lateinit var state: QuizState
    private val normalize = NormalizeInputUseCase()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = DatabaseModule.provideStaticDatabase(context)
        repository = CountryRepositoryImpl(
            countryDao = database.countryDao(),
            capitalAliasDao = database.capitalAliasDao(),
            normalizeInput = normalize
        )
        validateAnswer = ValidateAnswerUseCase(repository, normalize)
        validateCapital = ValidateCapitalAnswerUseCase(repository, normalize)
        val all = repository.getAllCountries().first()
        countries = all.associateBy { it.code }
        // Every country is in the quiz and none is answered yet.
        state = QuizState(quiz = Quiz(QuizCategory.AllCountries, all))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun rows(table: Table): List<Row> {
        val out = mutableListOf<Row>()
        database.openHelper.readableDatabase
            .query("SELECT alias, normalizedAlias, countryCca3 FROM ${table.sqlName}").use { c ->
                while (c.moveToNext()) out += Row(c.getString(0), c.getString(1), c.getString(2))
            }
        return out
    }

    private suspend fun validate(table: Table, input: String, allowFuzzy: Boolean): AnswerResult =
        when (table) {
            Table.COUNTRY -> validateAnswer(input, state, allowFuzzy)
            Table.CAPITAL -> validateCapital(input, state, allowFuzzy)
        }

    @Test
    fun `every alias is accepted for its own country in both modes`() = runBlocking {
        for (table in Table.entries) {
            val all = rows(table)
            assertTrue("${table.sqlName} is empty", all.isNotEmpty())
            val failures = mutableListOf<String>()
            for (row in all) {
                val expected = AnswerResult.Correct(countries.getValue(row.cca3).name)
                for (allowFuzzy in listOf(true, false)) {
                    val actual = validate(table, row.alias, allowFuzzy)
                    if (actual != expected) failures += "${row.alias} (fuzzy=$allowFuzzy): $actual, expected $expected"
                }
            }
            assertTrue("${table.sqlName}: ${failures.size} failures\n${failures.joinToString("\n")}", failures.isEmpty())
        }
    }

    @Test
    fun `no normalised alias belongs to two countries`() {
        for (table in Table.entries) {
            val shared = rows(table).groupBy { it.normalized }
                .mapValues { (_, r) -> r.map { it.cca3 }.toSet() }
                .filterValues { it.size > 1 }
            assertTrue("${table.sqlName}: $shared", shared.isEmpty())
        }
    }

    @Test
    fun `single typos never resolve to a different country`() = runBlocking {
        for (table in Table.entries) {
            val all = rows(table)
            val exact = all.associate { it.normalized to it.cca3 }
            val long = all.filter { it.normalized.length > TYPO_MIN_LENGTH }
                .distinctBy { it.normalized to it.cca3 }

            // Typos to try for each long alias: every deletion and adjacent swap, plus, for
            // aliases close to another country's alias, every edit that moves towards it
            // (this includes the "half-way" strings one edit from both).
            val variants = mutableMapOf<String, String>() // variant -> source country
            val towards = mutableSetOf<String>() // variants that move towards another country
            for (row in long) {
                // Typos are only realistic in names typed on a Latin keyboard.
                if (!row.normalized.all { it in 'a'..'z' || it == ' ' }) continue
                for (v in deletionsAndSwaps(row.normalized)) variants.putIfAbsent(v, row.cca3)
            }
            var closePairs = 0
            for (a in long) for (b in long) {
                if (a.cca3 == b.cca3) continue
                val d = osa(a.normalized, b.normalized)
                if (d > 2) continue
                closePairs++
                for (v in allSingleEdits(a.normalized, b.normalized.toSet())) {
                    if (osa(v, b.normalized) < d) {
                        variants.putIfAbsent(v, a.cca3)
                        towards += v
                    }
                }
            }

            val appAliases = when (table) {
                Table.COUNTRY -> repository.getCountryAnswerAliases()
                Table.CAPITAL -> repository.getCapitalAnswerAliases()
            }
            val failures = mutableListOf<String>()
            var accepted = 0
            for ((variant, source) in variants) {
                // Variants that the normaliser would rewrite (e.g. a new "st" word) are not
                // one edit from the source any more; skip them.
                if (normalize(variant) != variant) continue
                val near = if (variant.length > TYPO_MIN_LENGTH) {
                    long.filter { withinOneEdit(variant, it.normalized) }.map { it.cca3 }.toSet()
                } else emptySet()
                // Variants between two countries go through the full validator in both
                // modes. The broad sweep (thousands of strings) checks the matcher on the
                // repository's alias list directly to keep the runtime down; the exact-match
                // and hard-mode rules around it are covered above and in the use case tests.
                val modes = if (variant in towards) listOf(true, false) else listOf(true)
                for (allowFuzzy in modes) {
                    val expected = expected(exact[variant], near, allowFuzzy)
                    val actual = if (variant in towards) {
                        validate(table, variant, allowFuzzy)
                    } else if (exact[variant] != null) {
                        expected // an exact alias; covered by the alias-coverage test
                    } else {
                        when (val m = FuzzyAnswerMatcher.match(variant, appAliases)) {
                            FuzzyAnswerMatcher.Match.None -> AnswerResult.Incorrect
                            is FuzzyAnswerMatcher.Match.Ambiguous -> AnswerResult.NearMiss
                            is FuzzyAnswerMatcher.Match.Unique -> AnswerResult.Correct(m.country.name, viaTypo = true)
                        }
                    }
                    if (actual != expected) {
                        failures += "'$variant' (typo of $source, fuzzy=$allowFuzzy): $actual, expected $expected"
                    }
                    // The property itself, independent of the oracle above: a typo of
                    // country A is only ever accepted as A, unless it is exactly another
                    // country's alias.
                    if (actual is AnswerResult.Correct && actual.viaTypo) {
                        accepted++
                        if (actual.countryName != countries.getValue(source).name) {
                            failures += "'$variant' (typo of $source) accepted as ${actual.countryName}"
                        }
                        if (near.size != 1) failures += "'$variant' accepted although near $near"
                    }
                }
            }
            assertTrue("${table.sqlName}: no typos tried", variants.isNotEmpty())
            assertTrue("${table.sqlName}: no typo was accepted", accepted > 0)
            if (table == Table.COUNTRY) assertTrue("expected close country pairs", closePairs > 0)
            assertTrue(
                "${table.sqlName}: ${failures.size} failures\n${failures.take(50).joinToString("\n")}",
                failures.isEmpty()
            )
        }
    }

    @Test
    fun `known close names`() = runBlocking {
        // Iceland and Ireland are one letter apart: both exact names win, and a typo
        // between them is never accepted.
        assertEquals(AnswerResult.Correct("Iceland"), validateAnswer("Iceland", state, true))
        assertEquals(AnswerResult.Correct("Ireland"), validateAnswer("Ireland", state, true))
        assertEquals(AnswerResult.NearMiss, validateAnswer("Ixeland", state, true))
        assertEquals(AnswerResult.Correct("Australia", viaTypo = true), validateAnswer("Austrailia", state, true))
        assertEquals(AnswerResult.NearMiss, validateAnswer("Austrailia", state, false))
        // Kingston (Jamaica) and Kingstown (Saint Vincent and the Grenadines).
        assertEquals(AnswerResult.Correct("Jamaica"), validateCapital("Kingston", state, true))
        assertEquals(
            AnswerResult.Correct(countries.getValue("VCT").name),
            validateCapital("Kingstown", state, true)
        )
        assertEquals(AnswerResult.NearMiss, validateCapital("Kingstonw", state, true))
        // Canonicalisation reaches the real tables.
        assertEquals(AnswerResult.Correct("Saint Lucia"), validateAnswer("St. Lucia", state, false))
        assertEquals(AnswerResult.Correct("Gambia"), validateAnswer("The Gambia", state, false))
        assertEquals(AnswerResult.Correct("Bahamas"), validateAnswer("the bahamas", state, false))
        assertEquals(
            AnswerResult.Correct("Bosnia and Herzegovina"),
            validateAnswer("Bosnia & Herzegovina", state, false)
        )
        assertEquals(AnswerResult.Correct("Grenada"), validateCapital("Saint George's", state, false))
        assertEquals(AnswerResult.Correct("United States"), validateCapital("Washington DC", state, false))
        // Short names are never matched loosely.
        assertEquals(AnswerResult.Incorrect, validateAnswer("Irann", state, true))
    }

    private fun expected(exactCca3: String?, near: Set<String>, allowFuzzy: Boolean): AnswerResult = when {
        exactCca3 != null -> AnswerResult.Correct(countries.getValue(exactCca3).name)
        near.isEmpty() -> AnswerResult.Incorrect
        near.size > 1 || !allowFuzzy -> AnswerResult.NearMiss
        else -> AnswerResult.Correct(countries.getValue(near.single()).name, viaTypo = true)
    }

    private fun deletionsAndSwaps(s: String): Set<String> = buildSet {
        for (i in s.indices) add(s.removeRange(i, i + 1))
        for (i in 0 until s.length - 1) {
            val c = s.toCharArray()
            c[i] = s[i + 1]; c[i + 1] = s[i]
            add(String(c))
        }
        remove(s)
    }

    private fun allSingleEdits(s: String, alphabet: Set<Char>): Set<String> = buildSet {
        addAll(deletionsAndSwaps(s))
        for (ch in alphabet) {
            for (i in s.indices) add(s.substring(0, i) + ch + s.substring(i + 1))
            for (i in 0..s.length) add(s.substring(0, i) + ch + s.substring(i))
        }
        remove(s)
    }

    /**
     * True if [a] and [b] are equal or one insertion, deletion, substitution or adjacent
     * swap apart. Direct check, independent of the app's dynamic-programming version.
     */
    private fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        if (long.length - short.length > 1) return false
        var start = 0
        while (start < short.length && short[start] == long[start]) start++
        if (short.length < long.length) {
            // One deletion: the rest must line up after skipping one character of `long`.
            return short.substring(start) == long.substring(start + 1)
        }
        // Same length: a single substitution or a swap of two adjacent characters.
        if (short.substring(start + 1) == long.substring(start + 1)) return true
        return start + 1 < short.length &&
            short[start] == long[start + 1] && short[start + 1] == long[start] &&
            short.substring(start + 2) == long.substring(start + 2)
    }

    /** Plain full-table optimal string alignment distance (independent of the app's). */
    private fun osa(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3 // never needed beyond 2 here
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[a.length][b.length]
    }

    private companion object {
        /** Names must be longer than this on both sides for typo matching. */
        const val TYPO_MIN_LENGTH = 6
    }
}
