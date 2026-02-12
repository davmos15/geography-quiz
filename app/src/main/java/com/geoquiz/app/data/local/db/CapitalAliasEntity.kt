package com.geoquiz.app.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "capital_aliases",
    foreignKeys = [
        ForeignKey(
            entity = CountryEntity::class,
            parentColumns = ["cca3"],
            childColumns = ["countryCca3"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["normalizedAlias"]),
        Index(value = ["countryCca3"])
    ]
)
data class CapitalAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val countryCca3: String,
    val alias: String,
    val normalizedAlias: String
)
