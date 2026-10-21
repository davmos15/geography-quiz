package com.geoquiz.app.domain.model

import com.geoquiz.app.domain.map.RegionPreset

sealed class QuizCategory {
    data object AllCountries : QuizCategory()
    data class StartingWithLetter(val letter: Char) : QuizCategory()
    data class EndingWithLetter(val letter: Char) : QuizCategory()
    data class ContainingLetter(val letter: Char) : QuizCategory()
    data class ByRegion(val region: String) : QuizCategory()
    data class BySubregion(val subregion: String) : QuizCategory()
    data class ByNameLengthRange(val min: Int, val max: Int, val label: String) : QuizCategory()
    data class ByWordCount(val count: Int, val label: String) : QuizCategory()
    data class EndingWithSuffix(val suffix: String) : QuizCategory()
    data class ContainingWord(val word: String) : QuizCategory()
    data object DoubleLetter : QuizCategory()
    data object ConsonantCluster : QuizCategory()
    data object RepeatedLetter3 : QuizCategory()
    data object RepeatedLetter4 : QuizCategory()
    data object StartsEndsSame : QuizCategory()
    data object AllVowelsPresent : QuizCategory()
    data object IslandCountries : QuizCategory()
    data object UniqueLetters : QuizCategory()
    data object CardinalDirection : QuizCategory()
    data object CapitalMatchesCountry : QuizCategory()
    data object EndingInVowel : QuizCategory()
    data object SingleVowelType : QuizCategory()

    // Flag-specific categories
    data class FlagSingleColor(val color: String) : QuizCategory()
    data class FlagColorCombo(val colors: List<String>) : QuizCategory()
    data class FlagColorCount(val count: Int) : QuizCategory()
    data class FlagElement(val element: String) : QuizCategory()

    /**
     * World or one continent (task 5.2, Tap the map): [presetId] is a
     * [com.geoquiz.app.domain.map.RegionPreset.id]. No existing category type can express every
     * preset ([ByRegion]/[BySubregion] are each one of the app's regions or subregions, but North
     * America is three subregions), so this is its own type. Never offered in a classic mode
     * ([isOfferedIn] is always false for it): Tap the map is the only mode that offers it
     * ([com.geoquiz.app.domain.mode.tapmap.TapMapGameMode.offers]) and the only one that resolves
     * it to countries, via [com.geoquiz.app.domain.usecase.GetRegionMembersUseCase] (also used by
     * [com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase], which the mode's generator
     * reuses, task 5.0).
     */
    data class MapRegion(val presetId: String) : QuizCategory()

    /**
     * A fixed, named set of items that is not a classic category and has no classic-category
     * filter to express (task 6.2, US states and Canadian provinces: "just US states" or "just
     * Canada", not a region/subregion of countries). [label] is both the route value and the
     * display name, same as [ByRegion] for an ordinary continent, but [isOfferedIn] is always
     * false: unlike a real [ByRegion]/[BySubregion] value, a label such as "US states" would
     * otherwise be indistinguishable from a real region to other modes' `offers()` checks,
     * [AchievementRepository]'s region tracking, and [AnswerReviewScreen]'s "In {region}" text,
     * none of which this mode's labels mean anything to.
     */
    data class FixedRegion(val label: String) : QuizCategory()

    /**
     * The daily challenge round for one device-local calendar date (task 8.1, D4): [date] is
     * "yyyy-MM-dd", the same string
     * [com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGenerator] seeds a day's 10
     * questions from, so every device gets the same set on the same date. Never offered in a
     * classic mode ([isOfferedIn] is always false, like [MapRegion]/[FixedRegion]/[Practice]):
     * [com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode] is the only mode that
     * offers it, and its own setup screen starts a round directly rather than through a category
     * list. Never carried by a challenge link either (there is no entry for it in
     * [fromRouteOrNull], so an unknown "daily" type falls through to its `null` default, same as
     * [Practice]): a daily challenge is already the same for everyone on a given date, so there
     * is nothing to challenge a friend to that they could not already play themselves. Unlike
     * [Practice], its result *is* recorded ([isRecorded] keeps the default `!is Practice`): Stats
     * and "already played today" both need the row.
     */
    data class DailyChallenge(val date: String) : QuizCategory()

    /**
     * "Practise the ones you missed" (3.5c): exactly these items, in this order. [codes] are
     * distinct upper-case cca3 codes. Built from a finished quiz's misses
     * ([CompletedQuiz.practiceCategoryOrNull]), never offered in a category list and never carried
     * by a challenge link ([fromRouteOrNull] rejects it). Not recorded (D21, see [isRecorded]).
     */
    data class Practice(val codes: List<String>) : QuizCategory() {
        companion object {
            const val TYPE_KEY = "practice"
            private const val SEPARATOR = "+"
            private val CODE = Regex("[A-Z]{3}")

            /** Codes from a route value: well-formed ones only, first occurrence kept, order kept. */
            fun parseCodes(value: String): List<String> =
                value.split(SEPARATOR).filter { CODE.matches(it) }.distinct()

            fun routeValue(codes: List<String>): String = codes.joinToString(SEPARATOR)
        }
    }

    val displayName: String
        get() = when (this) {
            is AllCountries -> "All Countries"
            is StartingWithLetter -> "Starting with '$letter'"
            is EndingWithLetter -> "Ending with '$letter'"
            is ContainingLetter -> "Containing '$letter'"
            is ByRegion -> region
            is BySubregion -> subregion
            is ByNameLengthRange -> label
            is ByWordCount -> label
            is EndingWithSuffix -> "Ends with \"$suffix\""
            is ContainingWord -> "Contains \"$word\""
            is DoubleLetter -> "Double Letter"
            is ConsonantCluster -> "Consonant Cluster"
            is RepeatedLetter3 -> "Same Letter 3 Times"
            is RepeatedLetter4 -> "Same Letter 4+ Times"
            is StartsEndsSame -> "Starts & Ends Same"
            is AllVowelsPresent -> "All 5 Vowels"
            is IslandCountries -> "Island Nations"
            is UniqueLetters -> "All Unique Letters"
            is CardinalDirection -> "Cardinal Direction"
            is CapitalMatchesCountry -> "Same as Country"
            is EndingInVowel -> "Ending in a Vowel"
            is SingleVowelType -> "Single Vowel Type"
            is FlagSingleColor -> color.replaceFirstChar { it.uppercase() }
            is FlagColorCombo -> "Only " + colors.joinToString(" & ") { it.replaceFirstChar { c -> c.uppercase() } }
            is FlagColorCount -> "$count ${if (count == 1) "colour" else "colours"}"
            is FlagElement -> ELEMENT_DISPLAY_NAMES[element] ?: element.replaceFirstChar { it.uppercase() }
            // "world" -> "World", "north_america" -> "North America": matches region_world etc.
            // in strings.xml (no Android resource dependency here, same as every other name above).
            is MapRegion -> presetId.split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            is FixedRegion -> label
            // Same text as R.string.mode_daily_challenge_name.
            is DailyChallenge -> "Today's challenge"
            // Same text as R.string.practice_quiz_title, which Results shows instead.
            is Practice -> "Practise your misses"
        }

    val description: String?
        get() = when (this) {
            is DoubleLetter -> "Countries with consecutive identical letters"
            is ConsonantCluster -> "Countries with 3+ consonants in a row"
            is RepeatedLetter3 -> "Countries where one letter appears exactly 3 times"
            is RepeatedLetter4 -> "Countries where one letter appears 4 or more times"
            is StartsEndsSame -> "Countries that begin and end with the same letter"
            is AllVowelsPresent -> "Countries whose name contains all 5 vowels: A, E, I, O, U"
            is UniqueLetters -> "Countries where no letter in the name repeats"
            is CardinalDirection -> "Countries with North, South, East or West in the name"
            is CapitalMatchesCountry -> "Capitals that share their country's name"
            is EndingInVowel -> "Countries whose name ends with A, E, I, O or U"
            is SingleVowelType -> "Countries whose name contains only one type of vowel"
            is IslandCountries -> "Countries with 'Island' in the name"
            is EndingWithSuffix -> "Countries ending with \"${suffix}\""
            is ContainingWord -> "Countries containing \"${word}\""
            else -> null
        }

    val typeKey: String
        get() = when (this) {
            is AllCountries -> "all"
            is StartingWithLetter -> "startletter"
            is EndingWithLetter -> "endletter"
            is ContainingLetter -> "containletter"
            is ByRegion -> "region"
            is BySubregion -> "subregion"
            is ByNameLengthRange -> "lengthrange"
            is ByWordCount -> "wordcount"
            is EndingWithSuffix -> "endsuffix"
            is ContainingWord -> "containword"
            is DoubleLetter -> "doubleletter"
            is ConsonantCluster -> "consonantcluster"
            is RepeatedLetter3 -> "repeatedletter3"
            is RepeatedLetter4 -> "repeatedletter4"
            is StartsEndsSame -> "startsendssame"
            is AllVowelsPresent -> "allvowels"
            is IslandCountries -> "island"
            is UniqueLetters -> "uniqueletters"
            is CardinalDirection -> "cardinal"
            is CapitalMatchesCountry -> "capitalmatches"
            is EndingInVowel -> "endvowel"
            is SingleVowelType -> "singlevowel"
            is FlagSingleColor -> "flagcolor"
            is FlagColorCombo -> "flagcombo"
            is FlagColorCount -> "flagcount"
            is FlagElement -> "flagelement"
            is MapRegion -> "map_region"
            is FixedRegion -> "fixed_region"
            is DailyChallenge -> "daily"
            is Practice -> Practice.TYPE_KEY
        }

    val valueKey: String
        get() = when (this) {
            is AllCountries -> "_"
            is StartingWithLetter -> letter.toString()
            is EndingWithLetter -> letter.toString()
            is ContainingLetter -> letter.toString()
            is ByRegion -> region
            is BySubregion -> subregion
            is ByNameLengthRange -> "$min-$max"
            is ByWordCount -> count.toString()
            is EndingWithSuffix -> suffix
            is ContainingWord -> word
            is DoubleLetter -> "_"
            is ConsonantCluster -> "_"
            is RepeatedLetter3 -> "_"
            is RepeatedLetter4 -> "_"
            is StartsEndsSame -> "_"
            is AllVowelsPresent -> "_"
            is IslandCountries -> "_"
            is UniqueLetters -> "_"
            is CardinalDirection -> "_"
            is CapitalMatchesCountry -> "_"
            is EndingInVowel -> "_"
            is SingleVowelType -> "_"
            is FlagSingleColor -> color
            is FlagColorCombo -> colors.sorted().joinToString("+")
            is FlagColorCount -> count.toString()
            is FlagElement -> element
            is MapRegion -> presetId
            is FixedRegion -> label
            is DailyChallenge -> date
            // Quiz order, not sorted.
            is Practice -> Practice.routeValue(codes)
        }

    companion object {
        val ELEMENT_DISPLAY_NAMES = mapOf(
            "plant" to "Plants & Trees",
            "animal" to "Animals",
            "sun" to "Sun",
            "moon" to "Moon",
            "constellation" to "Stars & Constellations",
            "union_jack" to "Union Jack",
            "coat_of_arms" to "Coat of Arms",
            "text" to "Text & Script"
        )

        fun fromRoute(type: String, value: String): QuizCategory = try {
            when (type) {
                "all" -> AllCountries
                "startletter" -> if (value.isNotEmpty()) StartingWithLetter(value.first()) else AllCountries
                "endletter" -> if (value.isNotEmpty()) EndingWithLetter(value.first()) else AllCountries
                "containletter" -> if (value.isNotEmpty()) ContainingLetter(value.first()) else AllCountries
                "region" -> ByRegion(value)
                "subregion" -> BySubregion(value)
                "lengthrange" -> {
                    val parts = value.split("-")
                    if (parts.size == 2) {
                        val min = parts[0].toIntOrNull()
                        val max = parts[1].toIntOrNull()
                        if (min != null && max != null) ByNameLengthRange(min, max, "") else AllCountries
                    } else AllCountries
                }
                "wordcount" -> value.toIntOrNull()?.let { ByWordCount(it, "") } ?: AllCountries
                "endsuffix" -> EndingWithSuffix(value)
                "containword" -> ContainingWord(value)
                "doubleletter" -> DoubleLetter
                "consonantcluster" -> ConsonantCluster
                "repeatedletter3" -> RepeatedLetter3
                "repeatedletter4" -> RepeatedLetter4
                "startsendssame" -> StartsEndsSame
                "allvowels" -> AllVowelsPresent
                "island" -> IslandCountries
                "uniqueletters" -> UniqueLetters
                "cardinal" -> CardinalDirection
                "capitalmatches" -> CapitalMatchesCountry
                "endvowel" -> EndingInVowel
                "singlevowel" -> SingleVowelType
                "flagcolor" -> FlagSingleColor(value)
                "flagcombo" -> FlagColorCombo(value.split("+").sorted())
                "flagcount" -> value.toIntOrNull()?.let { FlagColorCount(it) } ?: AllCountries
                "flagelement" -> FlagElement(value)
                "map_region" -> RegionPreset.fromId(value)?.let { MapRegion(value) } ?: AllCountries
                "fixed_region" -> FixedRegion(value)
                "daily" -> if (ISO_DATE_VALUE.matches(value)) DailyChallenge(value) else AllCountries
                Practice.TYPE_KEY -> Practice.parseCodes(value).takeIf { it.isNotEmpty() }
                    ?.let { Practice(it) } ?: AllCountries
                else -> AllCountries
            }
        } catch (_: Exception) {
            AllCountries
        }

        /** "yyyy-MM-dd" as [DailyChallenge.date] and [com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeDate.today] produce it. */
        private val ISO_DATE_VALUE = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val LETTER_VALUE = Regex("[A-Z]")
        private val PLACE_VALUE = Regex("[A-Za-z][A-Za-z .,'&-]{0,39}")
        private val WORD_VALUE = Regex("[A-Za-z]{1,20}")
        private val LENGTH_RANGE_VALUE = Regex("(\\d{1,2})-(\\d{1,2})")
        private val WORD_COUNT_VALUE = Regex("-?[1-9]")
        private val COLOUR_VALUE = Regex("[a-z]{1,20}")
        private val ELEMENT_VALUE = Regex("[a-z_]{1,30}")
        private const val NO_VALUE = "_"
        private const val MAX_NAME_LENGTH = 60
        private const val MAX_FLAG_COLOURS = 12

        /**
         * Strict counterpart of [fromRoute] for untrusted input such as challenge links:
         * returns null instead of falling back to [AllCountries] when [type] is unknown or
         * [value] is not in the shape the app itself produces. It checks the shape only;
         * whether a region or flag colour exists in the data is up to the caller.
         */
        fun fromRouteOrNull(type: String, value: String): QuizCategory? {
            fun noValue(category: QuizCategory) = if (value == NO_VALUE) category else null
            return when (type) {
                "all" -> noValue(AllCountries)
                "startletter" -> if (LETTER_VALUE.matches(value)) StartingWithLetter(value[0]) else null
                "endletter" -> if (LETTER_VALUE.matches(value)) EndingWithLetter(value[0]) else null
                "containletter" -> if (LETTER_VALUE.matches(value)) ContainingLetter(value[0]) else null
                "region" -> if (PLACE_VALUE.matches(value)) ByRegion(value) else null
                "subregion" -> if (PLACE_VALUE.matches(value)) BySubregion(value) else null
                "lengthrange" -> LENGTH_RANGE_VALUE.matchEntire(value)?.let { match ->
                    val min = match.groupValues[1].toInt()
                    val max = match.groupValues[2].toInt()
                    if (min in 1..max && max <= MAX_NAME_LENGTH) {
                        ByNameLengthRange(min, max, lengthRangeLabel(min, max))
                    } else null
                }
                "wordcount" -> if (WORD_COUNT_VALUE.matches(value)) {
                    val count = value.toInt()
                    ByWordCount(count, wordCountLabel(count))
                } else null
                "endsuffix" -> if (WORD_VALUE.matches(value)) EndingWithSuffix(value) else null
                "containword" -> if (WORD_VALUE.matches(value)) ContainingWord(value) else null
                "doubleletter" -> noValue(DoubleLetter)
                "consonantcluster" -> noValue(ConsonantCluster)
                "repeatedletter3" -> noValue(RepeatedLetter3)
                "repeatedletter4" -> noValue(RepeatedLetter4)
                "startsendssame" -> noValue(StartsEndsSame)
                "allvowels" -> noValue(AllVowelsPresent)
                "island" -> noValue(IslandCountries)
                "uniqueletters" -> noValue(UniqueLetters)
                "cardinal" -> noValue(CardinalDirection)
                "capitalmatches" -> noValue(CapitalMatchesCountry)
                "endvowel" -> noValue(EndingInVowel)
                "singlevowel" -> noValue(SingleVowelType)
                "flagcolor" -> if (COLOUR_VALUE.matches(value)) FlagSingleColor(value) else null
                "flagcombo" -> {
                    val colours = value.split("+")
                    val wellFormed = colours.size in 2..3 &&
                        colours.all { COLOUR_VALUE.matches(it) } &&
                        colours.distinct().size == colours.size &&
                        colours == colours.sorted()
                    if (wellFormed) FlagColorCombo(colours) else null
                }
                "flagcount" -> value.takeIf { it.length <= 2 }?.toIntOrNull()
                    ?.takeIf { it in 1..MAX_FLAG_COLOURS }
                    ?.let { FlagColorCount(it) }
                "flagelement" -> if (ELEMENT_VALUE.matches(value)) FlagElement(value) else null
                // Tap the map resolves this itself (GetRegionMembersUseCase); never a challenge
                // link, same as Practice below, since a classic mode never offers it (D35).
                "map_region" -> if (RegionPreset.fromId(value) != null) MapRegion(value) else null
                // US/CA regions resolves this itself (UsCaRegionSet); never a challenge link,
                // same reasoning as map_region above - isOfferedIn is always false for it.
                "fixed_region" -> null
                // A practice set is the player's own misses: never something to send to a friend.
                Practice.TYPE_KEY -> null
                // Daily challenge resolves itself from today's date (DailyChallengeDate); never a
                // challenge link, same reasoning as fixed_region above - everyone on the same date
                // already plays the same round, so there is nothing to send. No "daily" case here
                // at all (falls through to the else below), same treatment as every other unknown
                // type.
                else -> null
            }
        }

        /** Labels as the category list shows them, so a challenge never has a blank title. */
        private fun lengthRangeLabel(min: Int, max: Int): String =
            if (min == max) "$min letters" else "$min–$max letters"

        private fun wordCountLabel(count: Int): String = when {
            count == 1 -> "One-Word Names"
            count == 2 -> "Two-Word Names"
            count == -2 -> "Multi-Word Names"
            count < 0 -> "${-count}+ Words"
            else -> "$count Words"
        }
    }

    val isFlagCategory: Boolean
        get() = this is FlagSingleColor || this is FlagColorCombo ||
            this is FlagColorCount || this is FlagElement

    /**
     * Whether the app offers this category in a classic [mode]; challenge links and pins must
     * match. Always false for [MapRegion]: it is never offered in a classic mode (only Tap the
     * map's own setup screen offers it, via [com.geoquiz.app.domain.mode.GameMode.offers], D35).
     */
    fun isOfferedIn(mode: QuizMode): Boolean = when {
        this is Practice -> false
        this is MapRegion -> false
        this is FixedRegion -> false
        this is DailyChallenge -> false
        isFlagCategory -> mode == QuizMode.FLAGS
        this is CapitalMatchesCountry -> mode == QuizMode.CAPITALS
        else -> mode == QuizMode.COUNTRIES || mode == QuizMode.CAPITALS
    }

    /**
     * Whether finishing a quiz in this category is recorded. D21 (Dav, 2026-10-08): a practice
     * quiz ([Practice]) adds no history or stats row, no achievements, no Play Games unlocks or
     * leaderboard scores, and leaves no "Resume quiz" save. Its result is still saved for Results
     * and Answer review.
     */
    val isRecorded: Boolean
        get() = this !is Practice
}
