package com.geoquiz.app.ui.quiz.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geoquiz.app.domain.model.Country

@Composable
fun CountryList(
    countries: List<Country>,
    answeredCodes: Set<String>,
    modifier: Modifier = Modifier
) {
    val sorted = countries.sortedBy { it.name }

    LazyColumn(modifier = modifier) {
        items(sorted, key = { it.code }) { country ->
            val isAnswered = country.code in answeredCodes
            Text(
                text = if (isAnswered) country.name else "???",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isAnswered) FontWeight.Medium else FontWeight.Normal,
                color = if (isAnswered) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}
