package com.geoquiz.app.ui.quiz.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.geoquiz.app.domain.model.AnswerResult
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
            label = { Text("Enter a country name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = lastResult is AnswerResult.Incorrect
        )

        Spacer(modifier = Modifier.height(4.dp))

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
            is AnswerResult.None -> { /* no feedback */ }
        }
    }
}
