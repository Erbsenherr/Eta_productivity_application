package com.example.erik_iteration_2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.ui.theme.ErikTheme

/**
 * A single − / + key.
 *
 * Its own component because the questionnaire and the dashboard both nudge values
 * this way, and a shared one keeps the hit area and the press feel identical.
 */
@Composable
fun ErikStepperButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .background(ErikTheme.colors.accentSoft, ErikTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ErikText(
            text = label,
            style = ErikTheme.typography.label,
            color = ErikTheme.colors.accent,
        )
    }
}

/** − value + , with the value wide enough that stepping does not shuffle the row. */
@Composable
fun ErikStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    valueWidth: Dp = 56.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
    ) {
        ErikStepperButton(label = "−", onClick = onDecrement)
        ErikText(
            text = value,
            style = ErikTheme.typography.bodyStrong,
            modifier = Modifier.widthIn(min = valueWidth),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        ErikStepperButton(label = "+", onClick = onIncrement)
    }
}
