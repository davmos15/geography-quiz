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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.theme.geoColors

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
    val geoColors = MaterialTheme.geoColors

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
                Text(stringResource(quizMode.spec.labels.inputLabel))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = lastResult is AnswerResult.Incorrect,
            // A wrong answer is orange, not the theme's red error colour.
            colors = OutlinedTextFieldDefaults.colors(
                errorBorderColor = geoColors.wrong,
                errorLabelColor = geoColors.wrong,
                errorCursorColor = geoColors.wrong,
                errorLeadingIconColor = geoColors.wrong,
                errorTrailingIconColor = geoColors.wrong,
                errorSupportingTextColor = geoColors.wrong,
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Polite live region so TalkBack announces the result of each submit.
        Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (lastResult) {
            is AnswerResult.Correct -> FeedbackLine(
                icon = Icons.Filled.Check,
                text = "${lastResult.countryName} - Correct!",
                color = geoColors.correct
            )
            // Neutral, not a warning: repeating an answer costs nothing.
            is AnswerResult.AlreadyAnswered -> FeedbackLine(
                icon = Icons.Outlined.Info,
                text = "Already answered!",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is AnswerResult.Incorrect -> FeedbackLine(
                icon = Icons.Filled.Close,
                text = "Not recognized. Try again!",
                color = geoColors.wrong
            )
            // Not a strike, so neither the correct nor the wrong colour.
            is AnswerResult.NearMiss -> FeedbackLine(
                icon = Icons.Outlined.Spellcheck,
                text = stringResource(R.string.answer_near_miss),
                color = geoColors.nearMiss
            )
            is AnswerResult.None -> { /* no feedback */ }
        }
        }
    }
}

/** Icon + text, never colour alone. The icon is decorative: the text says it. */
@Composable
private fun FeedbackLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
