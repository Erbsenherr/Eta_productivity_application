package com.example.erik_iteration_2.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.ui.theme.ErikTheme

/**
 * Checkbox drawn by hand — Foundation ships none, and the check needs to animate
 * in rather than pop, since ticking things off is the app's core gesture.
 */
@Composable
fun ErikCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        label = "checkProgress",
    )
    val accent = ErikTheme.colors.accent
    val border = ErikTheme.colors.border
    val onAccent = ErikTheme.colors.onAccent
    val interactionSource = remember { MutableInteractionSource() }

    Canvas(
        modifier = modifier
            .size(22.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        val corner = size.minDimension * 0.28f

        if (progress < 1f) {
            drawRoundRect(
                color = border,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        if (progress > 0f) {
            drawRoundRect(
                color = accent.copy(alpha = progress),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
            )
            // Two strokes of the tick, drawn proportionally so it sweeps in.
            val start = Offset(size.width * 0.26f, size.height * 0.52f)
            val elbow = Offset(size.width * 0.44f, size.height * 0.70f)
            val end = Offset(size.width * 0.76f, size.height * 0.32f)
            val firstLeg = (progress / 0.4f).coerceIn(0f, 1f)
            val secondLeg = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)

            drawLine(
                color = onAccent,
                start = start,
                end = Offset(
                    start.x + (elbow.x - start.x) * firstLeg,
                    start.y + (elbow.y - start.y) * firstLeg,
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            if (secondLeg > 0f) {
                drawLine(
                    color = onAccent,
                    start = elbow,
                    end = Offset(
                        elbow.x + (end.x - elbow.x) * secondLeg,
                        elbow.y + (end.y - elbow.y) * secondLeg,
                    ),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
