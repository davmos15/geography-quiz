package com.geoquiz.app.domain.model

/** One accepted answer in its normalised form (see `NormalizeInputUseCase`) and the country it names. */
data class AnswerAlias(
    val normalizedAlias: String,
    val country: Country
)
