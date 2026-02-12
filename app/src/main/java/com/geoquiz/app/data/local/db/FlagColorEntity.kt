package com.geoquiz.app.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "flag_colors",
    primaryKeys = ["countryCca3", "color"],
    foreignKeys = [
        ForeignKey(
            entity = CountryEntity::class,
            parentColumns = ["cca3"],
            childColumns = ["countryCca3"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["color"]),
        Index(value = ["countryCca3"])
    ]
)
data class FlagColorEntity(
    val countryCca3: String,
    val color: String
)
