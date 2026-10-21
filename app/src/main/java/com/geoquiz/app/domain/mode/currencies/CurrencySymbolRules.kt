package com.geoquiz.app.domain.mode.currencies

/**
 * Currency symbol-leak rules for Currencies' clue card (task P10-B, Dav 2026-10-20): the clue
 * shows the currency's symbol only, never its name or ISO code, before an answer is locked in.
 * Two leak paths needed closing beyond D48/D50's existing representative/shared-currency rules:
 *
 * - [EXCLUDED_CODES]: a currency whose *symbol itself* spells out the country is dropped from the
 *   mode entirely - never a question, never a distractor's "true" currency, never counted towards
 *   [ambiguousSymbols] - rather than merely skipped as a question, which would still let a basket
 *   listing or a future reuse of the entry show a symbol that gives the country away on its own.
 *   Two shapes of leak, both in the real bundled data: an Arabic abbreviation of "country +
 *   currency" (AED "د.إ" = "Emirati dirham", BHD/DZD/IQD/JOD/KWD/LBP/LYD/MAD/OMR/QAR/SAR/TND the
 *   same pattern for their own countries), and a Latin-letter abbreviation of the country/currency
 *   name (MYR "RM" = Ringgit Malaysia, MWK "MK" = Malawi Kwacha, ZMW "ZK" = Zambian Kwacha, CDF
 *   "FC" = Franc Congolais, TOP "T$" = Tongan paʻanga, MRU "UM" = Ouguiya Mauritanienne).
 * - [ambiguousSymbols]: a symbol used by more than one surviving currency (e.g. "$" is 29
 *   different currencies in this data) cannot be typed unambiguously even when exactly one country
 *   owns that one currency code (e.g. the Albanian lek is the ALL code's only country, but its "L"
 *   symbol is also Honduras/Lesotho/Moldova/Eswatini's): [CurrencyEntry.isChoiceOnly] forces these
 *   into the same four-option "which of these" shape D50 already uses for a genuinely shared
 *   currency, at every tier. Grouping and distractor exclusion compare [normalizedSymbol] rather
 *   than the raw displayed string (code review round, task P10-R1): trimmed, trailing "." dropped,
 *   case-insensitive, so e.g. CHF's "Fr." groups with BIF/DJF/GNF/KMF/RWF/XAF/XOF's "Fr" - without
 *   this, Switzerland/Liechtenstein could still be offered as a safely-wrong distractor on a "Fr"
 *   question, which it is not. [SYMBOL_ALIASES] additionally folds together a couple of pairs that
 *   plain normalisation does not catch: BOB "Bs." and VES "Bs.S." both abbreviate "bolívar/boliviano"
 *   -style Spanish currency names with a different number of segments, so they stay distinct after
 *   trimming one trailing "." each ("bs" vs "bs.s") - documented here rather than derived.
 * - [CurrencyEntry.isChoiceOnly] is also forced for any entry with a non-empty
 *   [CurrencyEntry.listedInBasketBy] (code review round, task P10-R1): a basket currency's symbol
 *   alone is just as valid a clue for the basket country (e.g. Zimbabwe, D48) as for the official
 *   member, even when the entry itself is not [CurrencyEntry.shared] and its symbol is not
 *   otherwise ambiguous (e.g. BWP "P", also used informally in Zimbabwe).
 */
object CurrencySymbolRules {

    /** Dropped from the mode entirely: never asked, never a distractor's true currency. */
    val EXCLUDED_CODES: Set<String> = setOf(
        "AED", "BHD", "DZD", "IQD", "JOD", "KWD", "LBP", "LYD", "MAD", "OMR", "QAR", "SAR", "TND",
        "MYR", "MWK", "ZMW", "CDF", "TOP", "MRU"
    )

    /**
     * Explicit normalised-symbol aliases that plain trim/trailing-"."/case-fold normalisation does
     * not catch (task P10-R1): BOB "Bs." and VES "Bs.S.". Keyed and valued by [normalizedSymbol]'s
     * own output (lower-case, no trailing "."), not the raw display string.
     */
    private val SYMBOL_ALIASES: Map<String, String> = mapOf("bs.s" to "bs")

    /**
     * The form of [symbol] used to decide whether two currencies' symbols are "the same" for
     * grouping ([ambiguousSymbols]) and distractor exclusion ([countryUsesSymbol]): trimmed,
     * trailing "." dropped, case folded, then [SYMBOL_ALIASES] applied. Never used for display -
     * every surface keeps showing [CurrencyEntry.symbol] exactly as bundled.
     */
    fun normalizedSymbol(symbol: String): String {
        val normalized = symbol.trim().removeSuffix(".").lowercase()
        return SYMBOL_ALIASES[normalized] ?: normalized
    }

    /**
     * [rawEntries] with [EXCLUDED_CODES] dropped and [CurrencyEntry.ambiguousSymbol] set from the
     * surviving entries. Called once, where the bundled data is loaded
     * ([com.geoquiz.app.data.currencies.AssetCurrencyRepository]) - every other consumer
     * ([CurrencyItems], [CurrenciesChoiceGenerator], [CurrencyAnswerValidator],
     * `ui/currencies/CurrencyRoundState`) reads [CurrencyEntry.isChoiceOnly] directly rather than
     * re-deriving ambiguity itself.
     */
    fun classify(rawEntries: List<CurrencyEntry>): List<CurrencyEntry> {
        val eligible = rawEntries.filterNot { it.code in EXCLUDED_CODES }
        val ambiguous = ambiguousSymbols(eligible)
        return eligible.map { it.copy(ambiguousSymbol = normalizedSymbol(it.symbol) in ambiguous) }
    }

    /**
     * Normalised symbols ([normalizedSymbol]) shared by more than one of [entries] (assumed
     * already [EXCLUDED_CODES]-free).
     */
    fun ambiguousSymbols(entries: List<CurrencyEntry>): Set<String> =
        entries.groupingBy { normalizedSymbol(it.symbol) }.eachCount().filterValues { it > 1 }.keys

    /**
     * True if [countryCode] has any currency among [entries] - an official member
     * ([CurrencyEntry.countries]) or an unofficial basket listing ([CurrencyEntry.listedInBasketBy],
     * D48) - whose [normalizedSymbol] matches [symbol]'s. Used to keep a choice question's
     * distractors from showing a country that could, just as validly, be "the" answer for the same
     * symbol (task P10-B): broader than only excluding the asked entry's own members, since a
     * different currency with the same (normalised) symbol - or the asked country's own unofficial
     * basket currency - is just as ambiguous a "wrong" option.
     */
    fun countryUsesSymbol(countryCode: String, symbol: String, entries: List<CurrencyEntry>): Boolean {
        val target = normalizedSymbol(symbol)
        return entries.any {
            normalizedSymbol(it.symbol) == target && (countryCode in it.countries || countryCode in it.listedInBasketBy)
        }
    }
}
