package com.geoquiz.app.domain.model

enum class FlagCategoryGroup(
    val id: String,
    val displayName: String,
    val description: String,
    val colorIndex: Int
) {
    FLAG_SINGLE_COLOR("flag_single_color", "By Colour", "Flags containing a specific colour", 0),
    FLAG_TWO_COLOR_COMBO("flag_two_color_combo", "Two-Colour Combos", "Flags with a pair of colours", 1),
    FLAG_THREE_COLOR_COMBO("flag_three_color_combo", "Three-Colour Combos", "Flags with a trio of colours", 2),
    FLAG_COLOR_COUNT("flag_color_count", "Number of Colours", "Flags by how many colours they have", 3),
    FLAG_ELEMENTS("flag_elements", "Shapes & Objects", "Flags featuring specific symbols and designs", 4);

    companion object {
        fun fromId(id: String): FlagCategoryGroup? = entries.find { it.id == id }
    }
}
