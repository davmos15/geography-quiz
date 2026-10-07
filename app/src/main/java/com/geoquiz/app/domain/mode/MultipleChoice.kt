package com.geoquiz.app.domain.mode

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import javax.inject.Inject
import kotlin.random.Random

/*
 * Multiple choice for the Easy tier (task 3.2b, D14). Each mode builds one question per quiz item
 * with its own ChoiceQuestionGenerator; QuizViewModel decides the order and records the picks.
 */

/** Number of options a question shows when the data allows it. */
const val CHOICE_OPTION_COUNT = 4

/**
 * One option. [code] is the cca3 of the country the option stands for (for a capital, the country
 * whose capital it is); [label] is what the player sees and what a wrong pick is recorded as.
 */
data class ChoiceOption(val code: String, val label: String)

/** What the question asks. The UI turns it into text (and a flag image for [FlagOf]). */
sealed interface ChoicePrompt {
    /** "Which of these fits <category>?": spot the one country in the quiz set. */
    data class InSet(val categoryName: String) : ChoicePrompt

    /** "What is the capital of <country>?" */
    data class CapitalOf(val countryName: String) : ChoicePrompt

    /** Shows the flag of [code]; pick its country. The UI must not name the country. */
    data class FlagOf(val code: String) : ChoicePrompt
}

/**
 * A multiple-choice question about the quiz item [targetCode]. [options] are shuffled, distinct
 * by code and by label, and include the target's option exactly once.
 */
data class ChoiceQuestion(
    val targetCode: String,
    val options: List<ChoiceOption>,
    val prompt: ChoicePrompt
) {
    /** The option that answers the question. */
    val correctOption: ChoiceOption get() = options.first { it.code == targetCode }

    fun option(code: String): ChoiceOption? = options.find { it.code == code }
}

/** Builds the question for one quiz item. Pure: the same [random] seed gives the same question. */
fun interface ChoiceQuestionGenerator {
    /**
     * @param target the quiz item being asked about (a member of [quizSet]).
     * @param quizSet every item in this quiz.
     * @param allCountries every country in the game, for distractors.
     * @param category the quiz's category, for the prompt text.
     * @return a question with [CHOICE_OPTION_COUNT] options when the data allows, never fewer
     *   than 2 unless only one country exists at all.
     */
    fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion
}

/**
 * The randomness behind Easy quizzes (question order, distractors, option order). Production
 * uses [Random.Default]; tests construct it with a seeded [Random].
 */
class QuizRandom(val random: Random) {
    @Inject constructor() : this(Random.Default)
}

/**
 * Picks up to [count] countries from [pool] (excluding [target]), those in the target's region
 * first, each group in random order.
 */
internal fun pickSameRegionFirst(
    target: Country,
    pool: List<Country>,
    count: Int,
    random: Random
): List<Country> {
    val candidates = pool.filter { it.code != target.code }.distinctBy { it.code }
    val (sameRegion, otherRegions) = candidates.partition { it.region == target.region }
    return (sameRegion.shuffled(random) + otherRegions.shuffled(random)).take(count)
}

/** The target's option plus [distractors] as options labelled by [label], shuffled. */
internal fun buildQuestion(
    target: Country,
    distractors: List<Country>,
    prompt: ChoicePrompt,
    random: Random,
    label: (Country) -> String
): ChoiceQuestion {
    val options = (listOf(target) + distractors)
        .map { ChoiceOption(it.code, label(it)) }
        .distinctBy { it.code }
        .shuffled(random)
    return ChoiceQuestion(targetCode = target.code, options = options, prompt = prompt)
}

private const val DISTRACTOR_COUNT = CHOICE_OPTION_COUNT - 1

/**
 * Countries Easy, "spot the member": the target plus three countries not in the set, from the
 * target's region where possible. When fewer than three outsiders exist (All countries, or a
 * near-complete set), shows the target's flag instead, with other set members as distractors
 * (topped up with outsiders if the set is tiny). A practice set ([QuizCategory.Practice], 3.5c)
 * always uses the flag prompt: "which of these did you miss" would test memory, not geography.
 */
object CountriesChoiceGenerator : ChoiceQuestionGenerator {
    override fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion {
        val setCodes = quizSet.mapTo(HashSet()) { it.code }
        val outsiders = allCountries.filter { it.code !in setCodes }.distinctBy { it.code }
        if (category !is QuizCategory.Practice && outsiders.size >= DISTRACTOR_COUNT) {
            val distractors = pickSameRegionFirst(target, outsiders, DISTRACTOR_COUNT, random)
            return buildQuestion(target, distractors, ChoicePrompt.InSet(category.displayName), random) { it.name }
        }
        val members = pickSameRegionFirst(target, quizSet, DISTRACTOR_COUNT, random)
        val topUp = pickSameRegionFirst(target, outsiders, DISTRACTOR_COUNT - members.size, random)
        return buildQuestion(target, members + topUp, ChoicePrompt.FlagOf(target.code), random) { it.name }
    }
}

/**
 * Capitals Easy: "What is the capital of X?" with the target's capital and three other capitals
 * (any country, the target's region first). Distractor capitals are non-blank, distinct from each
 * other and never the same text as the target's capital.
 */
object CapitalsChoiceGenerator : ChoiceQuestionGenerator {
    override fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion {
        val targetKey = capitalKey(target.capital)
        val pool = allCountries
            .filter { it.code != target.code && it.capital.isNotBlank() && capitalKey(it.capital) != targetKey }
        // Shuffle before de-duplicating, so a shared capital is not always shown for the same country.
        val (sameRegion, otherRegions) = pool.shuffled(random).partition { it.region == target.region }
        val distractors = (sameRegion + otherRegions)
            .distinctBy { capitalKey(it.capital) }
            .take(DISTRACTOR_COUNT)
        return buildQuestion(target, distractors, ChoicePrompt.CapitalOf(target.name), random) { it.capital.trim() }
    }

    private fun capitalKey(capital: String): String = capital.trim().lowercase()
}

/**
 * Flags Easy: show the target's flag; the options are its name and three other country names
 * (any country, the target's region first).
 */
object FlagsChoiceGenerator : ChoiceQuestionGenerator {
    override fun question(
        target: Country,
        quizSet: List<Country>,
        allCountries: List<Country>,
        category: QuizCategory,
        random: Random
    ): ChoiceQuestion {
        val distractors = pickSameRegionFirst(target, allCountries, DISTRACTOR_COUNT, random)
        return buildQuestion(target, distractors, ChoicePrompt.FlagOf(target.code), random) { it.name }
    }
}
