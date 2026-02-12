package com.geoquiz.app.domain.model

enum class CategoryGroup(
    val id: String,
    val displayName: String,
    val description: String,
    val colorIndex: Int
) {
    ALL_COUNTRIES("all_countries", "All Countries", "Name every country in the world", 0),
    REGIONS("regions", "By Region", "Quiz by continent", 1),
    SUBREGIONS("subregions", "By Subregion", "More specific areas", 2),
    STARTING_LETTER("starting_letter", "Starting Letter", "Countries beginning with...", 3),
    ENDING_LETTER("ending_letter", "Ending Letter", "Countries ending with...", 4),
    CONTAINING_LETTER("containing_letter", "Containing Letter", "Countries that contain...", 5),
    NAME_LENGTH("name_length", "Name Length", "Countries with exactly N letters", 6),
    LETTER_PATTERNS("letter_patterns", "Letter Patterns", "Double letters, palindromes, and more", 7),
    ISLAND_COUNTRIES("island_countries", "Islands", "Island nations of the world", 8);

    companion object {
        fun fromId(id: String): CategoryGroup? = entries.find { it.id == id }
    }
}
