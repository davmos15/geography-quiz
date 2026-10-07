package com.geoquiz.app.testutil

import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.domain.model.Country

/** Small fixtures shared by the quiz, results and answer review tests. */
object TestQuizData {

    fun country(code: String, name: String, capital: String, region: String = "Europe") = Country(
        code = code,
        name = name,
        officialName = name,
        region = region,
        subregion = region,
        nameLength = name.length,
        capital = capital
    )

    val FRANCE = country("FRA", "France", "Paris")
    val GERMANY = country("DEU", "Germany", "Berlin")
    val AUSTRIA = country("AUT", "Austria", "Vienna")
    val PERU = country("PER", "Peru", "Lima", region = "Americas")

    /** France, Germany and Austria, in that (deliberately unsorted) order. */
    val THREE = listOf(FRANCE, GERMANY, AUSTRIA)

    fun completedQuiz(
        id: String = "result-1",
        quizModeId: String = "countries",
        categoryType: String = "all",
        categoryValue: String = "_",
        countryCodes: List<String> = THREE.map { it.code },
        answeredCodes: List<String> = listOf("FRA"),
        incorrectGuessStrings: List<String> = emptyList(),
        challengeId: String? = null
    ) = CompletedQuiz(
        id = id,
        quizModeId = quizModeId,
        categoryType = categoryType,
        categoryValue = categoryValue,
        categoryName = "All Countries",
        countryCodes = countryCodes,
        answeredCodes = answeredCodes,
        incorrectGuessStrings = incorrectGuessStrings,
        correct = answeredCodes.size,
        total = countryCodes.size,
        timeSeconds = 42,
        score = 0.33,
        perfectBonus = false,
        incorrectGuesses = incorrectGuessStrings.size,
        hardMode = false,
        challengeId = challengeId,
        completedAtMillis = 1_700_000_000_000L
    )
}
