package com.example.eta.domain.reminder

import com.example.eta.domain.model.Reminder
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * The day a reminder lands on when no other date was chosen.
 *
 * Today — unless the time has already gone by today, in which case tomorrow. A
 * reminder for 07:00 set at 22:00 means tomorrow morning; ringing at once, or
 * refusing it, would both be reading the entry against its obvious meaning.
 */
fun defaultReminderDate(time: LocalTime, now: LocalDateTime): LocalDate =
    if (LocalTime(time.hour, time.minute) > LocalTime(now.hour, now.minute)) {
        now.date
    } else {
        now.date.plus(DatePeriod(days = 1))
    }

/** The moment a date and time stand for, on whole minutes. */
fun reminderMoment(date: LocalDate, time: LocalTime, timeZone: TimeZone): Instant =
    date.atTime(LocalTime(time.hour, time.minute)).toInstant(timeZone)

/**
 * When the one reminder alarm should next go off.
 *
 * The soonest pending reminder — and if that one is already overdue, because the
 * phone was off or asleep when it was due, a moment from now rather than a time
 * in the past, so it still rings instead of being lost. Null when nothing is left.
 */
fun nextReminderAlarm(pending: List<Reminder>, now: Instant): Instant? {
    val soonest = pending.filter { it.isPending }.minOfOrNull { it.at } ?: return null
    return maxOf(soonest, now + OVERDUE_GRACE)
}

/** How soon an overdue reminder is rung once it is noticed. */
private val OVERDUE_GRACE = 2.seconds
