package com.example.eta.domain.recurrence

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.stampedWith
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.vacation.VacationPlan
import com.example.eta.domain.vacation.startOverrideFor
import com.example.eta.domain.vacation.suspends
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
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

/**
 * The definitions a set of chosen weekdays asks for.
 *
 * All seven collapse into [RecurrenceRule.Daily], which is the rule that exists
 * for exactly that. Anything less becomes **one rule per weekday**, following the
 * setup questionnaire's precedent: a task on Tuesday and Thursday is two
 * definitions, so that later moving one of them does not drag the other along.
 *
 * Returned in weekday order, and empty for an empty set — no day is not an answer.
 */
fun rulesForWeekdays(weekdays: Set<DayOfWeek>): List<RecurrenceRule> = when {
    weekdays.isEmpty() -> emptyList()
    weekdays.size == DayOfWeek.entries.size -> listOf(RecurrenceRule.Daily)
    else -> DayOfWeek.entries
        .filter { it in weekdays }
        .map { RecurrenceRule.Weekly(it) }
}

/**
 * The weekdays a rule falls on — the rough inverse of [rulesForWeekdays].
 *
 * **Lossy on purpose, and only safe where it is used.** A fortnightly or monthly
 * rule reduces to its weekday, so round-tripping one through a weekday picker
 * would turn it into an ordinary weekly rule. Nothing does: the only editor that
 * reads this is the Sammelliste's, and a card there has no rule at all yet. It
 * exists so that editor has something to show rather than an empty picker.
 */
fun RecurrenceRule.weekdaysOf(): Set<DayOfWeek> = when (this) {
    is RecurrenceRule.Daily -> DayOfWeek.entries.toSet()
    is RecurrenceRule.Weekly -> setOf(weekday)
    is RecurrenceRule.Biweekly -> setOf(weekday)
    is RecurrenceRule.Monthly -> setOf(weekday)
}

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
                // The definition's journey and break travel to every occurrence
                // it lays down; a standing task with a commute has one each time.
                travelBefore = item.travelBefore,
                returnAfter = item.returnAfter,
                breakAfter = item.breakAfter,
                origin = BlockOrigin.RECURRING,
                createdAt = now,
                updatedAt = now,
            ).stampedWith(item)
        }
}
