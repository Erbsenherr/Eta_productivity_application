package com.example.eta.ui.components

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
import com.example.eta.ui.theme.EtaTheme

/**
 * A single − / + key.
 *
 * Its own component because the questionnaire and the dashboard both nudge values
 * this way, and a shared one keeps the hit area and the press feel identical.
 */
@Composable
fun EtaStepperButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .background(EtaTheme.colors.accentSoft, EtaTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        EtaText(
            text = label,
            style = EtaTheme.typography.label,
            color = EtaTheme.colors.accent,
        )
    }
}

/**
 * − value + , with the value wide enough that stepping does not shuffle the row.
 *
 * [onValueClick] makes the number itself a target, for the cases where nudging
 * is the wrong gesture — twelve taps to say "twelve hours before" is not an
 * answer. Absent, the number is inert, which is what every existing caller wants.
 */
@Composable
fun EtaStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    valueWidth: Dp = 56.dp,
    onValueClick: (() -> Unit)? = null,
) {
    val valueInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        EtaStepperButton(label = "−", onClick = onDecrement)
        EtaText(
            text = value,
            style = EtaTheme.typography.bodyStrong,
            color = if (onValueClick != null) {
                EtaTheme.colors.accent
            } else {
                EtaTheme.colors.textPrimary
            },
            modifier = Modifier
                .widthIn(min = valueWidth)
                .then(
                    if (onValueClick != null) {
                        Modifier.clickable(
                            interactionSource = valueInteraction,
                            indication = null,
                            onClick = onValueClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        EtaStepperButton(label = "+", onClick = onIncrement)
    }
}
