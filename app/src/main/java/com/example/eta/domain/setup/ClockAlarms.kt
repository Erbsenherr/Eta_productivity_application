package com.example.eta.domain.setup

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** One repeating alarm to be handed to the phone's clock app. */
data class ClockAlarm(
    val time: LocalTime,
    val days: Set<DayOfWeek>,
)

/**
 * The alarms the hours of getting up amount to, as the clock app would hold
 * them — as many as the way the nights were answered:
 *
 * - **one night for every day** → one alarm, on all seven days;
 * - **a weekend night of its own** → two, the week's and the weekend's;
 * - **a night per weekday** → seven, one each.
 *
 * By the *way* they were answered, not by which hours happen to coincide: a
 * weekend that currently gets up at the week's hour is still its own alarm, so
 * moving it later in the clock app does not move the week as well.
 *
 * A day belongs to the alarm of the night that **ends** on it — the same reading
 * `nightEndingOn` gives everything else.
 */
fun UserSetup.clockAlarms(): List<ClockAlarm> = when (nightMode) {
    NightMode.SAME -> listOf(ClockAlarm(wakeTime, DayOfWeek.entries.toSet()))

    NightMode.WEEKEND -> DayOfWeek.entries
        .groupBy { it in weekendDays }
        .toSortedMap()
        .values
        .map { days -> ClockAlarm(wakeTimeOn(days.first()), days.toSet()) }

    NightMode.PER_DAY -> DayOfWeek.entries.map { ClockAlarm(wakeTimeOn(it), setOf(it)) }
}
