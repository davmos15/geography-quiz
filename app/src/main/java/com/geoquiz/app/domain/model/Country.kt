package com.geoquiz.app.domain.model

data class Country(
    val code: String,
    val name: String,
    val officialName: String,
    val region: String,
    val subregion: String,
    val nameLength: Int
)
