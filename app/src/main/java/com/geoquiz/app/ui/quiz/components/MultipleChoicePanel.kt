package com.geoquiz.app.ui.quiz.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.ui.components.FlagImage
import com.geoquiz.app.ui.theme.geoColors

/** Height of the flag in a flag prompt: 160 dp wide at the 4:3 flag shape. */
private val PROMPT_FLAG_HEIGHT = 120.dp

/** How an option looks: before a pick, or while a pick's result is shown. */
private enum class OptionLook { NEUTRAL, CORRECT, WRONG, DIMMED }

/**
 * The Easy tier's question: a prompt card (text, plus the flag for a flag prompt) and one
 * full-width button per option. After a pick ([feedback] set) the correct option turns blue with
 * a tick, a wrongly picked option orange with a cross, the rest are dimmed, and nothing can be
 * picked until the next question. A polite live region announces the result.
 */
@Composable
fun MultipleChoicePanel(
    question: ChoiceQuestion,
    feedback: ChoiceFeedback?,
    enabled: Boolean,
    onSelect: (code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PromptCard(question.prompt)
        question.options.forEach { option ->
            val look = when {
                feedback == null -> OptionLook.NEUTRAL
                option.code == question.targetCode -> OptionLook.CORRECT
                option.code == feedback.selectedCode -> OptionLook.WRONG
                else -> OptionLook.DIMMED
            }
            OptionButton(
                option = option,
                look = look,
                enabled = enabled && feedback == null,
                onClick = { onSelect(option.code) }
            )
        }
        // Polite live region: says the result of each pick.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            when {
                feedback == null -> Unit
                feedback.isCorrect -> FeedbackLine(
                    icon = Icons.Filled.Check,
                    text = stringResource(R.string.choice_feedback_correct),
                    color = MaterialTheme.geoColors.correct
                )
                else -> FeedbackLine(
                    icon = Icons.Filled.Close,
                    text = stringResource(R.string.choice_feedback_incorrect, question.correctOption.label),
                    color = MaterialTheme.geoColors.wrong
                )
            }
        }
    }
}

@Composable
private fun PromptCard(prompt: ChoicePrompt) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (prompt is ChoicePrompt.FlagOf) {
                // Never name the country here: it is the answer.
                FlagImage(
                    countryCode = prompt.code,
                    contentDescription = stringResource(R.string.choice_flag_description),
                    height = PROMPT_FLAG_HEIGHT
                )
            }
            val text = when (prompt) {
                is ChoicePrompt.InSet -> stringResource(R.string.choice_prompt_in_set, prompt.categoryName)
                is ChoicePrompt.CapitalOf -> stringResource(R.string.choice_prompt_capital_of, prompt.countryName)
                is ChoicePrompt.FlagOf -> stringResource(R.string.choice_prompt_flag)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
        }
    }
}

@Composable
private fun OptionButton(
    option: ChoiceOption,
    look: OptionLook,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val geo = MaterialTheme.geoColors
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    val border: BorderStroke
    when (look) {
        OptionLook.NEUTRAL -> {
            container = Color.Transparent
            content = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f)
            border = BorderStroke(1.dp, scheme.outline)
        }
        OptionLook.CORRECT -> {
            container = geo.correctContainer
            content = geo.onCorrectContainer
            border = BorderStroke(2.dp, geo.correct)
        }
        OptionLook.WRONG -> {
            container = geo.wrongContainer
            content = geo.onWrongContainer
            border = BorderStroke(2.dp, geo.wrong)
        }
        OptionLook.DIMMED -> {
            container = Color.Transparent
            content = scheme.onSurface.copy(alpha = 0.38f)
            border = BorderStroke(1.dp, scheme.outlineVariant)
        }
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        border = border,
        // The same colours enabled or not: during feedback the buttons are disabled but must
        // still show the result.
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            when (look) {
                OptionLook.CORRECT -> ResultIcon(
                    icon = Icons.Filled.Check,
                    description = stringResource(R.string.choice_option_correct),
                    tint = geo.correct
                )
                OptionLook.WRONG -> ResultIcon(
                    icon = Icons.Filled.Close,
                    description = stringResource(R.string.choice_option_wrong),
                    tint = geo.wrong
                )
                OptionLook.NEUTRAL, OptionLook.DIMMED -> Unit
            }
        }
    }
}

@Composable
private fun ResultIcon(icon: ImageVector, description: String, tint: Color) {
    Spacer(modifier = Modifier.width(8.dp))
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(24.dp)
    )
}

/** Icon + text, never colour alone. The icon is decorative: the text says it. */
@Composable
private fun FeedbackLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
