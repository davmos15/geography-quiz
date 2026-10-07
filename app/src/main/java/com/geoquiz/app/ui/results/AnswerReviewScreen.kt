package com.geoquiz.app.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.R
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.components.FlagImage
import com.geoquiz.app.ui.theme.CorrectGreen
import com.geoquiz.app.ui.theme.IncorrectRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerReviewScreen(
    onNavigateBack: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: AnswerReviewViewModel = hiltViewModel()
) {
    val showFlags by viewModel.showFlags.collectAsStateWithLifecycle()
    when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
        AnswerReviewUiState.Loading -> ResultLoading()
        AnswerReviewUiState.Missing -> ResultMissing(onGoHome = onGoHome)
        is AnswerReviewUiState.Loaded -> AnswerReviewContent(
            state = state,
            showFlags = showFlags,
            onNavigateBack = onNavigateBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnswerReviewContent(
    state: AnswerReviewUiState.Loaded,
    showFlags: Boolean,
    onNavigateBack: () -> Unit
) {
    val sorted = state.countries
    val answeredCodes = state.answeredCodes
    val categoryName = state.categoryName
    val quizMode = state.quizMode
    val incorrectGuesses = state.incorrectGuesses
    val category = state.category

    val answeredCount = state.answeredCount
    val hasIncorrectGuesses = incorrectGuesses.isNotEmpty()
    // Saveable, so the selected tab survives rotation and process death.
    var showIncorrectGuesses by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Answers - $categoryName") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    text = "$answeredCount / ${sorted.size} answered",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (hasIncorrectGuesses) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        FilterChip(
                            selected = !showIncorrectGuesses,
                            onClick = { showIncorrectGuesses = false },
                            label = { Text("All Answers") }
                        )
                        FilterChip(
                            selected = showIncorrectGuesses,
                            onClick = { showIncorrectGuesses = true },
                            label = { Text("Incorrect Guesses (${incorrectGuesses.size})") }
                        )
                    }
                }
            }

            if (showIncorrectGuesses) {
                items(incorrectGuesses) { incorrect ->
                    val guess = incorrect.guess
                    val matchedCountry = incorrect.matchedCountry
                    val hint = if (matchedCountry != null) {
                        getContextHint(matchedCountry, category, quizMode)
                    } else null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Incorrect",
                            tint = IncorrectRed
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = guess,
                                style = MaterialTheme.typography.bodyLarge,
                                color = IncorrectRed
                            )
                            if (hint != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            } else {
                items(sorted, key = { it.code }) { country ->
                    val isAnswered = country.code in answeredCodes

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAnswered) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isAnswered) "Answered" else "Missed",
                            tint = if (isAnswered) CorrectGreen else IncorrectRed
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        if (showFlags) {
                            FlagImage(
                                countryCode = country.code,
                                contentDescription = stringResource(
                                    R.string.flag_content_description,
                                    country.name
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        when (quizMode) {
                            QuizMode.CAPITALS -> {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = country.capital,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isAnswered) CorrectGreen else IncorrectRed
                                    )
                                    Text(
                                        text = country.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            QuizMode.FLAGS, QuizMode.COUNTRIES -> {
                                Text(
                                    text = country.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isAnswered) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isAnswered) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        IncorrectRed
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

private fun getContextHint(country: Country, category: QuizCategory, quizMode: QuizMode): String? {
    return when (category) {
        is QuizCategory.StartingWithLetter -> {
            val actual = if (quizMode == QuizMode.CAPITALS) country.capital else country.name
            "Starts with '${actual.first().uppercaseChar()}'"
        }
        is QuizCategory.EndingWithLetter -> {
            val actual = if (quizMode == QuizMode.CAPITALS) country.capital else country.name
            "Ends with '${actual.last().uppercaseChar()}'"
        }
        is QuizCategory.ByRegion -> "In ${country.region}"
        is QuizCategory.BySubregion -> "In ${country.subregion}"
        is QuizCategory.ByNameLengthRange -> {
            val actual = if (quizMode == QuizMode.CAPITALS) country.capital else country.name
            "${actual.length} letters"
        }
        is QuizCategory.ContainingLetter -> {
            val actual = if (quizMode == QuizMode.CAPITALS) country.capital else country.name
            if (!actual.contains(category.letter, ignoreCase = true)) {
                "Doesn't contain '${category.letter.uppercaseChar()}'"
            } else null
        }
        is QuizCategory.AllCountries -> "In ${country.region}"
        is QuizCategory.UniqueLetters -> {
            val name = country.name.lowercase().replace(Regex("[^a-z]"), "")
            val repeats = name.groupBy { it }.filter { it.value.size > 1 }.keys
            if (repeats.isNotEmpty()) {
                "'${repeats.joinToString("', '") { it.uppercaseChar().toString() }}' repeats"
            } else null
        }
        is QuizCategory.CardinalDirection -> {
            val hasDirection = Regex("\\b(North|South|East|West)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(country.name)
            if (!hasDirection) "No cardinal direction in name" else null
        }
        is QuizCategory.EndingInVowel -> {
            val actual = if (quizMode == QuizMode.CAPITALS) country.capital else country.name
            val lastChar = actual.last().lowercaseChar()
            if (lastChar !in setOf('a', 'e', 'i', 'o', 'u')) "Ends with '${lastChar.uppercaseChar()}'" else null
        }
        is QuizCategory.SingleVowelType -> {
            val actual = if (quizMode == QuizMode.CAPITALS) country.capital else country.name
            val dv = actual.lowercase().filter { it in setOf('a', 'e', 'i', 'o', 'u') }.toSet()
            if (dv.size != 1) "${dv.size} vowel types (${dv.sorted().joinToString(", ")})" else null
        }
        else -> null
    }
}
