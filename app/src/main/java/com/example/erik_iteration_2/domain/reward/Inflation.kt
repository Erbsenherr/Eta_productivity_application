package com.example.erik_iteration_2.domain.reward

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** The share of savings the weekly devaluation takes off. */
const val WEEKLY_INFLATION_RATE = 0.30

/**
 * What the weekly devaluation costs at a given balance.
 *
 * Applied to the balance as it stands when it falls due — the week's harvests were
 * credited each evening and are devalued along with everything else, which is the
 * reading the user confirmed.
 *
 * A negative balance is left alone. Taking 30% off a debt would pay the user for
 * being in the red, which is the opposite of what inflation is for here.
 */
fun weeklyInflationOn(balance: Double): Double =
    if (balance > 0.0) balance * WEEKLY_INFLATION_RATE else 0.0

/** The most recent [weekday] on or before [today] — when the devaluation last fell due. */
fun lastDueDate(weekday: DayOfWeek, today: LocalDate): LocalDate {
    var date = today
    repeat(7) {
        if (date.dayOfWeek == weekday) return date
        date = date.minus(DatePeriod(days = 1))
    }
    return today
}

/**
 * Whether the devaluation is owed.
 *
 * Tied to the **due date**, not to visiting the planning screen: the week turning
 * over is a fact about the calendar, and hanging it on a visit meant never opening
 * the phase kept the savings.
 *
 * [since] is the floor for a fresh account — the day the setup was answered. Without
 * it a new user with no devaluation on record would be charged the moment they
 * first earned anything, days before their week had turned.
 */
fun isInflationDue(
    lastApplied: LocalDate?,
    since: LocalDate,
    weekday: DayOfWeek,
    today: LocalDate,
): Boolean = lastDueDate(weekday, today) > (lastApplied ?: since)
