package com.geoquiz.app.ui.results

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode

object QuizResultHolder {
    var countries: List<Country> = emptyList()
    var answeredCodes: Set<String> = emptySet()
    var categoryName: String = ""
    var quizMode: QuizMode = QuizMode.COUNTRIES
    var incorrectGuessStrings: List<String> = emptyList()
    var category: QuizCategory = QuizCategory.AllCountries
    var allCountries: List<Country> = emptyList()
}
