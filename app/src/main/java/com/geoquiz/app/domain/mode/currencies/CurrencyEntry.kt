package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.domain.model.Country

/**
 * One ISO 4217 currency code from `data/currencies.json` (mledoze/countries + Wikidata facts,
 * see `data/SOURCES.md`'s `data/currencies.json` row). [countries] is every cca3 among the app's
 * 197 that uses it (sorted); [shared] is true for the 13 codes used by two or more of them (D50).
 * [fact] is a one-line Wikidata fact to show after answering, or null when none passed review.
 *
 * [listedInBasketBy] (code review round, task 7.1 follow-up): countries that unofficially also
 * circulate this currency (currently only Zimbabwe, D48's basket decision) but are never a true
 * `countries` member - excluded from this currency's true-member set on purpose, since counting
 * them would break "exactly one valid option". They must still be excluded from the *wrong*-option
 * pool too (a basket country is a genuinely ambiguous distractor, not a safely-wrong one) -
 * see [CurrenciesChoiceGenerator].
 */
data class CurrencyEntry(
    val code: String,
    val name: String,
    val symbol: String,
    val countries: List<String>,
    val shared: Boolean,
    val fact: String?,
    val listedInBasketBy: List<String> = emptyList(),
    /**
     * True if [symbol] is also used by another currency in the bundled data (task P10-B, Dav
     * 2026-10-20: the Currencies clue now shows the symbol only, so a currency with exactly one
     * owning country can still have more than one valid-looking typed answer when its symbol -
     * not its code - is shared, e.g. the Albanian lek's "L" is also Honduras/Lesotho/Moldova/
     * Eswatini's symbol). Computed once by [CurrencySymbolRules.classify] when the bundled data
     * loads, never by callers constructing a [CurrencyEntry] fixture directly (defaults to
     * `false`, same as every pre-existing test fixture).
     */
    val ambiguousSymbol: Boolean = false
) {
    /**
     * Always a four-option "Which of these uses [symbol]?" question, at every tier, never typed
     * (task P10-B/P10-R1): true for a genuinely [shared] currency (D50), one whose [symbol] is
     * [ambiguousSymbol] even though it has only one owning country, or one with a non-empty
     * [listedInBasketBy] - the basket country is just as valid an answer from the symbol alone
     * (e.g. BWP "P", Zimbabwe also uses it informally, D48), even when [shared] is false and the
     * symbol is not otherwise ambiguous.
     */
    val isChoiceOnly: Boolean get() = shared || ambiguousSymbol || listedInBasketBy.isNotEmpty()
}

/** Every currency entry plus the country-to-primary-currency map (task 7.1, D48). */
data class CurrencyData(
    val entries: List<CurrencyEntry>,
    /** Country cca3 -> its official primary currency code; null only for Palestine (`PSE`, D48). */
    val primaryCurrency: Map<String, String?>
)

/**
 * One quiz item: the player is asked about [entry] (a currency). [country] represents it for
 * every place this codebase tracks progress by plain `cca3` string
 * ([com.geoquiz.app.domain.model.QuizState.answeredCountries], Practice sets, Answer review) -
 * see [CurrencyItems] for how it is picked and why one country can stand in for a whole currency.
 */
data class CurrencyItem(val country: Country, val entry: CurrencyEntry)

/**
 * Turns raw [CurrencyData] into the [CurrencyItem]s the player can be asked about (task 7.1).
 *
 * Every other mode interface in `domain/mode/GameMode.kt` is built around "one country = one
 * item": [com.geoquiz.app.domain.mode.AnswerValidator] judges a typed country name,
 * [com.geoquiz.app.domain.mode.ChoiceQuestionGenerator] builds a question about a target
 * [Country], and [com.geoquiz.app.domain.model.QuizState] tracks progress by `cca3` code. A
 * currency can stand for many countries (shared, e.g. the euro) or exactly one (e.g. the Albanian
 * lek), so each eligible currency code needs exactly one *representative* country to carry all of
 * that bookkeeping.
 *
 * [build] computes this **per call, scoped to [candidateCodes]** (code review round, task 7.1
 * follow-up: the original version assigned a shared currency's representative once, globally,
 * over every country in the game - so a category filter that happened not to contain that one
 * global representative lost the currency entirely, sometimes losing *every* currency a filter
 * had (e.g. Micronesia: FSM/MHL/PLW use USD, KIR/NRU use AUD, and neither global representative
 * was ever a Micronesian country), even though the setup screen still advertised real countries.
 * [buildAll] is the full-game case ([candidateCodes] = every known country), used where no
 * category filter is in play:
 *
 * - **Not shared**: the one country whose [CurrencyData.primaryCurrency] actually names this
 *   code, if that country is in [candidateCodes]. Most non-shared codes are simply their one
 *   listed country's official currency, but three in this data are not: `CUC` (Cuba's convertible
 *   peso, obsolete since 2021 - Cuba's real primary is `CUP`), `KID` (the Kiribati dollar, coins
 *   only, pegged to and overshadowed by `AUD`) and `TVD` (the Tuvaluan dollar, same arrangement
 *   with `AUD`) are real codes with one listed country each but are nobody's actual primary
 *   currency in this data (`data/currencies_report.md`'s `primaryCurrencyNotes`). These three have
 *   no representative in any round and are never typed-asked about directly - the same spirit as
 *   D48's Panama/Zimbabwe/Palestine calls, applied here to three more codes the decision log does
 *   not name explicitly (flagged for the lead). Kiribati and Tuvalu are not excluded from the mode
 *   altogether, though: both are true members of the shared `AUD` entry, so a filter that includes
 *   either of them (and does not also include a "closer" AUD member) can still ask about them via
 *   `AUD`'s multiple-choice question - this was already true before this fix and needed no change.
 * - **Shared**: the alphabetically-first cca3 in [CurrencyEntry.countries] that is both in
 *   [candidateCodes] and not already the representative of another item *in this same call*. If
 *   every in-scope member is already taken, the entry has no representative in this call (dropped
 *   entirely, never a duplicate) - tightened from a previous version that fell back to reusing an
 *   already-taken code; a duplicate representative would make two quiz items collide under one
 *   `cca3` code, which [com.geoquiz.app.domain.model.QuizState]'s `Set<String>` bookkeeping cannot
 *   tell apart, so dropping the item is strictly safer than a collision. Never happens in the real
 *   data at the full-game scope (checked in `CurrencyItemsTest`), and a per-category scope can only
 *   ever have *fewer* candidates competing for the same representative, not more, so this remains
 *   just as unreachable in practice as it always was. The "not already taken" rule still matters
 *   at full scope: four countries - Bhutan, Brunei, The Bahamas and Lesotho - each have their own
 *   non-shared currency (`BTN`, `BND`, `BSD`, `LSL`) *and* are the alphabetically-first member of
 *   a different shared currency they also use day to day (`INR`, `SGD`, `USD`, `ZAR`
 *   respectively); skipping to the next in-scope member avoids the same collision.
 *
 * The representative is used only for bookkeeping. The real question
 * ([CurrenciesChoiceGenerator], [CurrencyAnswerValidator]) always goes back to the
 * [CurrencyEntry] for the true rules: shared is always a choice with exactly one correct option
 * drawn from the real membership list; not shared is typed, judged against the representative
 * specifically.
 */
object CurrencyItems {

    /** [build], scoped to every country in [allCountriesByCode] - the full-game case. */
    fun buildAll(data: CurrencyData, allCountriesByCode: Map<String, Country>): List<CurrencyItem> =
        build(data, allCountriesByCode, allCountriesByCode.keys)

    /**
     * Every eligible [CurrencyItem] whose representative can be chosen from [candidateCodes]
     * (looked up in [allCountriesByCode]). See the class KDoc: representative assignment is
     * scoped to this one call, not fixed globally, so the same currency can have a different
     * representative in a different [candidateCodes] - that is by design, since the
     * representative is bookkeeping-only and the real question always goes back to the
     * [CurrencyEntry] itself.
     */
    fun build(
        data: CurrencyData,
        allCountriesByCode: Map<String, Country>,
        candidateCodes: Set<String>
    ): List<CurrencyItem> {
        val nonSharedItems = data.entries.filter { !it.shared }.mapNotNull { entry ->
            val code = data.primaryCurrency.entries.firstOrNull { it.value == entry.code }?.key
                ?: return@mapNotNull null
            if (code !in candidateCodes) return@mapNotNull null
            val country = allCountriesByCode[code] ?: return@mapNotNull null
            CurrencyItem(country, entry)
        }
        val usedCodes = nonSharedItems.mapTo(mutableSetOf()) { it.country.code }
        val sharedItems = data.entries.filter { it.shared }.mapNotNull { entry ->
            val sorted = entry.countries.filter { it in candidateCodes }.sorted()
            val code = sorted.firstOrNull { it !in usedCodes } ?: return@mapNotNull null
            val country = allCountriesByCode[code] ?: return@mapNotNull null
            usedCodes += code
            CurrencyItem(country, entry)
        }
        return nonSharedItems + sharedItems
    }
}
