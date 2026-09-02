package com.example.erik_iteration_2.domain.recurrence

import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.model.RecurrenceRule
import com.example.erik_iteration_2.domain.model.WeekParity
import com.example.erik_iteration_2.domain.vacation.VacationPlan
import com.example.erik_iteration_2.domain.vacation.startOverrideFor
import com.example.erik_iteration_2.domain.vacation.suspends
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * ISO week number, computed without java.time so the rule stays testable on any
 * platform: the week is the one containing its Thursday.
 */
fun LocalDate.isoWeekNumber(): Int {
    val thursday = plus(DatePeriod(days = 4 - dayOfWeek.isoDayNumber))
    return (thursday.dayOfYear - 1) / 7 + 1
}

/** Which occurrence of its weekday this date is within its month, 1-based. */
fun LocalDate.weekdayOccurrenceInMonth(): Int = (day - 1) / 7 + 1

/** Whether a recurring item is due on [date]. */
fun RecurrenceRule.matches(date: LocalDate): Boolean = when (this) {
    is RecurrenceRule.Daily -> true

    is RecurrenceRule.Weekly -> date.dayOfWeek == weekday

    is RecurrenceRule.Biweekly -> {
        val even = date.isoWeekNumber() % 2 == 0
        date.dayOfWeek == weekday && (parity == WeekParity.EVEN) == even
    }

    is RecurrenceRule.Monthly ->
        date.dayOfWeek == weekday && date.weekdayOccurrenceInMonth() == weekOfMonth
}

/** Every date in [from]..[to] on which this rule fires. */
fun RecurrenceRule.occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    var date = from
    while (date <= to) {
        if (matches(date)) dates += date
        date = date.plus(DatePeriod(days = 1))
    }
    return dates
}

/**
 * Materializes the blocks a set of recurring definitions owes for a date range.
 *
 * [existing] holds the item/date pairs that already have a block, so calling this
 * repeatedly — every day planning, every week planning — stays idempotent and
 * never duplicates or resurrects a block the user deleted from a past day.
 *
 * Definitions without a start time or duration are skipped rather than guessed at.
 */
fun expandRecurring(
    definitions: List<Item>,
    from: LocalDate,
    to: LocalDate,
    existing: Set<Pair<String, LocalDate>>,
    now: Instant,
    vacations: List<VacationPlan> = emptyList(),
): List<PlannedBlock> = definitions.flatMap { item ->
    val rule = item.recurrenceRule ?: return@flatMap emptyList()
    val start = item.startTime ?: return@flatMap emptyList()
    val duration: Duration = item.estimatedDuration ?: return@flatMap emptyList()

    rule.occurrencesBetween(from, to)
        .filterNot { date -> (item.id to date) in existing }
        // A suspended task produces no block at all, which is also why a holiday
        // never triggers a "Nachholen von …": there is nothing to drop.
        .filterNot { date -> vacations.suspends(item.id, date) }
        .map { date ->
            PlannedBlock(
                itemId = item.id,
                date = date,
                start = vacations.startOverrideFor(item.id, date) ?: start,
                plannedDuration = duration,
                origin = BlockOrigin.RECURRING,
                createdAt = now,
                updatedAt = now,
            )
        }
}
