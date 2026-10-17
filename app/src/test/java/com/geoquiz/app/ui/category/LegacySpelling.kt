package com.geoquiz.app.ui.category

/**
 * The one intended difference between [LegacyCategoryOptions] (a verbatim copy that must not be
 * "fixed") and today's [CategoryOptionsBuilder]: task 9.2 changed the "Number of colours" option
 * names from US to Australian spelling ("3 colors" -> "3 colours"). This rewrites only that
 * display name in the legacy output, so the oracle comparison still checks everything else
 * (counts, types, values, order) exactly. Route values such as `flagcount` are untouched.
 */
internal fun List<QuizOptionInfo>.withAustralianColourSpelling(): List<QuizOptionInfo> = map { option ->
    val match = LEGACY_COLOUR_COUNT.matchEntire(option.name)
    if (match == null) option else option.copy(name = "${match.groupValues[1]} colour${match.groupValues[2]}")
}

private val LEGACY_COLOUR_COUNT = Regex("""(\d+) color(s?)""")
