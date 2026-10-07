package com.geoquiz.app.domain.usecase

import java.text.Normalizer
import javax.inject.Inject

/**
 * Canonical form used to compare player input with stored answer aliases.
 *
 * Mirrored exactly by `normalize_input()` in `tools/data/export_aliases.py`, which
 * normalises the aliases stored in `static.db`. Change both together, regenerate the
 * data (see `data/README.md`) and bump `StaticDatabase.VERSION`.
 */
class NormalizeInputUseCase @Inject constructor() {

    operator fun invoke(input: String): String {
        if (input.isBlank()) return ""

        // NFD decompose and strip diacritics
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(DIACRITICS_REGEX, "")

        val collapsed = noDiacritics
            .lowercase()
            .replace("-", " ")
            .replace("'", "")   // straight apostrophe
            .replace("\u2019", "") // right single curly quote
            .replace("\u2018", "") // left single curly quote
            .replace("\u02BC", "") // modifier letter apostrophe
            .replace(".", "")   // "St." = "St", "D.C." = "DC"
            .replace(",", " ")
            .replace("&", " and ")
            .trim()
            .replace(WHITESPACE_REGEX, " ")

        val words = collapsed.split(" ").map { if (it == "st") "saint" else it }
        val withoutArticle = if (words.size > 1 && words[0] == "the") words.drop(1) else words
        return withoutArticle.joinToString(" ")
    }

    companion object {
        private val DIACRITICS_REGEX = Regex("[\\p{InCombiningDiacriticalMarks}]")
        // Explicit ASCII set: on Android (ICU) `\s` also matches Unicode spaces, unlike the
        // JVM tests and the Python port.
        private val WHITESPACE_REGEX = Regex("[ \\t\\n\\x0B\\f\\r]+")
    }
}
