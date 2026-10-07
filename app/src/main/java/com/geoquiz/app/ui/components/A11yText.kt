package com.geoquiz.app.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.model.QuizMode

/**
 * Screen reader descriptions shared by the quiz, results and answer review screens.
 * Plain functions over [Resources] so they can be unit-tested; composables call them with
 * [a11yResources].
 */
object A11yText {

    /** "3 minutes 12 seconds", "45 seconds", "2 minutes". Negative input counts as 0. */
    fun duration(res: Resources, totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val minutes = safe / 60
        val seconds = safe % 60
        val minutesText = res.getQuantityString(R.plurals.a11y_minutes, minutes, minutes)
        val secondsText = res.getQuantityString(R.plurals.a11y_seconds, seconds, seconds)
        return when {
            minutes == 0 -> secondsText
            seconds == 0 -> minutesText
            else -> res.getString(R.string.a11y_duration_minutes_seconds, minutesText, secondsText)
        }
    }

    /** "Time 3 minutes 12 seconds". */
    fun timer(res: Resources, totalSeconds: Int): String =
        res.getString(R.string.a11y_timer, duration(res, totalSeconds))

    /** "12 of 197 countries named" (capitals named, flags identified). */
    fun progress(res: Resources, mode: QuizMode, answered: Int, total: Int): String =
        progress(res, mode.spec, answered, total)

    /** Same as above for any registered mode, using its own plural. */
    fun progress(res: Resources, mode: GameModeSpec, answered: Int, total: Int): String =
        res.getQuantityString(mode.labels.a11yProgress, total, answered, total)

    /** Hard mode: "1 of 3 incorrect guesses used". */
    fun strikes(res: Resources, used: Int, allowed: Int): String =
        res.getQuantityString(R.plurals.a11y_hard_mode_strikes, allowed, used, allowed)

    /** "2 incorrect guesses". */
    fun incorrectGuesses(res: Resources, count: Int): String =
        res.getQuantityString(R.plurals.a11y_incorrect_guesses, count, count)

    /**
     * One quiz list row, read as a single item. Never reveals an unanswered answer:
     * "France, answered", "Row 12, not yet answered",
     * capitals with the country hint: "Paris, capital of France, answered" / "Capital of France, not yet answered".
     *
     * @param rowNumber 1-based position in the list as shown.
     */
    fun quizRow(
        res: Resources,
        mode: QuizMode,
        country: Country,
        rowNumber: Int,
        isAnswered: Boolean,
        showCountryHint: Boolean
    ): String {
        val capitalHint = mode == QuizMode.CAPITALS && showCountryHint
        return if (isAnswered) {
            val label = when {
                mode != QuizMode.CAPITALS -> country.name
                capitalHint -> res.getString(R.string.a11y_capital_of, country.capital, country.name)
                else -> country.capital
            }
            res.getString(R.string.a11y_status_answered, label)
        } else {
            val label = if (capitalHint) {
                res.getString(R.string.a11y_capital_of_hint, country.name)
            } else {
                res.getString(R.string.a11y_row_number, rowNumber)
            }
            res.getString(R.string.a11y_status_not_answered, label)
        }
    }

    /** One answer review row: "Brazil, missed", "Brasília, capital of Brazil, answered". */
    fun reviewRow(res: Resources, mode: QuizMode, country: Country, isAnswered: Boolean): String {
        val label = if (mode == QuizMode.CAPITALS) {
            res.getString(R.string.a11y_capital_of, country.capital, country.name)
        } else {
            country.name
        }
        val status = if (isAnswered) R.string.a11y_status_answered else R.string.a11y_status_missed
        return res.getString(status, label)
    }

    /** Size of a quiz option: "54 countries" or, in capitals mode, "54 capitals". */
    fun quizOptionCount(res: Resources, mode: QuizMode, count: Int): String {
        val id = if (mode == QuizMode.CAPITALS) {
            R.plurals.a11y_quiz_option_capitals
        } else {
            R.plurals.a11y_quiz_option_countries
        }
        return res.getQuantityString(id, count, count)
    }
}

/** Resources for [A11yText] inside a composable. */
@Composable
@ReadOnlyComposable
fun a11yResources(): Resources = LocalContext.current.resources

/**
 * For a Material card with `onClick` (which already merges its children into one focusable
 * item): announces it as a button and gives TalkBack an action label, e.g. "Double-tap to
 * start quiz". [onClick] must be the card's own click handler, because this replaces the
 * accessibility click action of the card.
 */
fun Modifier.buttonSemantics(onClickLabel: String, onClick: () -> Unit): Modifier =
    semantics {
        role = Role.Button
        onClick(label = onClickLabel) {
            onClick()
            true
        }
    }
