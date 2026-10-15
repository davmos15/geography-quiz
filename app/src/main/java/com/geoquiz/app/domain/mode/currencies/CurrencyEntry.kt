package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.domain.model.Country

/**
 * One ISO 4217 currency code from `data/currencies.json` (mledoze/countries + Wikidata facts,
 * see `data/SOURCES.md`'s `data/currencies.json` row). [countries] is every cca3 among the app's
 * 197 that uses it (sorted); [shared] is true for the 13 codes used by two or more of them (D50).
 * [fact] is a one-line Wikidata fact to show after answering, or null when none passed review.
 */
data class CurrencyEntry(
    val code: String,
    val name: String,
    val symbol: String,
    val countries: List<String>,
    val shared: Boolean,
    val fact: String?
)

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
 * that bookkeeping. [buildAll] computes this once, over every country in the game, so a
 * representative never changes between calls or category filters:
 *
 * - **Not shared**: the one country whose [CurrencyData.primaryCurrency] actually names this
 *   code. Most non-shared codes are simply their one listed country's official currency, but
 *   three in this data are not: `CUC` (Cuba's convertible peso, obsolete since 2021 - Cuba's real
 *   primary is `CUP`), `KID` (the Kiribati dollar, coins only, pegged to and overshadowed by
 *   `AUD`) and `TVD` (the Tuvaluan dollar, same arrangement with `AUD`) are real codes with one
 *   listed country each but are nobody's actual primary currency in this data
 *   (`data/currencies_report.md`'s `primaryCurrencyNotes`). These three have no representative
 *   and are never asked about - the same spirit as D48's Panama/Zimbabwe/Palestine calls, applied
 *   here to three more codes the decision log does not name explicitly (flagged for the lead).
 * - **Shared**: the alphabetically-first cca3 in [CurrencyEntry.countries] that is not already
 *   the representative of a non-shared code, falling back to the alphabetically-first member if
 *   every member is already taken (never happens in this data, checked in
 *   `CurrencyItemsTest`). The "not already taken" rule matters: four countries - Bhutan, Brunei,
 *   The Bahamas and Lesotho - each have their own non-shared currency (`BTN`, `BND`, `BSD`,
 *   `LSL`) *and* are the alphabetically-first member of a different shared currency they also use
 *   day to day (`INR`, `SGD`, `USD`, `ZAR` respectively). Picking the same country as the
 *   representative of two different items would make two quiz items collide under one `cca3`
 *   code, which [com.geoquiz.app.domain.model.QuizState]'s `Set<String>` bookkeeping cannot tell
 *   apart; skipping to the next member keeps every representative in the whole game unique.
 *
 * The representative is used only for bookkeeping. The real question
 * ([CurrenciesChoiceGenerator], [CurrencyAnswerValidator]) always goes back to the
 * [CurrencyEntry] for the true rules: shared is always a choice with exactly one correct option
 * drawn from the real membership list; not shared is typed, judged against the representative
 * specifically.
 */
object CurrencyItems {

    /**
     * Every eligible [CurrencyItem], computed over [allCountriesByCode] (every country in the
     * game, keyed by `cca3`) so representative assignment never depends on a category filter.
     */
    fun buildAll(data: CurrencyData, allCountriesByCode: Map<String, Country>): List<CurrencyItem> {
        val nonSharedItems = data.entries.filter { !it.shared }.mapNotNull { entry ->
            val code = data.primaryCurrency.entries.firstOrNull { it.value == entry.code }?.key
                ?: return@mapNotNull null
            val country = allCountriesByCode[code] ?: return@mapNotNull null
            CurrencyItem(country, entry)
        }
        val usedCodes = nonSharedItems.mapTo(mutableSetOf()) { it.country.code }
        val sharedItems = data.entries.filter { it.shared }.mapNotNull { entry ->
            val sorted = entry.countries.sorted()
            val code = sorted.firstOrNull { it !in usedCodes } ?: sorted.firstOrNull()
            val country = code?.let { allCountriesByCode[it] } ?: return@mapNotNull null
            CurrencyItem(country, entry)
        }
        return nonSharedItems + sharedItems
    }

    /** [buildAll], restricted to the items whose representative's code is in [candidateCodes]. */
    fun build(
        data: CurrencyData,
        allCountriesByCode: Map<String, Country>,
        candidateCodes: Set<String>
    ): List<CurrencyItem> = buildAll(data, allCountriesByCode).filter { it.country.code in candidateCodes }
}
