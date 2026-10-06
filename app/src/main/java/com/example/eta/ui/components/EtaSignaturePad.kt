package com.example.eta.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.eta.domain.contract.Signature
import com.example.eta.domain.contract.SignaturePoint
import com.example.eta.ui.theme.EtaTheme
import kotlin.math.abs

/** How far the finger has to travel before another point is kept. */
private const val MIN_STEP = 0.006f

/**
 * A pad to sign with a finger.
 *
 * `Konzept.md` builds the whole contract mechanism on having *promised*, and a typed
 * name is a weaker promise than a signature — so this is a drawing surface, and what
 * it produces is the strokes rather than a picture: see [Signature].
 *
 * Points are thinned as they arrive. A finger resting on the glass would otherwise
 * add one per frame, and a signature is a shape, not a sampling rate.
 */
@Composable
fun EtaSignaturePad(
    value: Signature?,
    onValueChange: (Signature?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    // The strokes being drawn, in unit coordinates. Held here and published on
    // every change, so the caller always has what is on screen.
    var strokes by remember { mutableStateOf(value?.strokes ?: emptyList()) }
    var size by remember { mutableStateOf(Offset(1f, 1f)) }

    val publish = rememberUpdatedState(onValueChange)
    val extent = rememberUpdatedState(size)
    val current = rememberUpdatedState(strokes)

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        EtaSurface(
            modifier = modifier.fillMaxWidth().height(height),
            contentPadding = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .pointerInput(Unit) {
                        size = Offset(this.size.width.toFloat(), this.size.height.toFloat())
                        detectDragGestures(
                            onDragStart = { offset ->
                                strokes = current.value + listOf(listOf(unit(offset, extent.value)))
                            },
                            onDragEnd = { publish.value(Signature(current.value)) },
                            onDragCancel = { publish.value(Signature(current.value)) },
                        ) { change, _ ->
                            change.consume()
                            val point = unit(change.position, extent.value)
                            val list = current.value
                            val stroke = list.lastOrNull() ?: return@detectDragGestures
                            val last = stroke.lastOrNull()
                            val total = list.sumOf { it.size }
                            if (total >= Signature.MAX_POINTS) return@detectDragGestures
                            if (last != null && !worthKeeping(last, point)) {
                                return@detectDragGestures
                            }
                            strokes = list.dropLast(1) + listOf(stroke + point)
                        }
                    },
            ) {
                SignatureCanvas(
                    strokes = strokes,
                    modifier = Modifier.fillMaxWidth().height(height),
                )
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            EtaText(
                text = "Mit dem Finger unterschreiben.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            Spacer(Modifier.weight(1f))
            if (strokes.isNotEmpty()) {
                EtaButton(
                    text = "Neu",
                    style = EtaButtonStyle.Secondary,
                    onClick = {
                        strokes = emptyList()
                        onValueChange(null)
                    },
                )
            }
        }
    }
}

/**
 * A signature as it was signed, drawn to fit whatever room it is given.
 *
 * Falls back to plain text, which is what a contract signed before the pad existed
 * holds in that column — nothing had to be converted.
 */
@Composable
fun SignatureView(signature: String, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    val drawn = remember(signature) { Signature.decode(signature) }
    if (drawn == null) {
        EtaText(
            text = signature,
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
            modifier = modifier,
        )
        return
    }
    SignatureCanvas(strokes = drawn.strokes, modifier = modifier.fillMaxWidth().height(height))
}

@Composable
private fun SignatureCanvas(strokes: List<List<SignaturePoint>>, modifier: Modifier) {
    val ink = EtaTheme.colors.textPrimary
    androidx.compose.foundation.Canvas(modifier = modifier) {
        strokes.forEach { stroke -> drawStroke(stroke, ink) }
    }
}

private fun DrawScope.drawStroke(
    stroke: List<SignaturePoint>,
    ink: androidx.compose.ui.graphics.Color,
) {
    if (stroke.isEmpty()) return
    val width = 2.dp.toPx()
    if (stroke.size == 1) {
        drawCircle(
            color = ink,
            radius = width,
            center = Offset(stroke[0].x * size.width, stroke[0].y * size.height),
        )
        return
    }
    val path = Path()
    stroke.forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = point.y * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = ink, style = Stroke(width = width))
}

private fun unit(offset: Offset, extent: Offset): SignaturePoint = SignaturePoint(
    x = (offset.x / extent.x.coerceAtLeast(1f)).coerceIn(0f, 1f),
    y = (offset.y / extent.y.coerceAtLeast(1f)).coerceIn(0f, 1f),
)

private fun worthKeeping(last: SignaturePoint, next: SignaturePoint): Boolean =
    abs(next.x - last.x) > MIN_STEP || abs(next.y - last.y) > MIN_STEP
