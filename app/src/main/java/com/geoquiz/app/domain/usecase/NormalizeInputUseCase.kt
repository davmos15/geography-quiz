package com.geoquiz.app.domain.usecase

import java.text.Normalizer
import javax.inject.Inject

class NormalizeInputUseCase @Inject constructor() {

    operator fun invoke(input: String): String {
        if (input.isBlank()) return ""

        // NFD decompose and strip diacritics
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(DIACRITICS_REGEX, "")

        return noDiacritics
            .lowercase()
            .replace("-", " ")
            .replace("'", "")   // straight apostrophe
            .replace("\u2019", "") // right single curly quote
            .replace("\u2018", "") // left single curly quote
            .replace("\u02BC", "") // modifier letter apostrophe
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }

    companion object {
        private val DIACRITICS_REGEX = Regex("[\\p{InCombiningDiacriticalMarks}]")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
