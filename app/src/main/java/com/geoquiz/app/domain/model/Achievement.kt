package com.geoquiz.app.domain.model

enum class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val tier: AchievementTier
) {
    FIRST_STEPS("first_steps", "First Steps", "Complete any quiz", AchievementTier.BRONZE),
    WORLD_TRAVELER("world_traveler", "World Traveler", "Complete the All Countries quiz", AchievementTier.GOLD),
    PERFECTIONIST("perfectionist", "Perfectionist", "Get 100% on any quiz", AchievementTier.GOLD),
    SPEED_DEMON("speed_demon", "Speed Demon", "Complete any quiz in under 2 minutes", AchievementTier.SILVER),
    REGION_MASTER("region_master", "Region Master", "Complete all 5 region quizzes", AchievementTier.GOLD),
    ALPHABET_SOUP("alphabet_soup", "Alphabet Soup", "Complete 10 starting-letter quizzes", AchievementTier.SILVER),
    CENTURY_CLUB("century_club", "Century Club", "Name 100+ countries in a single quiz", AchievementTier.SILVER),
    HALF_WAY_THERE("half_way_there", "Half Way There", "Name 50%+ in All Countries", AchievementTier.BRONZE),
    GEOGRAPHY_BUFF("geography_buff", "Geography Buff", "Complete 20 quizzes total", AchievementTier.GOLD),
    EXPLORER("explorer", "Explorer", "Try 5 different category groups", AchievementTier.BRONZE),

    // New Bronze
    QUICK_STUDY("quick_study", "Quick Study", "Complete a quiz in under 5 minutes", AchievementTier.BRONZE),
    ISLAND_HOPPER("island_hopper", "Island Hopper", "Complete the Island Countries quiz", AchievementTier.BRONZE),
    PATTERN_FINDER("pattern_finder", "Pattern Finder", "Complete any Letter Pattern quiz", AchievementTier.BRONZE),

    // New Silver
    WORLD_SCHOLAR("world_scholar", "World Scholar", "Name 75%+ in All Countries", AchievementTier.SILVER),
    LENGTH_MASTER("length_master", "Length Master", "Complete 5 different name length quizzes", AchievementTier.SILVER),
    VOWEL_HUNTER("vowel_hunter", "Vowel Hunter", "Complete the All Vowels Present quiz", AchievementTier.SILVER),
    CONTINENTAL("continental", "Continental", "Complete all region quizzes with 80%+", AchievementTier.SILVER),
    LETTER_COLLECTOR("letter_collector", "Letter Collector", "Complete 15 starting-letter quizzes", AchievementTier.SILVER),

    // New Gold
    ULTIMATE_GEOGRAPHER("ultimate_geographer", "Ultimate Geographer", "Name every country in the world", AchievementTier.GOLD),
    SPEED_MASTER("speed_master", "Speed Master", "Complete All Countries in under 15 minutes", AchievementTier.GOLD),
    PATTERN_MASTER("pattern_master", "Pattern Master", "Complete all Letter Pattern quizzes", AchievementTier.GOLD),
    SUBREGION_EXPLORER("subregion_explorer", "Subregion Explorer", "Complete 10 different subregion quizzes", AchievementTier.GOLD),

    // Capital achievements
    CAPITAL_BEGINNER("capital_beginner", "Capital Beginner", "Complete any capital quiz", AchievementTier.BRONZE),
    CAPITAL_EXPERT("capital_expert", "Capital Expert", "Get 80%+ on any capital quiz", AchievementTier.SILVER),
    WORLD_CAPITALS("world_capitals", "World Capitals", "Complete the All Capitals quiz", AchievementTier.GOLD),
    CAPITAL_SPEED_RUN("capital_speed_run", "Capital Speed Run", "Complete a capital quiz in under 2 minutes", AchievementTier.SILVER),
    CAPITAL_SCHOLAR("capital_scholar", "Capital Scholar", "Complete 10 capital quizzes", AchievementTier.SILVER),
    CAPITAL_MASTER("capital_master", "Capital Master", "Get 100% on the All Capitals quiz", AchievementTier.GOLD),

    // Flag achievements
    FLAG_SPOTTER("flag_spotter", "Flag Spotter", "Complete any flag quiz", AchievementTier.BRONZE),
    COLOR_EXPERT("color_expert", "Color Expert", "Complete 5 different flag color quizzes", AchievementTier.SILVER),
    RAINBOW("rainbow", "Rainbow", "Complete flag quizzes for 6 different colors", AchievementTier.GOLD),
    FLAG_PERFECTIONIST("flag_perfectionist", "Flag Perfectionist", "Get 100% on any flag quiz", AchievementTier.SILVER),
    VEXILLOLOGIST("vexillologist", "Vexillologist", "Complete 10 flag quizzes", AchievementTier.GOLD),
    FLAG_MASTER("flag_master", "Flag Master", "Complete the Flags of the World quiz with 80%+", AchievementTier.GOLD),

    // Incorrect guesses & hard mode achievements
    FLAWLESS("flawless", "Flawless", "Complete a quiz with 0 incorrect guesses", AchievementTier.BRONZE),
    SHARP_MIND("sharp_mind", "Sharp Mind", "Complete 5 quizzes with 0 incorrect guesses", AchievementTier.SILVER),
    SURVIVOR("survivor", "Survivor", "Complete a quiz in hard mode", AchievementTier.BRONZE),
    NERVES_OF_STEEL("nerves_of_steel", "Nerves of Steel", "Get 100% in hard mode", AchievementTier.GOLD)
}

enum class AchievementTier { BRONZE, SILVER, GOLD }
