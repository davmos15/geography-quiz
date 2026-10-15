package com.geoquiz.app.domain.mode

import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.model.QuizResult
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestGameModes
import com.geoquiz.app.testutil.TestQuizData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModeRegistryTest {

    private val registry = TestGameModes.registry()

    @Test
    fun `the three classic modes are registered under the QuizMode ids`() {
        assertEquals(listOf("countries", "capitals", "flags"), registry.all.map { it.id })
        assertEquals(QuizMode.entries.map { it.id }, registry.classic.map { it.id })
        for (mode in QuizMode.entries) {
            assertEquals(mode.id, registry[mode].id)
            assertSame(registry[mode].spec, mode.spec)
        }
    }

    @Test
    fun `lookups by id`() {
        assertEquals("capitals", registry.find("capitals")?.id)
        assertEquals("flags", registry.require("flags").id)
        assertEquals("countries", registry.findOrDefault("countries").id)
    }

    @Test
    fun `an unknown id is null from find, Countries from findOrDefault, and an error from require`() {
        assertNull(registry.find("no-such-mode"))
        assertNull(registry.find(""))
        assertEquals("countries", registry.findOrDefault("no-such-mode").id)
        val error = runCatching { registry.require("no-such-mode") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `classic modes support every difficulty, answer by typing and are not behind a flag`() {
        for (mode in registry.classic) {
            assertEquals(Difficulty.entries.toSet(), mode.spec.supportedDifficulties)
            assertEquals(AnswerType.TYPED_NAME, mode.spec.answerType)
            assertTrue(mode.spec.hintTypes.isEmpty())
            assertNull(mode.spec.featureFlag)
        }
    }

    @Test
    fun `classic icons match the tab icons`() {
        assertEquals(ModeIcon.GLOBE, registry[QuizMode.COUNTRIES].spec.icon)
        assertEquals(ModeIcon.LANDMARK, registry[QuizMode.CAPITALS].spec.icon)
        assertEquals(ModeIcon.FLAG, registry[QuizMode.FLAGS].spec.icon)
    }

    @Test
    fun `a flagged mode is hidden until its flag is on`() {
        val silhouettes = fakeMode("silhouettes", FeatureFlag.COUNTRY_SILHOUETTES, sortOrder = 10)
        val withNew = GameModeRegistry(registry.all.toSet() + silhouettes)

        assertEquals(4, withNew.all.size)
        assertEquals("silhouettes", withNew.all.last().id)
        assertEquals(3, withNew.available { false }.size)
        assertEquals(4, withNew.available { it == FeatureFlag.COUNTRY_SILHOUETTES }.size)
        // Classic modes stay the three QuizMode entries.
        assertEquals(QuizMode.entries.map { it.id }, withNew.classic.map { it.id })
    }

    @Test
    fun `silhouettes and tap_map are registered with their own icon, answer type and hints`() {
        val withNew = GameModeRegistry(registry.all.toSet() + TestGameModes.silhouettes() + TestGameModes.tapMap())

        val silhouettes = withNew.require("silhouettes")
        assertEquals(ModeIcon.SILHOUETTE, silhouettes.spec.icon)
        assertEquals(AnswerType.TYPED_NAME, silhouettes.spec.answerType)
        assertEquals(
            setOf(HintType.SHOW_REGION, HintType.FIRST_LETTER, HintType.SHOW_NEIGHBOURS),
            silhouettes.spec.hintTypes
        )
        assertEquals(FeatureFlag.COUNTRY_SILHOUETTES, silhouettes.spec.featureFlag)

        val tapMap = withNew.require("tap_map")
        assertEquals(ModeIcon.MAP_PIN, tapMap.spec.icon)
        assertEquals(AnswerType.MAP_TAP, tapMap.spec.answerType)
        assertEquals(setOf(HintType.FLASH_REGION), tapMap.spec.hintTypes)
        assertEquals(FeatureFlag.TAP_THE_MAP, tapMap.spec.featureFlag)

        // Hidden until their own flag is on; classic modes are unaffected either way.
        assertEquals(3, withNew.available { false }.size)
        assertEquals(4, withNew.available { it == FeatureFlag.COUNTRY_SILHOUETTES }.size)
        assertEquals(5, withNew.available { true }.size)
        assertEquals(QuizMode.entries.map { it.id }, withNew.classic.map { it.id })
    }

    @Test
    fun `silhouettes offers names, regions, letters and islands, not flags or capitals`() {
        val silhouettes = TestGameModes.silhouettes()

        assertTrue(silhouettes.offers(QuizCategory.AllCountries))
        assertTrue(silhouettes.offers(QuizCategory.ByRegion("Africa")))
        assertTrue(silhouettes.offers(QuizCategory.BySubregion("South America")))
        assertTrue(silhouettes.offers(QuizCategory.StartingWithLetter('A')))
        assertTrue(silhouettes.offers(QuizCategory.EndingWithLetter('A')))
        assertTrue(silhouettes.offers(QuizCategory.ContainingLetter('A')))
        assertTrue(silhouettes.offers(QuizCategory.IslandCountries))
        assertTrue(silhouettes.offers(QuizCategory.Practice(listOf("FRA"))))
        assertTrue(!silhouettes.offers(QuizCategory.FlagSingleColor("red")))
        assertTrue(!silhouettes.offers(QuizCategory.CapitalMatchesCountry))
        assertTrue(!silhouettes.offers(QuizCategory.DoubleLetter))
    }

    @Test
    fun `tap_map offers every region preset and practice, not letters, flags or region, subregion`() {
        val tapMap = TestGameModes.tapMap()

        assertTrue(tapMap.offers(QuizCategory.MapRegion("world")))
        assertTrue(tapMap.offers(QuizCategory.MapRegion("africa")))
        assertTrue(tapMap.offers(QuizCategory.MapRegion("asia")))
        assertTrue(tapMap.offers(QuizCategory.MapRegion("europe")))
        assertTrue(tapMap.offers(QuizCategory.MapRegion("oceania")))
        // North America spans three subregions, which is exactly why MapRegion exists.
        assertTrue(tapMap.offers(QuizCategory.MapRegion("north_america")))
        assertTrue(tapMap.offers(QuizCategory.MapRegion("south_america")))
        assertTrue(tapMap.offers(QuizCategory.Practice(listOf("FRA"))))
        assertTrue(!tapMap.offers(QuizCategory.MapRegion("atlantis")))
        assertTrue(!tapMap.offers(QuizCategory.AllCountries))
        assertTrue(!tapMap.offers(QuizCategory.ByRegion("Africa")))
        assertTrue(!tapMap.offers(QuizCategory.BySubregion("North America")))
        assertTrue(!tapMap.offers(QuizCategory.StartingWithLetter('A')))
        assertTrue(!tapMap.offers(QuizCategory.FlagSingleColor("red")))
    }

    @Test
    fun `the classic offers default matches QuizCategory isOfferedIn`() {
        for (mode in registry.classic) {
            val quizMode = QuizMode.entries.first { it.id == mode.id }
            assertEquals(QuizCategory.AllCountries.isOfferedIn(quizMode), mode.offers(QuizCategory.AllCountries))
            assertEquals(
                QuizCategory.FlagSingleColor("red").isOfferedIn(quizMode),
                mode.offers(QuizCategory.FlagSingleColor("red"))
            )
        }
    }

    @Test
    fun `registering two modes with the same id fails`() {
        val duplicate = fakeMode("countries", featureFlag = null, sortOrder = 5)
        val error = runCatching { GameModeRegistry(registry.all.toSet() + duplicate) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `classic scoring is the existing rule`() {
        val state = QuizState(
            quiz = Quiz(category = QuizCategory.AllCountries, countries = TestQuizData.THREE),
            answeredCountries = TestQuizData.THREE.map { it.code }.toSet()
        )
        for (mode in registry.classic) {
            val result = mode.scoring.score(state)
            assertEquals(3 * 1.2, result.score, 1e-9) // 3/3 * 3, perfect bonus
            assertTrue(result.perfectBonus)
        }
    }

    private fun fakeMode(id: String, featureFlag: FeatureFlag?, sortOrder: Int): GameMode = object : GameMode {
        override val spec = QuizMode.COUNTRIES.spec.copy(id = id, featureFlag = featureFlag, sortOrder = sortOrder)
        override val generator = QuestionGenerator { emptyList() }
        override val validator = AnswerValidator { _, _, _ -> error("unused") }
        override val choiceGenerator = ChoiceQuestionGenerator { _, _, _, _, _ -> error("unused") }
        override val scoring = ScoringRule { state ->
            QuizResult(
                category = state.quiz.category,
                totalCountries = 0,
                correctAnswers = 0,
                timeElapsedSeconds = 0,
                score = 0.0,
                perfectBonus = false,
                incorrectGuesses = 0
            )
        }
    }
}
