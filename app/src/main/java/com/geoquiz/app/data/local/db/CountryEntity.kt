package com.geoquiz.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "countries",
    indices = [
        Index(value = ["commonName"]),
        Index(value = ["region"])
    ]
)
data class CountryEntity(
    @PrimaryKey val cca3: String,
    val commonName: String,
    val officialName: String,
    val region: String,
    val subregion: String,
    val nameLength: Int
)
