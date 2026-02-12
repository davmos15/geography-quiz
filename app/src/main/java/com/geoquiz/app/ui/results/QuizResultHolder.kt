package com.geoquiz.app.ui.results

import com.geoquiz.app.domain.model.Country

object QuizResultHolder {
    var countries: List<Country> = emptyList()
    var answeredCodes: Set<String> = emptySet()
    var categoryName: String = ""
}
