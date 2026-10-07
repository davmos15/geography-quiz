package com.geoquiz.app.ui.quiz.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.theme.AlreadyAnsweredAmber
import com.geoquiz.app.ui.theme.CorrectGreen
import com.geoquiz.app.ui.theme.IncorrectRed

@Composable
fun AnswerInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    lastResult: AnswerResult,
    enabled: Boolean,
    quizMode: QuizMode = QuizMode.COUNTRIES,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            enabled = enabled,
            label = {
                Text(
                    when (quizMode) {
                        QuizMode.CAPITALS -> "Enter a capital city"
                        QuizMode.FLAGS -> "Enter a country name"
                        QuizMode.COUNTRIES -> "Enter a country name"
                    }
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = lastResult is AnswerResult.Incorrect
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Polite live region so TalkBack announces the result of each submit.
        Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (lastResult) {
            is AnswerResult.Correct -> {
                Text(
                    text = "${lastResult.countryName} - Correct!",
                    color = CorrectGreen,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is AnswerResult.AlreadyAnswered -> {
                Text(
                    text = "Already answered!",
                    color = AlreadyAnsweredAmber,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is AnswerResult.Incorrect -> {
                Text(
                    text = "Not recognized. Try again!",
                    color = IncorrectRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is AnswerResult.NearMiss -> {
                // Icon + text, not colour alone. Not a strike, so not styled as an error.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Spellcheck,
                        contentDescription = null, // the text says it
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.answer_near_miss),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is AnswerResult.None -> { /* no feedback */ }
        }
        }
    }
}
