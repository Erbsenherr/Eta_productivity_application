package com.example.eta.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.eta.domain.stats.PensumPoint
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez",
)

/**
 * How a task's workload moved between [from] and [to]: one point per occurrence,
 * joined by a line.
 *
 * The x-axis is always **days** — a point sits on the day it belongs to — and
 * only the lettering changes: day numbers for a week or a month, month names
 * where [months] says the stretch is too long for them. Drawn on a `Canvas`
 * with the foundation's own text measurer; there is no chart library in this
 * project, and one line does not ask for one.
 *
 * [format] turns a value into the label at the two ends of the y-axis.
 */
@Composable
fun PensumChart(
    points: List<PensumPoint>,
    from: LocalDate,
    to: LocalDate,
    months: Boolean,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val colors = EtaTheme.colors
    val labelStyle = EtaTheme.typography.caption

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val shown = points.filter { it.date >= from && it.date <= to }
        val low = shown.minOfOrNull { it.value } ?: 0.0
        val high = shown.maxOfOrNull { it.value } ?: 1.0
        // A flat line gets room above and below, or it would sit on the frame.
        val bottomValue = if (high == low) low - 1.0 else low
        val topValue = if (high == low) high + 1.0 else high

        val topLabel = measurer.measure(format(high), labelStyle)
        val bottomLabel = measurer.measure(format(low), labelStyle)
        val left = maxOf(topLabel.size.width, bottomLabel.size.width) + 8.dp.toPx()
        val bottom = size.height - topLabel.size.height - 6.dp.toPx()
        val top = topLabel.size.height / 2f
        val right = size.width - 6.dp.toPx()
        val span = from.daysUntil(to).coerceAtLeast(1)

        fun x(date: LocalDate) = left + (right - left) * from.daysUntil(date) / span
        fun y(value: Double) =
            (bottom - (bottom - top) * ((value - bottomValue) / (topValue - bottomValue))).toFloat()

        // The frame: the two axes.
        drawLine(colors.border, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
        drawLine(colors.border, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())

        // The y-axis says its two ends; where they are the same number, once.
        drawText(topLabel, colors.textMuted, Offset(0f, y(high) - topLabel.size.height / 2f))
        if (high != low) {
            drawText(bottomLabel, colors.textMuted, Offset(0f, y(low) - bottomLabel.size.height / 2f))
        }

        // The x-axis: a tick and a label wherever one fits.
        var lastLabelEnd = Float.NEGATIVE_INFINITY
        var day = from
        val everyDays = when {
            months -> 1
            span <= 7 -> 1
            else -> 5
        }
        while (day <= to) {
            val text = when {
                months -> MONTHS_SHORT[day.month.ordinal].takeIf { day.day == 1 }
                from.daysUntil(day) % everyDays == 0 -> "${day.day}."
                else -> null
            }
            if (text != null) {
                val measured = measurer.measure(text, labelStyle)
                val at = x(day)
                val start = (at - measured.size.width / 2f).coerceIn(left, right - measured.size.width)
                if (start > lastLabelEnd + 4.dp.toPx()) {
                    drawLine(colors.border, Offset(at, bottom), Offset(at, bottom + 4.dp.toPx()))
                    drawText(measured, colors.textMuted, Offset(start, bottom + 5.dp.toPx()))
                    lastLabelEnd = start + measured.size.width
                }
            }
            day = day.plus(DatePeriod(days = 1))
        }

        if (shown.isEmpty()) return@Canvas

        val line = Path()
        shown.forEachIndexed { index, point ->
            val px = x(point.date)
            val py = y(point.value)
            if (index == 0) line.moveTo(px, py) else line.lineTo(px, py)
        }
        drawPath(line, colors.accent, style = Stroke(width = 2.dp.toPx()))
        // Dots only while they can be told apart; a year of them is a smear.
        if (shown.size <= 62) {
            shown.forEach { drawCircle(colors.accent, 3.dp.toPx(), Offset(x(it.date), y(it.value))) }
        }
    }
}
