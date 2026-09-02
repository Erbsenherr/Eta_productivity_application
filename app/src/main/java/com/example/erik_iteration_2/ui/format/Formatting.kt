package com.example.erik_iteration_2.ui.format

import com.example.erik_iteration_2.domain.model.Priority
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val WEEKDAY_NAMES = listOf(
    "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag",
)
private val MONTHS = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember",
)

private fun Int.pad2(): String = toString().padStart(2, '0')

/** "18:00" */
fun LocalTime.formatClock(): String = "${hour.pad2()}:${minute.pad2()}"

/** "Sa, 22. August" */
fun LocalDate.formatLong(): String =
    "${WEEKDAYS[dayOfWeek.ordinal]}, $day. ${MONTHS[month.ordinal]}"

/** The four priorities, worded as `ToDo-Karten.md` words them. */
fun Priority.formatLong(): String = when (this) {
    Priority.URGENT_MUST -> "Muss zeitnah geschehen"
    Priority.MUST -> "Muss geschehen"
    Priority.URGENT_WANT -> "Soll zeitnah geschehen"
    Priority.WANT -> "Soll geschehen"
}

/** "Mo" — for the weekday pills, which have room for two letters. */
fun DayOfWeek.formatVeryShort(): String = WEEKDAYS[ordinal]

/** "Montag" */
fun DayOfWeek.formatLong(): String = WEEKDAY_NAMES[ordinal]

/** Minutes as "12 h 30 min", used for the weekly free-hour totals. */
fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours h"
        else -> "$hours h $minutes min"
    }
}

/** "1:30 h" for anything over an hour, otherwise "45 min". */
fun Duration.formatShort(): String {
    val totalMinutes = inWholeMinutes
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours:${minutes.toInt().pad2()} h" else "$minutes min"
}

/**
 * Countdown to a deadline, coarsening as it recedes: seconds only matter in the
 * last hour, and past deadlines read as overdue rather than as a negative number.
 */
fun formatCountdown(target: Instant, now: Instant): String {
    val remaining = target - now
    if (remaining.isNegative()) return "überfällig"

    val days = remaining.inWholeDays
    val hours = remaining.inWholeHours % 24
    val minutes = remaining.inWholeMinutes % 60
    val seconds = remaining.inWholeSeconds % 60

    return when {
        days > 0 -> "${days}T ${hours}h"
        remaining.inWholeHours > 0 -> "${remaining.inWholeHours}h ${minutes}m"
        else -> "${minutes}m ${seconds}s"
    }
}

/** Points, trimmed of a pointless ".0" — the scale uses halves throughout. */
fun formatPoints(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
