package com.example.eta.domain.planning

import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The least a block may be cut down to and still be a block.
 *
 * The planner's own grid: below one snap step a block is not really placeable,
 * cannot hold its own label, and "shortened" would mean "destroyed by another
 * name" — which is what the two other answers to a conflict are already for.
 */
val MIN_BLOCK_DURATION: Duration = SNAP_MINUTES.minutes

/**
 * Making room for a fixed appointment without giving the day up.
 *
 * A calendar entry cannot move — that is what makes it a fixture — so when it
 * lands on something already planned, the something has to give way. Calling it
 * off or carrying it into the week are the whole-activity answers. These two are
 * the partial ones, and they only exist because a partial overlap is the common
 * case: an appointment at noon does not usually cancel a morning, it shortens it.
 *
 * **Only the activity itself changes.** The journey there, the way back and the
 * break keep the lengths they had — they are what the task costs to reach and
 * what it earns afterwards, and neither of those becomes different because the
 * task got shorter. They move with it, because they always did: the container is
 * derived from the task's own start and end.
 */

/**
 * The same activity, cut so it is over before [fixed] begins.
 *
 * Null when there is nothing to keep: the block starts inside the appointment (so
 * there is no stretch in front of it to shorten *to*), or what is left would be
 * under [MIN_BLOCK_DURATION].
 *
 * The cut is measured against the appointment's **container**, not its start: an
 * appointment with a quarter of an hour of travel in front of it genuinely holds
 * that quarter of an hour, and shortening only as far as its start would leave
 * the two still overlapping — the user would have answered the question and
 * watched nothing happen. For the same reason the block's own tail is subtracted:
 * what has to clear the appointment is the whole container, so a task earning a
 * 25-minute break ends 25 minutes earlier than one that does not.
 *
 * Not snapped to the grid. The appointment's hour comes from a calendar, and
 * rounding it down would silently throw away the minutes between — the block ends
 * exactly where the appointment's claim begins.
 */
fun PlannedBlock.shortenedBefore(fixed: PlannedBlock): PlannedBlock? {
    if (containerStartMinute() >= fixed.containerStartMinute()) return null

    val tail = (returnAfter?.inWholeMinutes?.toInt() ?: 0) +
        (breakAfter?.inWholeMinutes?.toInt() ?: 0)
    val newEnd = fixed.containerStartMinute() - tail
    val minutes = newEnd - startMinute()
    if (minutes < MIN_BLOCK_DURATION.inWholeMinutes) return null

    return copy(
        plannedDuration = minutes.minutes,
        // The plan changed, so a correction recorded against the old one no
        // longer describes anything. In practice always null here — a block in
        // conflict with a future appointment has not been completed.
        actualDuration = null,
    )
}

/**
 * The same activity, started again once [fixed] is over.
 *
 * The mirror image, for a conflict that bites at the front: the appointment
 * covers the beginning of the block, so the block waits and runs its full length
 * afterwards. It keeps its duration — nothing about it got smaller, it merely
 * happens later.
 *
 * Null when there is nothing left behind the appointment (the block ends inside
 * it, so waiting would mean moving it wholesale rather than out of the way) or
 * when the day runs out. Whether the new position is free of *other* blocks is
 * not asked here — this function knows about two blocks — and the caller checks
 * it with [canPlace] before offering the move.
 */
fun PlannedBlock.startedAfter(fixed: PlannedBlock): PlannedBlock? {
    if (containerEndMinute() <= fixed.containerEndMinute()) return null

    val lead = travelBefore?.inWholeMinutes?.toInt() ?: 0
    val tail = (returnAfter?.inWholeMinutes?.toInt() ?: 0) +
        (breakAfter?.inWholeMinutes?.toInt() ?: 0)
    val length = effectiveDuration.inWholeMinutes.toInt()
    val newStart = fixed.containerEndMinute() + lead
    if (newStart + length + tail > MINUTES_PER_DAY) return null

    return copy(start = minuteToLocalTime(newStart))
}
