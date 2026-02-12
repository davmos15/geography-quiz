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
    EXPLORER("explorer", "Explorer", "Try 5 different category groups", AchievementTier.BRONZE)
}

enum class AchievementTier { BRONZE, SILVER, GOLD }
