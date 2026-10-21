package com.geoquiz.app.ui.play

import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.theme.CategoryHue

/**
 * The accent hue for a category group tile on Play (task P10-A.4): purely decorative, so
 * category tiles read as visually distinct from one another without relying on colour alone
 * (the title and quiz count are still shown as text). Mapped per (mode, group id); a group not
 * listed here (or a mode that isn't classic) falls back to the default tile colours.
 *
 * No page uses [CategoryHue.AMBER] (licence audit, task P10-R1): Countries' `name_length` and
 * Capitals' `subregions` were its only two uses, and amber sitting next to green or brown tiles
 * in the 2- or 3-column grid read too close to other trade dress elsewhere (L9). Swapped for
 * [CategoryHue.ORANGE] and [CategoryHue.BLUE] respectively - both already distinct from every
 * other hue on their own page, so this is a like-for-like swap, not a new clash.
 */
internal fun categoryHue(modeId: String, groupId: String): CategoryHue? = when (modeId) {
    QuizMode.COUNTRIES.id -> when (groupId) {
        "regions" -> CategoryHue.BLUE
        "subregions" -> CategoryHue.INDIGO
        "starting_letter" -> CategoryHue.TEAL
        "ending_letter" -> CategoryHue.CYAN
        "containing_letter" -> CategoryHue.GREEN
        "name_length" -> CategoryHue.ORANGE
        "letter_patterns" -> CategoryHue.PINK
        "word_patterns" -> CategoryHue.PURPLE
        "island_countries" -> CategoryHue.BROWN
        else -> null
    }

    QuizMode.CAPITALS.id -> when (groupId) {
        "regions" -> CategoryHue.ORANGE
        "subregions" -> CategoryHue.BLUE
        "starting_letter" -> CategoryHue.BROWN
        "ending_letter" -> CategoryHue.PINK
        "containing_letter" -> CategoryHue.PURPLE
        "name_length" -> CategoryHue.TEAL
        "letter_patterns" -> CategoryHue.INDIGO
        "word_patterns" -> CategoryHue.CYAN
        else -> null
    }

    QuizMode.FLAGS.id -> when (groupId) {
        "flag_single_color" -> CategoryHue.INDIGO
        "flag_two_color_combo" -> CategoryHue.PURPLE
        "flag_three_color_combo" -> CategoryHue.PINK
        "flag_color_count" -> CategoryHue.CYAN
        "flag_elements" -> CategoryHue.BLUE
        else -> null
    }

    else -> null
}
