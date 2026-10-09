package com.example.eta.ui.format

import com.example.eta.domain.model.Priority
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

/**
 * A set of weekdays the way a timetable writes it: "täglich", "Mo–Fr",
 * "Mo, Mi, Fr", "Sa–So". Runs of three or more collapse into a range; two
 * neighbours stay a list, since "Di–Mi" reads like a typo for "Di, Mi".
 */
fun formatWeekdays(days: Set<DayOfWeek>): String {
    if (days.size == WEEKDAYS.size) return "täglich"
    val ordinals = days.map { it.ordinal }.sorted()
    val runs = mutableListOf<MutableList<Int>>()
    ordinals.forEach { ordinal ->
        val last = runs.lastOrNull()
        if (last != null && last.last() == ordinal - 1) last += ordinal else runs += mutableListOf(ordinal)
    }
    return runs.joinToString(", ") { run ->
        if (run.size >= 3) {
            "${WEEKDAYS[run.first()]}–${WEEKDAYS[run.last()]}"
        } else {
            run.joinToString(", ") { WEEKDAYS[it] }
        }
    }
}

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

/**
 * "1:30 h" for anything over an hour, otherwise "45 min".
 *
 * Seconds appear only where a duration has them — "1:30:20 h", "12 min 30 s",
 * "45 s" — which since step 24 is a growth task set to the second. Every other
 * duration in the app is whole minutes and reads exactly as before.
 */
fun Duration.formatShort(): String {
    val totalMinutes = inWholeMinutes
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = (inWholeSeconds % 60).toInt()
    return when {
        seconds == 0 && hours > 0 -> "$hours:${minutes.toInt().pad2()} h"
        seconds == 0 -> "$minutes min"
        hours > 0 -> "$hours:${minutes.toInt().pad2()}:${seconds.pad2()} h"
        minutes > 0 -> "$minutes min $seconds s"
        else -> "$seconds s"
    }
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

/** A change of points: "+1.5", "-5", "0". The minus comes from [formatPoints]. */
fun formatSignedPoints(value: Double): String =
    formatPoints(value).let { if (value > 0 && it != "0") "+$it" else it }

/** "Sa, 22. August 2027" — for a date that may lie in another year. */
fun LocalDate.formatWithYear(): String = "${formatLong()} $year"

/** "August 2027" */
fun LocalDate.formatMonthYear(): String = "${MONTHS[month.ordinal]} $year"
