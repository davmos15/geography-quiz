package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.data.flagspeed.FlagLookalikeRepository
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import javax.inject.Inject

/**
 * Thin [QuestionGenerator] wrapper (task 7.3, D52). This mode's round is not built from a fixed
 * category list - [category] is ignored, since Flag speed's own setup screen offers a difficulty
 * only, no region or category filter - it is an open-ended 60-second draw over the lookalike data
 * file, done live by [FlagSpeedRoundDrawer] in
 * [com.geoquiz.app.ui.flagspeed.FlagSpeedGameViewModel]. [items] still returns something
 * meaningful if ever called by shared code written for the classic shape (e.g.
 * [com.geoquiz.app.domain.challenge.AcceptIncomingChallengeUseCase]'s "does this category have
 * any items" check, never actually reached for this mode because it issues no challenge links,
 * D52): every country that appears in a non-near-identical lookalike set, this mode's whole
 * playable pool.
 */
class FlagSpeedQuestionGenerator @Inject constructor(
    private val lookalikeRepository: FlagLookalikeRepository,
    private val countryRepository: CountryRepository
) : QuestionGenerator {
    override suspend fun items(category: QuizCategory): List<Country> {
        val codes = lookalikeRepository.sets()
            .filter { it.visibility != FlagLookalikeVisibility.NEAR_IDENTICAL }
            .flatMap { it.countries }
            .distinct()
        return countryRepository.getCountriesByCodes(codes)
    }
}
