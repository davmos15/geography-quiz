package com.geoquiz.app.domain.model

enum class QuizMode(val id: String, val displayLabel: String) {
    COUNTRIES("countries", "Countries"),
    CAPITALS("capitals", "Capitals"),
    FLAGS("flags", "Flags");

    companion object {
        fun fromId(id: String): QuizMode = entries.find { it.id == id } ?: COUNTRIES
    }
}
