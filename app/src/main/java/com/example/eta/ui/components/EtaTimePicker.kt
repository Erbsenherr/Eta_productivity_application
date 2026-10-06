package com.example.eta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.theme.EtaTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.datetime.LocalTime

/** The minute ring snaps to five, which is as fine as anything in this app plans. */
private const val MINUTE_STEP = 5

private const val TWO_PI = 2.0 * Math.PI

/** Which half of the time the dial is currently editing. */
private enum class DialMode { HOUR, MINUTE }

/**
 * A time, entered the way Android does it: tap the field, pick the hour on a
 * clock face, then the minute.
 *
 * Built from scratch on Foundation — Material's TimePicker lives in the artifacts
 * this project omits. The dial is one [Canvas]: numbers, hand and knob are all
 * drawn, which keeps the geometry and the hit testing in the same coordinates.
 */
@Composable
fun EtaTimePicker(
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = EtaTheme.shapes.small

    Box(
        modifier = modifier
            .background(EtaTheme.colors.accentSoft, shape)
            .border(1.dp, EtaTheme.colors.border, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { showDialog = true },
            )
            .padding(horizontal = EtaTheme.spacing.lg, vertical = EtaTheme.spacing.md),
    ) {
        EtaText(
            text = value.formatClock(),
            style = EtaTheme.typography.title,
            color = EtaTheme.colors.accent,
        )
    }

    if (showDialog) {
        EtaTimePickerDialog(
            initial = value,
            onDismiss = { showDialog = false },
            onConfirm = {
                onValueChange(it)
                showDialog = false
            },
        )
    }
}

/**
 * The dial on its own, for callers that already have something to tap.
 *
 * A list row cannot afford the field above — it would swamp the row it belongs
 * to — but it can afford a caption that opens this.
 */
@Composable
fun EtaTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    var time by remember { mutableStateOf(initial) }
    var mode by remember { mutableStateOf(DialMode.HOUR) }

    Dialog(onDismissRequest = onDismiss) {
        EtaSurface(
            modifier = Modifier.widthIn(max = 360.dp),
            color = EtaTheme.colors.surfaceRaised,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
                EtaText(
                    text = if (mode == DialMode.HOUR) "Stunde wählen" else "Minute wählen",
                    style = EtaTheme.typography.label,
                    color = EtaTheme.colors.textSecondary,
                )

                // The two halves double as the mode switch, so a mistyped hour can
                // be corrected without starting over.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DialPart(
                        text = time.hour.toString().padStart(2, '0'),
                        active = mode == DialMode.HOUR,
                        onClick = { mode = DialMode.HOUR },
                    )
                    EtaText(text = ":", style = EtaTheme.typography.display)
                    DialPart(
                        text = time.minute.toString().padStart(2, '0'),
                        active = mode == DialMode.MINUTE,
                        onClick = { mode = DialMode.MINUTE },
                    )
                }

                ClockDial(
                    time = time,
                    mode = mode,
                    onPick = { picked, finished ->
                        time = picked
                        // Moving on to the minutes by itself is what makes this
                        // two taps rather than four.
                        if (finished && mode == DialMode.HOUR) mode = DialMode.MINUTE
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
                ) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    EtaButton(text = "OK", onClick = { onConfirm(time) })
                }
            }
        }
    }
}

@Composable
private fun DialPart(text: String, active: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = EtaTheme.spacing.sm, vertical = EtaTheme.spacing.xs),
    ) {
        EtaText(
            text = text,
            style = EtaTheme.typography.display,
            color = if (active) EtaTheme.colors.accent else EtaTheme.colors.textMuted,
        )
    }
}

/** Where a value sits on its ring, counting clockwise from twelve o'clock. */
private fun pointOnRing(center: Offset, position: Int, positions: Int, radius: Float): Offset {
    val angle = (position.toDouble() / positions - 0.25) * TWO_PI
    return center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
}

/**
 * The clock face.
 *
 * In hour mode it carries two rings, as a 24-hour dial does: 1–12 on the outside,
 * 13–24 on the inside, where 24 reads as midnight. Which ring a touch lands on is
 * decided by its distance from the centre, so 09:00 and 21:00 are one tap apart.
 */
@Composable
private fun ClockDial(
    time: LocalTime,
    mode: DialMode,
    onPick: (LocalTime, finished: Boolean) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = EtaTheme.colors
    val labelStyle = EtaTheme.typography.bodyStrong

    // The gesture handlers must not be rebuilt while a drag is in flight, so they
    // read the live values through these instead of capturing them.
    val currentTime by rememberUpdatedState(time)
    val currentOnPick by rememberUpdatedState(onPick)

    /** Turns a touch into a time. Null for touches too close to the centre to aim. */
    fun resolve(position: Offset, size: Size): LocalTime? {
        val center = Offset(size.width / 2f, size.height / 2f)
        val delta = position - center
        val radius = hypot(delta.x, delta.y)
        val outer = minOf(size.width, size.height) / 2f
        if (radius < outer * 0.18f) return null

        // Zero at twelve o'clock, growing clockwise.
        var turn = (atan2(delta.y, delta.x) / TWO_PI + 0.25).toFloat()
        turn -= floor(turn)

        return when (mode) {
            DialMode.MINUTE -> {
                val steps = 60 / MINUTE_STEP
                LocalTime(currentTime.hour, (turn * steps).roundToInt() % steps * MINUTE_STEP)
            }

            DialMode.HOUR -> {
                val position12 = (turn * 12).roundToInt() % 12
                val inner = radius < outer * 0.67f
                // Position 0 is twelve o'clock: 12 outside, 24 → midnight inside.
                val hour = when {
                    inner && position12 == 0 -> 0
                    inner -> position12 + 12
                    position12 == 0 -> 12
                    else -> position12
                }
                LocalTime(hour, currentTime.minute)
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(mode) {
                detectTapGestures { offset ->
                    resolve(offset, size.toSize())?.let { currentOnPick(it, true) }
                }
            }
            .pointerInput(mode) {
                detectDragGestures(
                    onDragEnd = { currentOnPick(currentTime, true) },
                ) { change, _ ->
                    resolve(change.position, size.toSize())?.let { currentOnPick(it, false) }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = minOf(size.width, size.height) / 2f
        val outerRadius = outer * 0.82f
        val innerRadius = outer * 0.52f

        drawCircle(color = colors.surface, radius = outer, center = center)
        drawCircle(
            color = colors.border,
            radius = outer,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        fun label(text: String, at: Offset, color: Color) {
            val measured = textMeasurer.measure(text, labelStyle)
            drawText(
                textLayoutResult = measured,
                color = color,
                topLeft = at - Offset(measured.size.width / 2f, measured.size.height / 2f),
            )
        }

        when (mode) {
            DialMode.HOUR -> {
                val hour = time.hour
                val onInnerRing = hour == 0 || hour > 12
                val position = when {
                    hour == 0 -> 0
                    hour > 12 -> hour - 12
                    else -> hour % 12
                }
                drawSelector(
                    center = center,
                    target = pointOnRing(
                        center = center,
                        position = position,
                        positions = 12,
                        radius = if (onInnerRing) innerRadius else outerRadius,
                    ),
                    color = colors.accent,
                    outer = outer,
                )

                (1..12).forEach { value ->
                    label(
                        text = value.toString(),
                        at = pointOnRing(center, value % 12, 12, outerRadius),
                        color = if (!onInnerRing && value == hour) {
                            colors.onAccent
                        } else {
                            colors.textPrimary
                        },
                    )
                }
                (13..24).forEach { value ->
                    val shown = if (value == 24) 0 else value
                    label(
                        text = shown.toString().padStart(2, '0'),
                        at = pointOnRing(center, value % 12, 12, innerRadius),
                        color = if (onInnerRing && shown == hour) {
                            colors.onAccent
                        } else {
                            colors.textMuted
                        },
                    )
                }
            }

            DialMode.MINUTE -> {
                val steps = 60 / MINUTE_STEP
                drawSelector(
                    center = center,
                    target = pointOnRing(center, time.minute / MINUTE_STEP, steps, outerRadius),
                    color = colors.accent,
                    outer = outer,
                )

                (0 until 60 step MINUTE_STEP).forEach { value ->
                    label(
                        text = value.toString().padStart(2, '0'),
                        at = pointOnRing(center, value / MINUTE_STEP, steps, outerRadius),
                        color = if (value == time.minute) colors.onAccent else colors.textPrimary,
                    )
                }
            }
        }
    }
}

/** The hand and the knob under the selected number. */
private fun DrawScope.drawSelector(
    center: Offset,
    target: Offset,
    color: Color,
    outer: Float,
) {
    drawLine(color = color, start = center, end = target, strokeWidth = 2.dp.toPx())
    drawCircle(color = color, radius = outer * 0.14f, center = target)
    drawCircle(color = color, radius = 4.dp.toPx(), center = center)
}
