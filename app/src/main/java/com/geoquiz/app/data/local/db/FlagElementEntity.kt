package com.geoquiz.app.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "flag_elements",
    primaryKeys = ["countryCca3", "element"],
    foreignKeys = [
        ForeignKey(
            entity = CountryEntity::class,
            parentColumns = ["cca3"],
            childColumns = ["countryCca3"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["element"]),
        Index(value = ["countryCca3"])
    ]
)
data class FlagElementEntity(
    val countryCca3: String,
    val element: String
)
