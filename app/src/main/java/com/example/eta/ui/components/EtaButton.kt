package com.example.eta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
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

/** How long [EtaHoldButton] takes to fill, and how quickly it falls back. */
private const val HOLD_MILLIS = 900
private const val HOLD_RELEASE_MILLIS = 180

/**
 * How far the bar may have got for a release to still count as a tap. Past it
 * the finger was clearly holding, and letting go early means "neither".
 */
private const val HOLD_TAP_FRACTION = 0.45f

/**
 * A secondary button with a second meaning behind a hold.
 *
 * A tap is [onClick]. Holding it down fills the button from left to right, and
 * once the fill has covered it [onHold] fires instead. Letting go part-way does
 * **nothing**: someone who started to hold and thought better of it has not
 * asked for the tap either.
 */
@Composable
fun EtaHoldButton(
    text: String,
    onClick: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
    fillColor: Color = EtaTheme.colors.warning,
) {
    var holding by remember { mutableStateOf(false) }
    var fired by remember { mutableStateOf(false) }
    val fill = remember { Animatable(0f) }
    // The gesture block is launched once and keeps what it closed over, so the
    // callbacks are read through these.
    val latestClick by rememberUpdatedState(onClick)
    val latestHold by rememberUpdatedState(onHold)

    LaunchedEffect(holding) {
        if (!holding) {
            fill.animateTo(0f, tween(HOLD_RELEASE_MILLIS))
            return@LaunchedEffect
        }
        fill.animateTo(1f, tween(HOLD_MILLIS, easing = LinearEasing))
        fired = true
        latestHold()
    }

    val shape = EtaTheme.shapes.medium
    Box(
        modifier = modifier
            .clip(shape)
            .background(color = EtaTheme.colors.surface, shape = shape)
            .drawBehind {
                if (fill.value > 0f) {
                    drawRect(
                        color = fillColor.copy(alpha = 0.45f),
                        size = size.copy(width = size.width * fill.value),
                    )
                }
            }
            .border(1.dp, EtaTheme.colors.border, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        fired = false
                        holding = true
                        val released = tryAwaitRelease()
                        val tapped = released && !fired && fill.value < HOLD_TAP_FRACTION
                        holding = false
                        if (tapped) latestClick()
                    },
                )
            }
            .padding(PaddingValues(horizontal = 20.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        EtaText(
            text = text,
            style = EtaTheme.typography.label,
            color = EtaTheme.colors.textPrimary,
        )
    }
}

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
