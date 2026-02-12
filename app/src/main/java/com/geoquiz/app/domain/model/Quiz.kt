package com.geoquiz.app.domain.model

data class Quiz(
    val category: QuizCategory,
    val countries: List<Country>,
    val timerSeconds: Int? = null,
    val showCountryCount: Boolean = true
)
