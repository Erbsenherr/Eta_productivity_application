package com.example.eta.ui.lists

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.recurrence.freeSpans
import com.example.eta.domain.setup.WEEK
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.DayOfWeek

/**
 * The Wochenschema: seven columns, Monday on the left, midnight at the top.
 *
 * **No labels at all**, as asked — it answers one question, "where is the week
 * still free", and a grid of hours would turn a glance into a reading. Free time
 * is the green, everything booked (the night included) the quiet grey behind it.
 * [draft] is what a form being filled in would add, drawn in the accent colour
 * over both, so a recurring task can be moved until it sits in green. On
 * [clashDays] — the weekdays on which it collides with another standing task —
 * the mark turns red instead: the whole mark rather than the overlapping part,
 * since a quarter of an hour is a sliver at this scale and would not be seen.
 *
 * One [Canvas], like the time picker: there is nothing to tap, and the drawing is
 * the whole of it.
 */
@Composable
fun WeekSchemeChart(
    booked: Map<DayOfWeek, List<IntRange>>,
    modifier: Modifier = Modifier,
    draft: Map<DayOfWeek, List<IntRange>> = emptyMap(),
    height: Dp = 140.dp,
    clashDays: Set<DayOfWeek> = emptySet(),
) {
    val bookedColor = EtaTheme.colors.border
    val freeColor = EtaTheme.colors.success
    val draftColor = EtaTheme.colors.accent
    val clashColor = EtaTheme.colors.danger
    val freeByDay = WEEK.associateWith { freeSpans(booked[it].orEmpty()) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // The picture has no words, so a screen reader gets the one sentence
            // it stands for rather than nothing.
            .semantics { contentDescription = "Wochenschema: freie Zeiten der Woche" },
    ) {
        val gap = 4.dp.toPx()
        val columnWidth = (size.width - gap * (WEEK.size - 1)) / WEEK.size
        val perMinute = size.height / MINUTES_PER_DAY
        val corner = CornerRadius(3.dp.toPx())

        fun span(x: Float, range: IntRange, color: Color, inset: Float = 0f) {
            val top = range.first * perMinute
            val bottom = (range.last + 1) * perMinute
            drawRoundRect(
                color = color,
                topLeft = Offset(x + inset, top),
                size = Size(columnWidth - 2 * inset, (bottom - top).coerceAtLeast(1f)),
                cornerRadius = corner,
            )
        }

        WEEK.forEachIndexed { index, day ->
            val x = index * (columnWidth + gap)
            drawRoundRect(
                color = bookedColor,
                topLeft = Offset(x, 0f),
                size = Size(columnWidth, size.height),
                cornerRadius = corner,
            )
            freeByDay.getValue(day).forEach { span(x, it, freeColor) }
            val markColor = if (day in clashDays) clashColor else draftColor
            draft[day].orEmpty().forEach { span(x, it, markColor, inset = columnWidth * 0.2f) }
        }
    }
}
