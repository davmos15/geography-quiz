package com.geoquiz.app.domain.mode.dailychallenge

import com.geoquiz.app.domain.mode.CapitalsChoiceGenerator
import com.geoquiz.app.domain.mode.FlagsChoiceGenerator
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import kotlin.random.Random

/**
 * Builds one day's 10-question daily challenge round (task 8.1, D4), deterministically from its
 * local calendar date: the same [date] string always produces the same 10 questions, in the same
 * order, with the same multiple-choice options, on any device or JVM, forever (same spirit as
 * [com.geoquiz.app.domain.usecase.NormalizeInputUseCase]'s stability guarantee, but for
 * [kotlin.random.Random] draws instead of string normalisation).
 *
 * Seed derivation ([seedFor]): [String.hashCode] is specified by the Java/Kotlin standard library
 * to always compute `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]` using 32-bit int arithmetic
 * (wrapping on overflow), so it is stable across Kotlin/JVM/Android versions - not an
 * implementation detail that could change later. Two different "yyyy-MM-dd" strings give the same
 * seed only on an astronomically rare hash collision, which is why "different dates (almost
 * always) give a different mix" (task 8.1 brief) rather than a hard guarantee.
 *
 * Question mix (a fixed, documented distribution, not required to be exactly 2.5 of each kind):
 * 3 [DailyChallengeQuestionKind.TYPED_COUNTRY], 3 [DailyChallengeQuestionKind.TYPED_CAPITAL],
 * 2 [DailyChallengeQuestionKind.CHOICE_FLAG], 2 [DailyChallengeQuestionKind.CHOICE_CAPITAL] - typed
 * questions are slightly favoured since Normal is the challenge's only tier (D4/task 8.1: it has
 * no difficulty picker) and typed answers feel more like "the real quiz" than always being handed
 * four options.
 *
 * No duplicate target countries within one day's 10: targets are drawn from [allCountries]
 * de-duplicated by code and shuffled once with the seeded [Random], then the fixed kind
 * distribution (independently shuffled with the same [Random] sequence) is laid over the top in
 * order. [CapitalsChoiceGenerator]/[FlagsChoiceGenerator] (the same ready-made Easy-tier
 * generators every classic mode uses) build the two choice kinds' options, continuing to draw
 * from the same [Random] - so the whole round, start to finish, is one deterministic sequence of
 * draws from one seed. Their `quizSet` parameter is ignored by both generators (distractors come
 * from [allCountries] and the target's region only), so an empty list is passed for it.
 */
object DailyChallengeGenerator {
    /** How many questions one day's round has, when there is enough data to fill it. */
    const val QUESTION_COUNT = 10

    private val KIND_DISTRIBUTION: List<DailyChallengeQuestionKind> = buildList {
        repeat(3) { add(DailyChallengeQuestionKind.TYPED_COUNTRY) }
        repeat(3) { add(DailyChallengeQuestionKind.TYPED_CAPITAL) }
        repeat(2) { add(DailyChallengeQuestionKind.CHOICE_FLAG) }
        repeat(2) { add(DailyChallengeQuestionKind.CHOICE_CAPITAL) }
    }

    /**
     * The seed [generate] uses for [date]. See the class doc for why this is stable forever.
     */
    fun seedFor(date: String): Long = date.hashCode().toLong()

    /**
     * [QUESTION_COUNT] questions for [date] (fewer only if [allCountries] has fewer distinct
     * countries than that - never expected with the app's real 197-country data), deterministic
     * in [date] and [allCountries] (their order and content must be stable between calls for two
     * calls to agree - true of [com.geoquiz.app.domain.repository.CountryRepository.getAllCountries]
     * in practice, since it is a stable Room query over data that never changes at runtime).
     */
    fun generate(date: String, allCountries: List<Country>): List<DailyChallengeQuestion> {
        val pool = allCountries.distinctBy { it.code }
        if (pool.isEmpty()) return emptyList()

        val random = Random(seedFor(date))
        val targets = pool.shuffled(random).take(QUESTION_COUNT)
        val kinds = KIND_DISTRIBUTION.shuffled(random).take(targets.size)

        return targets.zip(kinds) { target, kind ->
            DailyChallengeQuestion(
                target = target,
                kind = kind,
                choice = buildChoiceOrNull(kind, target, allCountries, date, random)
            )
        }
    }

    private fun buildChoiceOrNull(
        kind: DailyChallengeQuestionKind,
        target: Country,
        allCountries: List<Country>,
        date: String,
        random: Random
    ) = when (kind) {
        DailyChallengeQuestionKind.CHOICE_FLAG -> FlagsChoiceGenerator.question(
            target = target,
            quizSet = emptyList(),
            allCountries = allCountries,
            category = QuizCategory.DailyChallenge(date),
            random = random
        )
        DailyChallengeQuestionKind.CHOICE_CAPITAL -> CapitalsChoiceGenerator.question(
            target = target,
            quizSet = emptyList(),
            allCountries = allCountries,
            category = QuizCategory.DailyChallenge(date),
            random = random
        )
        DailyChallengeQuestionKind.TYPED_COUNTRY, DailyChallengeQuestionKind.TYPED_CAPITAL -> null
    }
}
