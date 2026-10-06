package com.example.eta.domain.recurrence

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.sleepStretches
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WEEK
import kotlin.time.Duration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * The standing week as one picture: per weekday, which minutes are already booked.
 *
 * What the Wochenschema on the Listen tab draws, so a recurring task can be
 * placed where the week still has room. Booked is the **night** from the setup —
 * sleep produces no item, so it has to be read off the answers — and the
 * **container** of every live standing task, journeys and break included, the
 * same span the overlap warning and the planner compare.
 *
 * A fortnightly or monthly task counts as booking its weekday every week. The
 * picture is meant to be read at a glance, and a slot that is taken every other
 * week is not one to plan something weekly into.
 *
 * The lists come back **merged and sorted**, so whatever is not in them is free.
 */
fun weekOccupancy(
    definitions: List<Item>,
    setup: UserSetup?,
    ignoreIds: Set<String> = emptySet(),
): Map<DayOfWeek, List<IntRange>> {
    val byDay = WEEK.associateWith { setup?.sleepStretches(it).orEmpty().toMutableList() }
    definitions
        .filter { it.id !in ignoreIds && it.completedAt == null && it.isConcretized }
        .filter { it.stage != Stage.COLLECTION && it.stage != Stage.LOCKED }
        .forEach { item ->
            val slot = RecurringSlot.of(item) ?: return@forEach
            slot.spansByWeekday().forEach { (day, span) -> byDay.getValue(day) += span }
        }
    return byDay.mapValues { (_, spans) -> mergeSpans(spans) }
}

/** The spans a set of slots — a form being filled in — would book, per weekday. */
fun slotOccupancy(slots: List<RecurringSlot>): Map<DayOfWeek, List<IntRange>> {
    val byDay = WEEK.associateWith { mutableListOf<IntRange>() }
    slots.forEach { slot -> slot.spansByWeekday().forEach { (day, span) -> byDay.getValue(day) += span } }
    return byDay.mapValues { (_, spans) -> mergeSpans(spans) }
}

/** The minutes of each day nothing has booked: the complement of [booked]. */
fun freeSpans(booked: List<IntRange>): List<IntRange> = buildList {
    var cursor = 0
    mergeSpans(booked).forEach { span ->
        if (span.first > cursor) add(cursor until span.first)
        cursor = maxOf(cursor, span.last + 1)
    }
    if (cursor < MINUTES_PER_DAY) add(cursor until MINUTES_PER_DAY)
}

/**
 * The next start at which what [slots] describe sits in free time on **every**
 * one of its weekdays — or null when the rest of the day holds no such stretch.
 *
 * What "Nächster freier Slot" under the overlap warning moves a task to. It looks
 * forward from the start the form has now, never back: the user has said roughly
 * when, and an answer before that would be a different question. The whole
 * container has to fit, journeys and break included, and it has to end inside
 * the day.
 *
 * Measured against [booked], the picture the Wochenschema draws, so the answer
 * lands in green — the night included where the setup is known. Chained onto the
 * end of whatever was in the way rather than onto a grid line, like a growth
 * task: the slot asked for is the *next* one, and rounding up would leave a gap.
 *
 * All slots of one form share start, length and margins and differ only in the
 * weekday, so the first one speaks for them.
 */
fun nextFreeStart(
    slots: List<RecurringSlot>,
    booked: Map<DayOfWeek, List<IntRange>>,
): LocalTime? {
    val slot = slots.firstOrNull() ?: return null
    val lead = slot.travelBefore.wholeMinutes()
    val length = slot.duration.wholeMinutes()
    val tail = slot.returnAfter.wholeMinutes() + slot.breakAfter.wholeMinutes()
    val obstacles = slots
        .flatMap { it.rule.weekdaysOf() }
        .distinct()
        .flatMap { booked[it].orEmpty() }

    var start = maxOf(slot.startTime.minuteOfDay(), lead)
    while (start < MINUTES_PER_DAY && start + length + tail <= MINUTES_PER_DAY) {
        val from = start - lead
        val to = start + length + tail
        val inTheWay = obstacles.filter { it.first < to && it.last >= from }
        if (inTheWay.isEmpty()) return minuteToLocalTime(start)
        start = inTheWay.maxOf { it.last } + 1 + lead
    }
    return null
}

private fun Duration?.wholeMinutes(): Int = this?.inWholeMinutes?.toInt() ?: 0

private fun RecurringSlot.spansByWeekday(): List<Pair<DayOfWeek, IntRange>> {
    val from = containerStartMinute()
    val to = containerEndMinute()
    if (to <= from) return emptyList()
    return rule.weekdaysOf().map { it to (from until to) }
}

/** Sorted, with touching or overlapping spans joined into one. */
internal fun mergeSpans(spans: List<IntRange>): List<IntRange> {
    val sorted = spans.filterNot { it.isEmpty() }.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    sorted.forEach { span ->
        val last = merged.lastOrNull()
        if (last != null && span.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, span.last)
        } else {
            merged += span
        }
    }
    return merged
}
