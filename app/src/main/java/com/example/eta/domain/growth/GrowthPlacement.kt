package com.example.eta.domain.growth

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.containerEndMinute
import com.example.eta.domain.planning.containerStartMinute
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.claimsItsSlot
import com.example.eta.domain.recurrence.RecurringSlot
import com.example.eta.domain.recurrence.sharedWeekdays
import kotlin.time.Duration
import kotlinx.datetime.DayOfWeek

/** Where a dynamically placed growth task should sit, and where it sits now. */
data class GrowthMove(
    val block: PlannedBlock,
    val toMinute: Int,
) {
    val moved: Boolean get() = toMinute != block.start.minuteOfDay()
}

/**
 * Lays the day's dynamic growth tasks down again, in order of priority.
 *
 * The rule, as the user put it: the start time of a dynamic growth task is its
 * **earliest** hour, and if that hour is taken the task begins at the next slot
 * that fits. Two of them wanting the same slot is settled by the order of the
 * Growth-Tasks tab, and a *static* task standing in the way — an ordinary
 * standing task, or a growth task without dynamic placement — pushes the growth
 * task behind it, after which the same priority rule applies again.
 *
 * The idea behind it is gapless stretches of work, which is why placement chains
 * exactly onto the end of whatever was in the way rather than onto the next grid
 * line. See [firstFreeGrowthStart].
 *
 * Only **open** occurrences are moved, and only an open block is an obstacle. A
 * completed one has released the stretch it was given — a growth task may grow
 * into it — and a called-off one holds nothing either. Everything still standing
 * is an obstacle and is never touched: an appointment, a dragged ToDo and a plain
 * standing task all say "this time is taken", and it is the growth task that
 * gives way.
 *
 * Returns one entry per dynamic growth occurrence, moved or not, so the caller
 * writes only what changed.
 */
fun placeDynamicGrowth(blocks: List<BlockWithItem>): List<GrowthMove> {
    // A flexible occurrence was put where it is by hand, on purpose: it is an
    // obstacle like any static task, not something this pass moves back.
    val (movable, rest) = blocks.partition {
        it.isDynamicGrowth() && it.block.isOpen && !it.block.flexible
    }
    if (movable.isEmpty()) return emptyList()

    val obstacles = rest
        .filter { it.claimsItsSlot() }
        .map { MinuteSpan(it.block.containerStartMinute(), it.block.containerEndMinute()) }
        .toMutableList()

    return movable
        .sortedWith(
            compareBy(
                { it.item.growthOrder ?: Int.MAX_VALUE },
                { it.item.name },
                { it.item.id },
            ),
        )
        .map { entry ->
            val block = entry.block
            val lead = block.travelBefore.minutes()
            val tail = block.returnAfter.minutes() + block.breakAfter.minutes()
            val length = block.effectiveDuration.inWholeMinutes.toInt()
            val earliest = (entry.item.startTime ?: block.start).minuteOfDay()

            // No room left: it stays where it is and shows up in the dashboard's
            // "Überschneidung" box. It is still an obstacle to the ones behind it,
            // or they would quietly stack on top of it as well.
            val at = firstFreeGrowthStart(obstacles, earliest, length, lead, tail)
                ?: block.start.minuteOfDay()
            obstacles += MinuteSpan(
                (at - lead).coerceAtLeast(0),
                (at + length + tail).coerceAtMost(MINUTES_PER_DAY),
            )
            GrowthMove(block, at)
        }
}

private fun BlockWithItem.isDynamicGrowth(): Boolean = item.isGrowthTask && item.growthDynamic

private fun Duration?.minutes(): Int = this?.inWholeMinutes?.toInt() ?: 0

/**
 * Why the final form of a growth task will not fit on one of its weekdays.
 *
 * [NO_ROOM] is the dynamic case — the day is full from the earliest hour on, so
 * there is no slot the task could ever grow into. [COLLIDES] is the static one:
 * the hour is fixed, and by the time the task has grown something else is
 * standing in it.
 */
enum class GrowthFitProblem { NO_ROOM, COLLIDES }

data class GrowthFitIssue(
    val weekday: DayOfWeek,
    val problem: GrowthFitProblem,
)

/**
 * One thing a day has to make room for while the check runs: either a definition
 * that is already there, or the candidate being set up.
 */
private data class FitCandidate(
    val order: Int,
    val earliest: Int,
    val length: Int,
    val lead: Int,
    val tail: Int,
    /** True for the one the user is looking at, whose fate is the answer. */
    val isSubject: Boolean,
)

/**
 * Whether the task will still fit once it has grown all the way.
 *
 * The user asked for this at the moment a growth task is set up: a target length
 * that can never be reached without a clash is worth knowing about while the
 * form is still open, rather than in four weeks when the day runs out of room.
 * So the check is made against the **final** form of everything — every growth
 * task at *its* target length, not at today's — which is the only picture in
 * which the question has a stable answer.
 *
 * A warning, never a refusal: the day may well be rearranged before the target
 * is reached, and only the user knows whether it will be. [ignoreIds] are the
 * rows being edited, which would otherwise collide with themselves.
 */
fun growthTargetFit(
    rules: List<RecurrenceRule>,
    earliestStart: Int,
    target: Duration,
    travelBefore: Duration?,
    returnAfter: Duration?,
    breakAfter: Duration?,
    dynamic: Boolean,
    order: Int?,
    definitions: List<Item>,
    ignoreIds: Set<String> = emptySet(),
): List<GrowthFitIssue> {
    val subject = FitCandidate(
        order = order ?: Int.MAX_VALUE,
        earliest = earliestStart,
        length = target.inWholeMinutes.toInt(),
        lead = travelBefore.minutes(),
        tail = returnAfter.minutes() + breakAfter.minutes(),
        isSubject = true,
    )
    if (subject.length <= 0) return emptyList()

    val others = definitions.filter {
        it.id !in ignoreIds &&
            it.completedAt == null &&
            it.stage != Stage.COLLECTION &&
            it.stage != Stage.LOCKED
    }

    return rules.mapNotNull { rule ->
        val weekday = rule.namedWeekday()
        val onTheDay = others.filter { other ->
            val otherRule = other.recurrenceRule ?: return@filter false
            rule.sharedWeekdays(otherRule).isNotEmpty()
        }

        // Everything with a fixed hour, at its final length: that is what "will
        // it ever fit" has to be asked against.
        val obstacles = onTheDay
            .filterNot { it.isGrowthTask && it.growthDynamic }
            .mapNotNull { it.finalSlot()?.span() }
            .toMutableList()

        // The dynamic ones chain in priority order, exactly as the day itself
        // does, with the candidate taking its own place in that queue.
        val queue = onTheDay
            .filter { it.isGrowthTask && it.growthDynamic }
            .mapNotNull { other -> other.finalSlot()?.let { other.growthOrder to it } }
            .map { (otherOrder, slot) ->
                FitCandidate(
                    order = otherOrder ?: Int.MAX_VALUE,
                    earliest = slot.startTime.minuteOfDay(),
                    length = slot.duration.inWholeMinutes.toInt(),
                    lead = slot.travelBefore.minutes(),
                    tail = slot.returnAfter.minutes() + slot.breakAfter.minutes(),
                    isSubject = false,
                )
            }

        if (!dynamic) {
            // A fixed hour: the dynamic ones give way to it, so what is left to
            // ask is whether anything immovable still stands in that hour.
            val own = MinuteSpan(subject.earliest - subject.lead, subject.earliest + subject.length + subject.tail)
            return@mapNotNull when {
                own.to > MINUTES_PER_DAY -> GrowthFitIssue(weekday, GrowthFitProblem.NO_ROOM)
                obstacles.any { it.overlaps(own) } -> GrowthFitIssue(weekday, GrowthFitProblem.COLLIDES)
                else -> null
            }
        }

        var fits = false
        (queue + subject).sortedBy { it.order }.forEach { candidate ->
            val at = firstFreeGrowthStart(
                obstacles = obstacles,
                earliest = candidate.earliest,
                length = candidate.length,
                lead = candidate.lead,
                tail = candidate.tail,
            ) ?: return@forEach
            if (candidate.isSubject) fits = true
            obstacles += MinuteSpan(at - candidate.lead, at + candidate.length + candidate.tail)
        }
        if (fits) null else GrowthFitIssue(weekday, GrowthFitProblem.NO_ROOM)
    }
}

/**
 * The weekday a rule stands for.
 *
 * The daily rule names all seven; it is reported as Monday, since a task that
 * runs every day and does not fit fits on no day, and seven identical warnings
 * would say the one thing seven times.
 */
private fun RecurrenceRule.namedWeekday(): DayOfWeek = when (this) {
    RecurrenceRule.Daily -> DayOfWeek.MONDAY
    is RecurrenceRule.Weekly -> weekday
    is RecurrenceRule.Biweekly -> weekday
    is RecurrenceRule.Monthly -> weekday
}

/** The slot a definition claims once it has finished growing. */
private fun Item.finalSlot(): RecurringSlot? {
    val base = RecurringSlot.of(this) ?: return null
    val target = growthTarget ?: return base
    return base.copy(duration = maxOf(base.duration, target))
}

private fun RecurringSlot.span() = MinuteSpan(containerStartMinute(), containerEndMinute())
