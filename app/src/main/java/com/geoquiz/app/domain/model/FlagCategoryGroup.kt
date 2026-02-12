package com.geoquiz.app.domain.model

enum class FlagCategoryGroup(
    val id: String,
    val displayName: String,
    val description: String,
    val colorIndex: Int
) {
    FLAG_SINGLE_COLOR("flag_single_color", "By Color", "Flags containing a specific color", 0),
    FLAG_TWO_COLOR_COMBO("flag_two_color_combo", "Two-Color Combos", "Flags with a pair of colors", 1),
    FLAG_THREE_COLOR_COMBO("flag_three_color_combo", "Three-Color Combos", "Flags with a trio of colors", 2),
    FLAG_COLOR_COUNT("flag_color_count", "Number of Colors", "Flags by how many colors they have", 3);

    companion object {
        fun fromId(id: String): FlagCategoryGroup? = entries.find { it.id == id }
    }
}
