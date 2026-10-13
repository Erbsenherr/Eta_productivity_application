package com.example.eta.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The icons of the tab bar — one per destination, drawn by hand.
 *
 * There is no icon library in this project (it would bring Material with it),
 * and nine glyphs do not ask for one. Each is drawn on a 24-unit grid in one
 * stroke weight and one colour, so they read as a set and take whatever colour
 * the bar gives them.
 */
enum class EtaIcon {
    /** Dashboard: a house. */
    HOME,

    /** Listen: three bulleted lines. */
    LIST,

    /** Verträge: a page with a signature on it. */
    CONTRACT,

    /** Erinnerungen: a bell. */
    BELL,

    /** Ziele: a target. */
    TARGET,

    /** Belohn-o-mat: a present. */
    GIFT,

    /** Statistiken: a bar chart. */
    CHART,

    /** Notiz-Merger: a sheet with its corner folded. */
    NOTE,

    /** Einstellungen: three sliders. */
    SLIDERS,
}

@Composable
fun EtaIconImage(
    icon: EtaIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(22.dp)) { drawIcon(icon, color) }
}

private fun DrawScope.drawIcon(icon: EtaIcon, color: Color) {
    val unit = size.minDimension / 24f
    val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun at(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, at(x1, y1), at(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)

    fun dot(x: Float, y: Float, radius: Float) = drawCircle(color, radius * unit, at(x, y))
    fun ring(x: Float, y: Float, radius: Float) = drawCircle(color, radius * unit, at(x, y), style = stroke)

    /** A path through [points], given as x, y pairs; closed where asked. */
    fun outline(vararg points: Float, closed: Boolean = false) {
        val path = Path()
        points.toList().chunked(2).forEachIndexed { index, (x, y) ->
            if (index == 0) path.moveTo(x * unit, y * unit) else path.lineTo(x * unit, y * unit)
        }
        if (closed) path.close()
        drawPath(path, color, style = stroke)
    }

    when (icon) {
        EtaIcon.HOME -> {
            outline(3f, 11.5f, 12f, 3.5f, 21f, 11.5f)
            outline(5.5f, 10f, 5.5f, 20.5f, 18.5f, 20.5f, 18.5f, 10f)
            outline(10f, 20.5f, 10f, 14.5f, 14f, 14.5f, 14f, 20.5f)
        }

        EtaIcon.LIST -> listOf(6f, 12f, 18f).forEach { y ->
            dot(4.5f, y, 1.3f)
            line(9f, y, 20.5f, y)
        }

        EtaIcon.CONTRACT -> {
            outline(6f, 3f, 18f, 3f, 18f, 21f, 6f, 21f, closed = true)
            line(9f, 7.5f, 15f, 7.5f)
            line(9f, 11f, 15f, 11f)
            // The signature: one stroke that goes up, over and trails off.
            val pen = Path().apply {
                moveTo(8.5f * unit, 17.5f * unit)
                quadraticTo(10f * unit, 13.5f * unit, 11.5f * unit, 17f * unit)
                quadraticTo(12.5f * unit, 19f * unit, 13.5f * unit, 16.5f * unit)
                lineTo(15.5f * unit, 17.5f * unit)
            }
            drawPath(pen, color, style = stroke)
        }

        EtaIcon.BELL -> {
            // The dome: half a circle on two short sides, standing on its rim.
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = at(6.5f, 4.5f),
                size = Size(11f * unit, 11f * unit),
                style = stroke,
            )
            line(6.5f, 10f, 6.5f, 16.5f)
            line(17.5f, 10f, 17.5f, 16.5f)
            line(4f, 16.5f, 20f, 16.5f)
            dot(12f, 19.8f, 1.5f)
        }

        EtaIcon.TARGET -> {
            ring(12f, 12f, 9f)
            ring(12f, 12f, 5f)
            dot(12f, 12f, 1.6f)
        }

        EtaIcon.GIFT -> {
            outline(3.5f, 8f, 20.5f, 8f, 20.5f, 11.5f, 3.5f, 11.5f, closed = true)
            outline(5f, 11.5f, 5f, 20.5f, 19f, 20.5f, 19f, 11.5f)
            line(12f, 8f, 12f, 20.5f)
            // The bow: a loop either side of the ribbon.
            listOf(-1f, 1f).forEach { side ->
                val loop = Path().apply {
                    moveTo(12f * unit, 8f * unit)
                    quadraticTo((12f + side * 2.5f) * unit, 2.5f * unit, (12f + side * 5f) * unit, 4.5f * unit)
                    quadraticTo((12f + side * 5.5f) * unit, 7.5f * unit, 12f * unit, 8f * unit)
                }
                drawPath(loop, color, style = stroke)
            }
        }

        EtaIcon.CHART -> {
            line(3.5f, 20.5f, 20.5f, 20.5f)
            outline(5.5f, 20.5f, 5.5f, 13f, 9f, 13f, 9f, 20.5f)
            outline(10.5f, 20.5f, 10.5f, 5f, 14f, 5f, 14f, 20.5f)
            outline(15.5f, 20.5f, 15.5f, 9.5f, 19f, 9.5f, 19f, 20.5f)
        }

        EtaIcon.NOTE -> {
            outline(5f, 3f, 14.5f, 3f, 19f, 7.5f, 19f, 21f, 5f, 21f, closed = true)
            outline(14.5f, 3f, 14.5f, 7.5f, 19f, 7.5f)
            line(8f, 12f, 16f, 12f)
            line(8f, 16f, 13.5f, 16f)
        }

        EtaIcon.SLIDERS -> listOf(6f to 9f, 12f to 15f, 18f to 8f).forEach { (y, knob) ->
            line(3.5f, y, 20.5f, y)
            dot(knob, y, 2.3f)
        }
    }
}
