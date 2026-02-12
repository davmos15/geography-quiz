package com.geoquiz.app.domain.model

data class QuizResult(
    val category: QuizCategory,
    val totalCountries: Int,
    val correctAnswers: Int,
    val timeElapsedSeconds: Int,
    val score: Double,
    val perfectBonus: Boolean
)
