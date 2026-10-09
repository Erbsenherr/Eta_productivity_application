package com.example.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.eta.ui.format.formatMonthYear
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The days of one month as the calendar lays them out: weeks from Monday, with
 * null where a week reaches into the month before or after.
 *
 * Pure, so the one piece of arithmetic in the picker can be checked without a
 * screen — a month that starts on a Sunday and February in a leap year are the
 * two a grid gets wrong.
 */
fun monthGrid(year: Int, month: Int): List<List<LocalDate?>> {
    val first = LocalDate(year, month, 1)
    val length = first.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    val lead = first.dayOfWeek.ordinal
    val cells: List<LocalDate?> = List(lead) { null } +
        (1..length).map { LocalDate(year, month, it) }
    return cells.chunked(DAYS_IN_WEEK).map { week -> week + List(DAYS_IN_WEEK - week.size) { null } }
}

private const val DAYS_IN_WEEK = 7
private val WEEKDAY_LETTERS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/**
 * A calendar to pick one day from — any day, in any month of any year.
 *
 * The first date picker in the app. Every other date is a day stepper with week
 * buttons, which is right for "in a few days" and wrong for "the third of
 * March next year": thirty taps is not an answer. Hand-built like the clock dial,
 * there being no Material to borrow one from.
 *
 * Tapping a day **is** the answer and closes the window; there is nothing else
 * in it to set. ‹ › turn the month, « » the year. Days before [minimum] are
 * drawn faint and do nothing.
 */
@Composable
fun EtaDatePickerDialog(
    title: String,
    value: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    minimum: LocalDate = today,
) {
    // The first of the month on show; opens on the month the value is in.
    var shown by remember { mutableStateOf(LocalDate(value.year, value.month, 1)) }
    val weeks = remember(shown) { monthGrid(shown.year, shown.month.ordinal + 1) }

    EtaDialog(title = title, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TurnButton("«") { shown = shown.minus(DatePeriod(years = 1)) }
            TurnButton("‹") { shown = shown.minus(DatePeriod(months = 1)) }
            EtaText(
                text = shown.formatMonthYear(),
                style = EtaTheme.typography.bodyStrong,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TurnButton("›") { shown = shown.plus(DatePeriod(months = 1)) }
            TurnButton("»") { shown = shown.plus(DatePeriod(years = 1)) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            Row {
                WEEKDAY_LETTERS.forEach { letter ->
                    EtaText(
                        text = letter,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            selected = day == value,
                            isToday = day == today,
                            enabled = day != null && day >= minimum,
                            onClick = { day?.let(onConfirm) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Heute", onClick = { onConfirm(today) })
        }
    }
}

@Composable
private fun TurnButton(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    EtaText(
        text = label,
        style = EtaTheme.typography.title,
        color = EtaTheme.colors.accent,
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = EtaTheme.spacing.md, vertical = EtaTheme.spacing.sm),
    )
}

@Composable
private fun DayCell(
    day: LocalDate?,
    selected: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = EtaTheme.shapes.small

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                when {
                    selected -> Modifier.background(EtaTheme.colors.accent, shape)
                    // Today is ringed rather than filled, so it can be found
                    // without being mistaken for the answer.
                    isToday -> Modifier.border(1.dp, EtaTheme.colors.accent, shape)
                    else -> Modifier
                },
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) {
            EtaText(
                text = day.day.toString(),
                style = EtaTheme.typography.body,
                color = if (selected) EtaTheme.colors.onAccent else EtaTheme.colors.textPrimary,
            )
        }
    }
}
