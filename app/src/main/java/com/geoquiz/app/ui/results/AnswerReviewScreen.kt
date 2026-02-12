package com.geoquiz.app.ui.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geoquiz.app.data.local.preferences.settingsDataStore
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.ui.theme.CorrectGreen
import com.geoquiz.app.ui.theme.IncorrectRed
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnswerReviewScreen(
    onNavigateBack: () -> Unit
) {
    val countries = QuizResultHolder.countries
    val answeredCodes = QuizResultHolder.answeredCodes
    val categoryName = QuizResultHolder.categoryName
    val quizMode = QuizResultHolder.quizMode
    val sorted = if (quizMode == QuizMode.CAPITALS) {
        countries.sortedBy { it.capital }
    } else {
        countries.sortedBy { it.name }
    }

    val context = LocalContext.current
    val showFlagsFlow = remember {
        context.settingsDataStore.data.map {
            it[booleanPreferencesKey("show_flags")] ?: false
        }
    }
    val showFlags by showFlagsFlow.collectAsStateWithLifecycle(initialValue = false)

    val answeredCount = sorted.count { it.code in answeredCodes }

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
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

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
                        Text(
                            text = country.flag,
                            style = MaterialTheme.typography.titleLarge
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
