package com.example.eta.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.eta.ui.theme.EtaTheme

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
fun EtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: EtaButtonStyle = EtaButtonStyle.Primary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        label = "buttonScale",
    )

    val shape = EtaTheme.shapes.medium
    val background = when (style) {
        EtaButtonStyle.Primary -> EtaTheme.colors.accent
        EtaButtonStyle.Secondary -> EtaTheme.colors.surface
    }
    val contentColor = when (style) {
        EtaButtonStyle.Primary -> EtaTheme.colors.onAccent
        EtaButtonStyle.Secondary -> EtaTheme.colors.textPrimary
    }

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.45f)
            .background(color = background, shape = shape)
            .then(
                if (style == EtaButtonStyle.Secondary) {
                    Modifier.border(1.dp, EtaTheme.colors.border, shape)
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
        EtaText(
            text = text,
            style = EtaTheme.typography.label,
            color = contentColor,
        )
    }
}

enum class EtaButtonStyle { Primary, Secondary }

/**
 * A wastebasket in the corner of a dialog.
 *
 * Deleting is not one of the two things a card is opened to do, so it does not
 * belong in the row of buttons that answer "what now" — it sits away from them,
 * as an icon, where a slip of the thumb cannot reach it. It is drawn rather than
 * imported: there is no Material dependency here, and a wastebasket is six
 * strokes.
 */
@Composable
fun EtaTrashButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Löschen",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        label = "trashScale",
    )
    val color = EtaTheme.colors.danger

    Box(
        modifier = modifier
            .scale(scale)
            .clip(EtaTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(6.dp)
            // `label`, not `contentDescription`: inside this lambda the bare name
            // resolves to the receiver's own property, whose getter throws.
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.09f
            fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = stroke) =
                drawLine(
                    color = color,
                    start = Offset(w * x1, h * y1),
                    end = Offset(w * x2, h * y2),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )

            line(0.38f, 0.13f, 0.62f, 0.13f)
            line(0.10f, 0.27f, 0.90f, 0.27f)
            line(0.22f, 0.31f, 0.28f, 0.88f)
            line(0.78f, 0.31f, 0.72f, 0.88f)
            line(0.28f, 0.88f, 0.72f, 0.88f)
            line(0.43f, 0.43f, 0.45f, 0.76f, stroke * 0.7f)
            line(0.57f, 0.43f, 0.55f, 0.76f, stroke * 0.7f)
        }
    }
}
