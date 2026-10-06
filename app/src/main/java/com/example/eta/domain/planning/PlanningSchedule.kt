package com.example.eta.domain.planning

import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.wakeTimeOn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** The two phases the setup schedules an alarm for. */
enum class PlanningPhase {
    DAILY,
    WEEKLY,
}

/** What the snooze button buys. */
val PLANNING_SNOOZE: Duration = 5.minutes

/** How long the alarm waits before asking again on its own. */
val PLANNING_RETRY: Duration = 5.minutes

/** The longest a "NOTFALL" may push a phase back. A day would just skip it. */
const val MAX_EMERGENCY_HOURS = 12

/**
 * When the phase is next due, strictly after [after].
 *
 * Strictly, so that firing at the appointed minute cannot schedule the same
 * moment again and loop.
 */
fun nextPlanning(setup: UserSetup, phase: PlanningPhase, after: LocalDateTime): LocalDateTime =
    when (phase) {
        PlanningPhase.DAILY -> nextDaily(setup.dailyPlanningTime, after)
        PlanningPhase.WEEKLY -> nextWeekly(setup, after)
    }

private fun nextDaily(time: LocalTime, after: LocalDateTime): LocalDateTime {
    val today = LocalDateTime(after.date, time)
    return if (today > after) today else LocalDateTime(after.date.plus(DatePeriod(days = 1)), time)
}

private fun nextWeekly(setup: UserSetup, after: LocalDateTime): LocalDateTime {
    val time = setup.weeklyPlanningTime
    // Walk forward at most a week; the first matching weekday that is still ahead.
    for (offset in 0..7) {
        val date: LocalDate = after.date.plus(DatePeriod(days = offset))
        if (date.dayOfWeek != setup.weeklyPlanningDay) continue
        val candidate = LocalDateTime(date, time)
        if (candidate > after) return candidate
    }
    // Unreachable for a valid weekday, but a total function is easier to trust.
    return LocalDateTime(after.date.plus(DatePeriod(days = 7)), time)
}

/** What a tap on the wake alarm's snooze buys. Longer than the planning one. */
val WAKE_SNOOZE: Duration = 9.minutes

/**
 * When the wake alarm is next due, or null when the user has not asked for one.
 *
 * Strict about "after", like the planning alarm and for the same reason: ringing at the appointed minute must not be able to schedule that
 * same minute again and loop.
 */
fun nextWake(setup: UserSetup, after: LocalDateTime): LocalDateTime? {
    if (!setup.wakeAlarm) return null
    // Day by day, since the weekend may get up at an hour of its own.
    for (offset in 0..7) {
        val date: LocalDate = after.date.plus(DatePeriod(days = offset))
        val candidate = LocalDateTime(date, setup.wakeTimeOn(date.dayOfWeek))
        if (candidate > after) return candidate
    }
    return null
}

/**
 * Whether the alarm should sound.
 *
 * `Planungsphase.md` silences it in exactly two cases: the user already has the
 * app open, or the phase is done. Everything else keeps ringing every five
 * minutes, which is the point — the phase is the spine of the day.
 */
fun shouldRing(appInForeground: Boolean, phaseCompleted: Boolean): Boolean =
    !appInForeground && !phaseCompleted

/** Where a snooze puts the next ring. */
fun snoozedFrom(now: Instant): Instant = now + PLANNING_SNOOZE

/**
 * Where a "NOTFALL" of [hours] puts it.
 *
 * Clamped rather than free: pushing a phase a whole day back is not deferring it,
 * it is skipping it, and the phase is what keeps the plan honest.
 */
fun deferredFrom(now: Instant, hours: Int): Instant =
    now + hours.coerceIn(1, MAX_EMERGENCY_HOURS).hours
