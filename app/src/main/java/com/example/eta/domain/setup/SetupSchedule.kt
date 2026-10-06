package com.example.eta.domain.setup

import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.overlaps
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * One occupied stretch of a weekday, in minutes from midnight.
 *
 * Everything the questionnaire produces is flattened into these before being
 * checked for overlaps or counted, so sleep, work and tasks are treated alike.
 * A stretch that crosses midnight is split into two spans on adjacent days —
 * clipping instead would silently drop the small hours from the maths.
 */
data class SetupSpan(
    val label: String,
    val weekday: DayOfWeek,
    val fromMinute: Int,
    val toMinute: Int,
) {
    val minutes: Int get() = toMinute - fromMinute

    fun overlaps(other: SetupSpan): Boolean =
        weekday == other.weekday &&
            overlaps(fromMinute, toMinute, other.fromMinute, other.toMinute)
}

/** Two answers claiming the same minutes — what the concept calls a Doppeltbelegung. */
data class SetupConflict(
    val weekday: DayOfWeek,
    val first: SetupSpan,
    val second: SetupSpan,
) {
    val fromMinute: Int get() = maxOf(first.fromMinute, second.fromMinute)
    val toMinute: Int get() = minOf(first.toMinute, second.toMinute)
}

private fun DayOfWeek.next(): DayOfWeek = WEEK[(WEEK.indexOf(this) + 1) % WEEK.size]

/**
 * Adds a stretch to [target], wrapping past midnight onto the following weekday.
 * Zero-length stretches are skipped: an answer of "no morning time" should not
 * show up as an appointment.
 */
private fun MutableList<SetupSpan>.addSpan(
    label: String,
    weekday: DayOfWeek,
    start: LocalTime,
    duration: Duration,
) {
    val length = duration.inWholeMinutes.toInt()
    if (length <= 0) return
    val from = start.minuteOfDay()
    val end = from + length
    if (end <= MINUTES_PER_DAY) {
        add(SetupSpan(label, weekday, from, end))
    } else {
        add(SetupSpan(label, weekday, from, MINUTES_PER_DAY))
        add(SetupSpan(label, weekday.next(), 0, end - MINUTES_PER_DAY))
    }
}

private fun MutableList<SetupSpan>.addEveryDay(
    label: String,
    start: LocalTime,
    duration: Duration,
) = WEEK.forEach { addSpan(label, it, start, duration) }

object SetupLabels {
    const val SLEEP = "Schlafen"
    const val BED_PREP = "Bettfertig machen"
    const val MORNING = "Morgenzeit"

    /** The break inside a work block — never a question of its own. */
    const val PAUSE = "Pause"
    const val COOKING = "Kochen"
    const val MEAL_PREP = "Kochen & Einkaufen"
    const val HOUSEKEEPING = "Hausputz"
    const val SPORT = "Sport"
    const val FREE_TIME = "Freie Zeit"
    const val MINDFULNESS = "Achtsamkeit"
    const val WORK = "Arbeit / Uni"
    const val DAILY_PLANNING = "Tagesplanung"
    const val WEEKLY_PLANNING = "Wochenplanung"
}

/** How long the night lasts, from falling asleep to getting up. */
fun UserSetup.sleepDuration(): Duration {
    val from = sleepTime.minuteOfDay()
    val to = wakeTime.minuteOfDay()
    val length = if (to > from) to - from else to + MINUTES_PER_DAY - from
    return length.minutes
}

/** How long winding down lasts, from putting things away to lights out. */
fun UserSetup.bedPrepDuration(): Duration {
    val from = bedPrepTime.minuteOfDay()
    val to = sleepTime.minuteOfDay()
    val length = if (to >= from) to - from else to + MINUTES_PER_DAY - from
    return length.minutes
}

/**
 * Everything the answers occupy in a representative week.
 *
 * A biweekly Hausputz is included as if it fired every week: on the weeks it does
 * fire the clash is real, and warning about it is the point.
 */
fun UserSetup.weeklySpans(): List<SetupSpan> {
    val spans = mutableListOf<SetupSpan>()

    spans.addEveryDay(SetupLabels.SLEEP, sleepTime, sleepDuration())
    spans.addEveryDay(SetupLabels.BED_PREP, bedPrepTime, bedPrepDuration())
    spans.addEveryDay(SetupLabels.MORNING, wakeTime, morningDuration)
    spans.addEveryDay(SetupLabels.FREE_TIME, freeTime.start, freeTime.duration)

    when (val plan = meals) {
        is MealPlan.MealPrep -> plan.slot.orderedWeekdays.forEach {
            spans.addSpan(SetupLabels.MEAL_PREP, it, plan.slot.start, plan.slot.duration)
        }

        is MealPlan.DailyCooking -> plan.slots.forEach {
            spans.addEveryDay(SetupLabels.COOKING, it.start, it.duration)
        }
    }

        housekeeping?.slot?.let { slot ->
        slot.orderedWeekdays.forEach {
            spans.addSpan(SetupLabels.HOUSEKEEPING, it, slot.start, slot.duration)
        }
    }
    sport?.let { slot ->
        slot.orderedWeekdays.forEach { spans.addSpan(SetupLabels.SPORT, it, slot.start, slot.duration) }
    }

    when (val plan = mindfulness) {
        is MindfulnessPlan.EveryDay ->
            spans.addEveryDay(SetupLabels.MINDFULNESS, plan.slot.start, plan.slot.duration)

        is MindfulnessPlan.Weekly -> plan.slot.orderedWeekdays.forEach {
            spans.addSpan(SetupLabels.MINDFULNESS, it, plan.slot.start, plan.slot.duration)
        }

        null -> Unit
    }

    // Work is added piece by piece, so the break inside a block does not read as
    // an overlap with the work around it.
    WEEK.forEach { weekday ->
        work.segmentsOn(weekday).forEach { segment ->
            spans.addSpan(
                label = when (segment.kind) {
                    WorkSegmentKind.WORK -> SetupLabels.WORK
                    WorkSegmentKind.PAUSE -> SetupLabels.PAUSE
                },
                weekday = weekday,
                start = segment.span.start,
                duration = segment.span.duration,
            )
        }
    }

    return spans.sortedWith(compareBy({ WEEK.indexOf(it.weekday) }, { it.fromMinute }))
}

/**
 * Every pair of answers that claims the same minutes. Reported while the
 * questionnaire is still being filled in, so the user can move one of them.
 *
 * Two spans of the *same* answer are not a conflict: a night wrapping onto the
 * next weekday is one appointment, not two.
 */
fun UserSetup.conflicts(): List<SetupConflict> {
    val spans = weeklySpans()
    val conflicts = mutableListOf<SetupConflict>()
    for (i in spans.indices) {
        for (j in i + 1 until spans.size) {
            val first = spans[i]
            val second = spans[j]
            if (first.weekday != second.weekday) continue
            if (first.label == second.label) continue
            if (first.overlaps(second)) conflicts += SetupConflict(first.weekday, first, second)
        }
    }
    return conflicts
}

/** The occupied minutes of one weekday, overlaps counted once. */
fun List<SetupSpan>.occupiedMinutes(): Int {
    if (isEmpty()) return 0
    val sorted = sortedBy { it.fromMinute }
    var total = 0
    var from = sorted.first().fromMinute
    var to = sorted.first().toMinute
    for (span in sorted.drop(1)) {
        if (span.fromMinute > to) {
            total += to - from
            from = span.fromMinute
            to = span.toMinute
        } else {
            to = maxOf(to, span.toMinute)
        }
    }
    return total + (to - from)
}

/**
 * What is left of the week once the standing schedule and the social budget are
 * taken out — the "Freistunden" the weekly planning phase distributes.
 */
fun UserSetup.freeMinutesPerWeek(): Int {
    val occupied = weeklySpans()
        .groupBy { it.weekday }
        .values
        .sumOf { it.occupiedMinutes() }
    val social = socialTimePerWeek.inWholeMinutes.toInt()
    return (WEEK.size * MINUTES_PER_DAY - occupied - social).coerceAtLeast(0)
}
