package com.example.eta.domain.planning

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Where something that is not on the day yet is meant to go.
 *
 * A block without a row: the same five numbers a [PlannedBlock] answers every
 * question about time with, so that a card can be asked "what would you run
 * into" before anything is written. The planner used to answer that question by
 * itself, sliding the card to the next free hour; since step 41 it asks, and
 * this is what it asks about.
 */
data class PlacementSpan(
    val startMinute: Int,
    val duration: Duration,
    val travelBefore: Duration? = null,
    val returnAfter: Duration? = null,
    val breakAfter: Duration? = null,
) {
    val endMinute: Int
        get() = (startMinute + duration.inWholeMinutes.toInt()).coerceAtMost(MINUTES_PER_DAY)

    val containerStart: Int get() = containerStartMinute(startMinute, travelBefore)

    val containerEnd: Int get() = containerEndMinute(endMinute, returnAfter, breakAfter)
}

/** What [this] would run into, soonest first. Empty when it fits as it is. */
fun PlacementSpan.collisions(existing: List<PlannedBlock>): List<PlannedBlock> =
    existing
        .filter {
            overlaps(containerStart, containerEnd, it.containerStartMinute(), it.containerEndMinute())
        }
        .sortedBy { it.containerStartMinute() }

/**
 * "ToDo anpassen": the new card gives way, and the plan stays as it is.
 *
 * Three things are tried, in the order that costs the card least:
 *
 * 1. **Without its break, where it was put.** The break is the part that yields
 *    when a day is full — the rule the planner always had.
 * 2. **In a free stretch its own span touches**, cut down to what that stretch
 *    holds. The one that leaves most of the task is taken, the earlier of two
 *    alike. A stretch is used as far as it reaches, also past the end the card
 *    asked for: a card dropped onto the last half hour of something starts when
 *    that is over and keeps its length if the day allows it.
 * 3. **In the next free stretch after it**, when its own span is covered whole.
 *
 * The journeys are never given up, and neither is the break once step 1 has
 * failed with it — only when no stretch holds the card *with* its break is the
 * search run again without. Null when nothing of [MIN_BLOCK_DURATION] is left
 * anywhere from where the card was put.
 *
 * Not snapped to the grid, for the reason `ConflictRelief` is not: what bounds a
 * free stretch may be an appointment out of a calendar, and rounding would
 * either throw minutes away or leave the two still overlapping.
 */
fun fittedSpan(existing: List<PlannedBlock>, wanted: PlacementSpan): PlacementSpan? {
    if (wanted.breakAfter != null) {
        val bare = wanted.copy(breakAfter = null)
        if (bare.collisions(existing).isEmpty()) return bare
    }
    return fitIntoGap(existing, wanted)
        ?: wanted.breakAfter?.let { fitIntoGap(existing, wanted.copy(breakAfter = null)) }
}

private fun fitIntoGap(existing: List<PlannedBlock>, wanted: PlacementSpan): PlacementSpan? {
    val lead = wanted.travelBefore?.inWholeMinutes?.toInt() ?: 0
    val tail = (wanted.returnAfter?.inWholeMinutes?.toInt() ?: 0) +
        (wanted.breakAfter?.inWholeMinutes?.toInt() ?: 0)
    val length = wanted.duration.inWholeMinutes.toInt()
    val whole = lead + length + tail
    val least = lead + MIN_BLOCK_DURATION.inWholeMinutes.toInt() + tail
    // The container as asked for, not as clamped: a journey that would begin
    // before midnight is still a journey the card has to be given room for.
    val from = wanted.startMinute - lead
    val until = from + whole

    val gaps = freeStretches(existing)

    /** What of [gap] the card can use, starting no earlier than it was put. */
    fun usable(gap: Pair<Int, Int>): Pair<Int, Int>? {
        val start = maxOf(gap.first, from, 0)
        val room = minOf(gap.second - start, whole)
        return (start to room).takeIf { room >= least }
    }

    val touched = gaps
        .filter { overlaps(it.first, it.second, from, until) }
        .mapNotNull(::usable)
        .maxByOrNull { it.second }
    val (start, room) = touched
        ?: gaps.filter { it.first >= until }.firstNotNullOfOrNull(::usable)
        ?: return null

    return wanted.copy(
        startMinute = start + lead,
        duration = (room - lead - tail).minutes,
    )
}

/** The stretches of the day nothing in [existing] holds, as half-open ranges. */
private fun freeStretches(existing: List<PlannedBlock>): List<Pair<Int, Int>> {
    val gaps = mutableListOf<Pair<Int, Int>>()
    var cursor = 0
    existing
        .map { it.containerStartMinute() to it.containerEndMinute() }
        .sortedBy { it.first }
        .forEach { (from, to) ->
            if (from > cursor) gaps += cursor to from
            cursor = maxOf(cursor, to)
        }
    if (cursor < MINUTES_PER_DAY) gaps += cursor to MINUTES_PER_DAY
    return gaps
}

/**
 * What "Plan anpassen" does to one block that is in the new card's way.
 *
 * Decided per block and shown before anything is written: the three are very
 * different things to have happen to a task, and one of them — [Removed] —
 * costs on a day already running.
 */
sealed interface PlanRelief {
    val block: PlannedBlock

    /** Cut at its end: it is over before the new card's claim begins. */
    data class EndsEarlier(override val block: PlannedBlock, val result: PlannedBlock) : PlanRelief

    /** Cut at its start: it begins once the new card's claim is over. */
    data class StartsLater(override val block: PlannedBlock, val result: PlannedBlock) : PlanRelief

    /** Nothing worth keeping is left of it on either side. */
    data class Removed(override val block: PlannedBlock) : PlanRelief

    /** An appointment out of the calendar: a fact, and it stays one. */
    data class Kept(override val block: PlannedBlock) : PlanRelief
}

/**
 * "Plan anpassen": the plan gives way, and the new card stands where it was put.
 *
 * The answers a calendar conflict has, chosen by the arithmetic instead of asked
 * one at a time — "je nachdem, was sich anbietet". A block reaching in from
 * before is cut at its end, one reaching out behind is cut at its start, and one
 * that does both keeps the longer side, the front on a tie. What would be left
 * under [MIN_BLOCK_DURATION] is not a shortened task but a destroyed one, so it
 * is [PlanRelief.Removed] and says so.
 *
 * An imported appointment is never touched. Its hour is Google's, and cutting it
 * would be the plan lying about the world; the overlap with it stays and is
 * reported as one.
 */
fun planRelief(existing: List<PlannedBlock>, wanted: PlacementSpan): List<PlanRelief> =
    wanted.collisions(existing).map { block ->
        if (block.origin == BlockOrigin.CALENDAR_IMPORT) return@map PlanRelief.Kept(block)

        val front = block.shortenedBefore(wanted.containerStart)
        val back = block.trimmedAfter(wanted.containerEnd)
        when {
            front != null && (back == null || front.plannedDuration >= back.plannedDuration) ->
                PlanRelief.EndsEarlier(block, front)

            back != null -> PlanRelief.StartsLater(block, back)
            else -> PlanRelief.Removed(block)
        }
    }

/**
 * The same activity, begun once a claim that ends at [claimEnd] is over — and
 * **ending when it always did**, so what is lost is its beginning.
 *
 * Unlike [startedAfter], which keeps the length and lets the end wander: there
 * an appointment pushed a task later into a day that was still being arranged.
 * Here a card was put on top of a plan, and everything behind the block is as
 * likely to be planned as not — the one thing known to be free is the block's
 * own remaining stretch.
 *
 * Null when the block does not reach past the claim, or when less than
 * [MIN_BLOCK_DURATION] would be left.
 */
fun PlannedBlock.trimmedAfter(claimEnd: Int): PlannedBlock? {
    if (containerEndMinute() <= claimEnd) return null

    val lead = travelBefore?.inWholeMinutes?.toInt() ?: 0
    val newStart = claimEnd + lead
    val minutes = endMinute() - newStart
    if (minutes < MIN_BLOCK_DURATION.inWholeMinutes) return null

    return copy(
        start = minuteToLocalTime(newStart),
        plannedDuration = minutes.minutes,
        actualDuration = null,
    )
}
