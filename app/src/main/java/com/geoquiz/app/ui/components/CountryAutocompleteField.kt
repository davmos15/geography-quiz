package com.geoquiz.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.ui.theme.geoColors

/** One autocomplete row: [key] identifies it to the caller (e.g. a cca3 code), [label] is shown. */
data class AutocompleteSuggestion(val key: String, val label: String)

/**
 * A text field for a country name with a list of [suggestions] directly above it (task P10-C;
 * the field normally sits pinned at the bottom of the screen above the keyboard, so the list
 * opens upwards and never hides behind it). Tapping a suggestion calls [onSuggestionSelected]
 * (the caller submits it); the keyboard's Done calls [onSubmit] with whatever was typed.
 *
 * The list is inline, not a popup, so it never steals focus from the field and TalkBack reaches
 * it in reading order. Each row is a focusable button at least 48 dp tall whose name wraps at
 * large font sizes; the list scrolls when it is taller than [maxSuggestionsHeight]. A polite
 * live region says how many suggestions there are as the list changes. Theme colours only;
 * [isError] uses the "wrong" orange, not the theme's red (same as `AnswerInput`).
 *
 * Ranking is the caller's job (see [com.geoquiz.app.domain.usecase.CountryNameSuggester]).
 */
@Composable
fun CountryAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<AutocompleteSuggestion>,
    onSuggestionSelected: (key: String) -> Unit,
    onSubmit: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    fieldModifier: Modifier = Modifier,
    maxSuggestionsHeight: Dp = DEFAULT_MAX_SUGGESTIONS_HEIGHT,
    /**
     * The list's actual rendered height, for a caller that needs to know how much room it just
     * took (e.g. Border hop at a large font scale with the keyboard open, so the map and the
     * field/Submit below the list never lose the space they need, P10-R2 M2). Not called when the
     * list isn't shown.
     */
    onSuggestionsHeightChanged: (Dp) -> Unit = {}
) {
    val geo = MaterialTheme.geoColors
    val density = LocalDensity.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val countText = pluralStringResource(R.plurals.country_autocomplete_suggestions, suggestions.size, suggestions.size)
        // Announces the count whenever it changes while there is something to type into.
        Box(
            modifier = Modifier.clearAndSetSemantics {
                if (enabled && value.isNotBlank()) {
                    contentDescription = countText
                    liveRegion = LiveRegionMode.Polite
                }
            }
        )
        if (enabled && suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().onSizeChanged {
                    onSuggestionsHeightChanged(with(density) { it.height.toDp() })
                },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxSuggestionsHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    suggestions.forEachIndexed { index, suggestion ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SuggestionRow(suggestion = suggestion, onClick = { onSuggestionSelected(suggestion.key) })
                    }
                }
            }
        } else {
            // No stale height lingers for a caller computing room for the next time it reappears.
            LaunchedEffect(Unit) { onSuggestionsHeightChanged(0.dp) }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                errorBorderColor = geo.wrong,
                errorLabelColor = geo.wrong,
                errorCursorColor = geo.wrong,
                errorLeadingIconColor = geo.wrong,
                errorTrailingIconColor = geo.wrong,
                errorSupportingTextColor = geo.wrong,
            )
        )
    }
}

@Composable
private fun SuggestionRow(suggestion: AutocompleteSuggestion, onClick: () -> Unit) {
    Text(
        text = suggestion.label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                onClickLabel = stringResource(R.string.country_autocomplete_choose_action),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/** About three rows at 100% font; the list scrolls beyond it, so the map keeps its room. */
internal val DEFAULT_MAX_SUGGESTIONS_HEIGHT = 168.dp
