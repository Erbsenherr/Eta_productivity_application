package com.example.erik_iteration_2.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.ui.theme.ErikTheme

/** How much a button shrinks while held. Small enough to feel physical, not bouncy. */
private const val PRESSED_SCALE = 0.97f

/**
 * Primary action.
 *
 * Press feedback is a scale-and-fade rather than a ripple: ripple lives in the
 * Material artifacts this project deliberately does without, and a subtle squeeze
 * suits the card-and-drag feel of the planner better anyway.
 */
@Composable
fun ErikButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ErikButtonStyle = ErikButtonStyle.Primary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        label = "buttonScale",
    )

    val shape = ErikTheme.shapes.medium
    val background = when (style) {
        ErikButtonStyle.Primary -> ErikTheme.colors.accent
        ErikButtonStyle.Secondary -> ErikTheme.colors.surface
    }
    val contentColor = when (style) {
        ErikButtonStyle.Primary -> ErikTheme.colors.onAccent
        ErikButtonStyle.Secondary -> ErikTheme.colors.textPrimary
    }

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .background(color = background, shape = shape)
            .then(
                if (style == ErikButtonStyle.Secondary) {
                    Modifier.border(1.dp, ErikTheme.colors.border, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = 20.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        ErikText(
            text = text,
            style = ErikTheme.typography.label,
            color = contentColor,
        )
    }
}

enum class ErikButtonStyle { Primary, Secondary }
