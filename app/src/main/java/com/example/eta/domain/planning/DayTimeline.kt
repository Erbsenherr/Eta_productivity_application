package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.nightEndingOn
import com.example.eta.domain.setup.shifted
import kotlin.time.Duration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

const val MINUTES_PER_DAY = 24 * 60

/** The grid the planner snaps to. Fine enough to be useful, coarse enough to hit. */
const val SNAP_MINUTES = 15

fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

fun minuteToLocalTime(minute: Int): LocalTime {
    val clamped = minute.coerceIn(0, MINUTES_PER_DAY - 1)
    return LocalTime(clamped / 60, clamped % 60)
}

/** Where a block starts, in minutes from midnight. */
fun PlannedBlock.startMinute(): Int = start.minuteOfDay()

/**
 * Where it ends. Clamped to midnight: the planner draws one day, and a block that
 * would run past it is shown ending at the edge rather than wrapping into a day
 * the user is not looking at.
 */
fun PlannedBlock.endMinute(): Int =
    (startMinute() + effectiveDuration.inWholeMinutes.toInt()).coerceAtMost(MINUTES_PER_DAY)

/**
 * Where the whole thing starts and ends, journeys and break included.
 *
 * Two spans, not one, because they answer different questions. [startMinute] and
 * [endMinute] are the task, and are what points are billed for. The **container**
 * is what the task *occupies*, and is what every question about time is asked —
 * overlap, placement, and how much of a day carries a plan. Clamped to the day at
 * both ends: the planner draws one day, and a margin that would run off it is
 * shown ending at the edge.
 */
fun PlannedBlock.containerStartMinute(): Int = containerStartMinute(startMinute(), travelBefore)

fun PlannedBlock.containerEndMinute(): Int = containerEndMinute(endMinute(), returnAfter, breakAfter)

/**
 * The same arithmetic without a block, for whatever claims time the same way —
 * a standing task's slot is asked the identical question before any block of
 * it exists, and must not be able to answer it differently.
 */
fun containerStartMinute(startMinute: Int, travelBefore: Duration?): Int =
    (startMinute - (travelBefore?.inWholeMinutes?.toInt() ?: 0)).coerceAtLeast(0)

fun containerEndMinute(endMinute: Int, returnAfter: Duration?, breakAfter: Duration?): Int {
    // The return journey first, then the break: you come back, and then you rest.
    val tail = (returnAfter?.inWholeMinutes?.toInt() ?: 0) +
        (breakAfter?.inWholeMinutes?.toInt() ?: 0)
    return (endMinute + tail).coerceAtMost(MINUTES_PER_DAY)
}

/** The minute the return journey begins, which is the moment the task is over. */
fun PlannedBlock.returnStartMinute(): Int = endMinute()

/** The minute the break begins: after the task, and after the way back from it. */
fun PlannedBlock.breakStartMinute(): Int =
    endMinute() + (returnAfter?.inWholeMinutes?.toInt() ?: 0)

/** Half-open ranges, so a block ending at 10:00 and one starting there do not clash. */
fun overlaps(fromA: Int, toA: Int, fromB: Int, toB: Int): Boolean = fromA < toB && fromB < toA

/**
 * Whether two blocks want the same minutes.
 *
 * Compared as containers: a task with a quarter of an hour of travel in front of
 * it genuinely holds that quarter of an hour, and something else planned into it
 * would be planned into a journey already under way.
 */
fun PlannedBlock.overlaps(other: PlannedBlock): Boolean =
    overlaps(
        containerStartMinute(),
        containerEndMinute(),
        other.containerStartMinute(),
        other.containerEndMinute(),
    )

fun snapToGrid(minute: Int, step: Int = SNAP_MINUTES): Int =
    ((minute + step / 2) / step) * step

/** The next grid line at or after [minute] — what a lead-in has to clear. */
private fun ceilToGrid(minute: Int, step: Int = SNAP_MINUTES): Int =
    ((minute + step - 1) / step) * step

/**
 * Whether [candidate] can go where it wants to.
 *
 * Blocks the day already holds are the obstacles; the candidate is compared
 * against everything except itself, so moving a block around does not collide
 * with where it used to be. Two kinds of block are **not** obstacles: a cancelled
 * one, because calling something off is precisely how its hours come back, and a
 * **completed** one, because a task that is over releases the stretch it was
 * given. Filtering both out is the caller's job, through
 * [BlockWithItem.claimsItsSlot] — this function only sees blocks, and whether a
 * discarded one still holds its slot depends on the item behind it.
 */
fun canPlace(existing: List<PlannedBlock>, candidate: PlannedBlock): Boolean =
    existing.none { it.id != candidate.id && it.overlaps(candidate) }

/**
 * Whether [duration] plus its margins fits anywhere at all from [preferredStart].
 *
 * The question the break prompt asks twice: once with the break and once without
 * it. The journey is never dropped — it is the time the task takes to reach, and
 * a task placed without it is simply planned wrong — so only the tail moves
 * between the two calls.
 */
fun fitsSomewhere(
    existing: List<PlannedBlock>,
    duration: Duration,
    preferredStart: Int,
    ignoreId: String? = null,
    leadIn: Duration? = null,
    tailOut: Duration? = null,
    tailOutExtra: Duration? = null,
): Boolean =
    firstFreeStart(existing, duration, preferredStart, ignoreId, leadIn, tailOut, tailOutExtra) != null

/**
 * Whether a called-off occurrence still holds the hours it was given.
 *
 * It does not — that is the point of calling something off — with one exception
 * the reward rules already own. Giving up an [ItemRole.FREE_TIME] block is a
 * **forfeit**, priced at four points an hour in the user's favour; taking its
 * hours away as well would turn that payout into something smaller by a side
 * door, and it would be charged for the cancellation on top. So free time keeps
 * its slot however it ends, exactly as `heldItsTime` always had it.
 */
fun BlockWithItem.isCancelled(): Boolean =
    block.isDiscarded && item.role != ItemRole.FREE_TIME

/**
 * Whether the calling-off happened *before* the day it belongs to.
 *
 * Deciding on Monday evening that Tuesday's Sport is not going to happen is
 * planning, not giving up: the hours go back into a day that has not started
 * yet and can still be filled with something else. Only a day that was already
 * confirmed and is being lived can lose a promise, and that is the one the
 * charge is for — see [DailySettlement].
 *
 * Read off `discardedAt` rather than stored, because the two dates are already
 * there and a column would only be a second thing to keep in step with them.
 */
fun PlannedBlock.cancelledInAdvance(timeZone: TimeZone): Boolean {
    val at = discardedAt ?: return false
    return at.toLocalDateTime(timeZone).date < date
}

/**
 * Whether this cancellation is one the evening actually charges for.
 *
 * Three ways out of it, and they are different statements: free time keeps its
 * slot and is priced the other way round ([isCancelled]), an excused one was
 * nobody's doing (`forceMajeure`), and one decided in advance never cost the day
 * anything ([cancelledInAdvance]).
 */
fun BlockWithItem.cancellationCharged(timeZone: TimeZone): Boolean =
    isCancelled() && !block.isExcused && !block.cancelledInAdvance(timeZone)

/**
 * Whether the day still counts these minutes as carried by the plan.
 *
 * This is the **accounting** question, and the settlement is what asks it: a
 * completed block carried its hours, a cancellation gave them back. It is not
 * the same as whether the block is still in the way — see [claimsItsSlot].
 */
fun BlockWithItem.occupiesTime(): Boolean = !isCancelled()

/**
 * Whether the block still holds its place in the plan.
 *
 * The **placement** question, and a narrower one: a task that has been ticked off
 * is over, so the stretch it was given — journey, task, journey back and break
 * together — is free again and something else may be planned into it. The user
 * asked for exactly that, and the day planner shades the freed stretch green
 * rather than leaving the finished block standing in the way of the afternoon.
 *
 * It costs nothing to leave that stretch empty: the settlement asks
 * [occupiesTime] and [heldItsTime], and both still count a completed block's
 * hours, so the unplanned-time charge never sees the gap. Two questions rather
 * than one, because the honest answers differ — the time was spent *and* the slot
 * is free.
 *
 * A cancellation leaves the plan for the other reason and is not drawn at all;
 * free time keeps its slot however it ends, which [isCancelled] already says.
 */
fun BlockWithItem.claimsItsSlot(): Boolean = occupiesTime() && !block.isCompleted

/** The blocks a placement has to work around. */
fun List<BlockWithItem>.standing(): List<PlannedBlock> =
    filter { it.claimsItsSlot() }.map { it.block }

/**
 * Every block drawn over [minute], soonest first.
 *
 * What a long press in the planner has actually landed on. Blocks are drawn full
 * width at their own minute, so two that share an hour are stacked, and only the
 * last one drawn can ever receive a gesture — which meant the one underneath
 * could not be opened at all. The screen asks which of these the user meant when
 * there is more than one.
 *
 * The **task's** span, not the container: the frame around a journey carries no
 * gestures, so what is under the finger is the box between [startMinute] and
 * [endMinute]. A block of no length covers the minute it sits on, or a task
 * ticked off with no duration would be unreachable.
 */
fun List<BlockWithItem>.coveringMinute(minute: Int): List<BlockWithItem> =
    filter { minute >= it.block.startMinute() && minute < maxOf(it.block.endMinute(), it.block.startMinute() + 1) }
        .sortedBy { it.block.startMinute() }

/** What [candidate] would run into. Empty when it fits. */
fun collisionsFor(existing: List<PlannedBlock>, candidate: PlannedBlock): List<PlannedBlock> =
    existing.filter { it.id != candidate.id && it.overlaps(candidate) }

/**
 * The first start at or after [preferredStart] where [duration] fits, or null if
 * the rest of the day is full.
 *
 * Used when a block is dropped onto occupied time: rather than refusing the drop
 * or silently overwriting, the planner offers the next place it would fit.
 */
fun firstFreeStart(
    existing: List<PlannedBlock>,
    duration: Duration,
    preferredStart: Int,
    ignoreId: String? = null,
    leadIn: Duration? = null,
    tailOut: Duration? = null,
    tailOutExtra: Duration? = null,
    /**
     * Whether the answer is put on the quarter-hour grid.
     *
     * A drop is snapped, because the user is aiming with a finger. Pulling a task
     * forward to *now* is not: the minute is read off the clock, and rounding it
     * would either throw minutes away or start the task before the break it is
     * supposed to follow — the same reasoning that keeps `ConflictRelief` unsnapped.
     */
    snap: Boolean = true,
): Int? {
    val length = duration.inWholeMinutes.toInt()
    if (length <= 0) return null

    val lead = leadIn?.inWholeMinutes?.toInt() ?: 0
    val tail = (tailOut?.inWholeMinutes?.toInt() ?: 0) +
        (tailOutExtra?.inWholeMinutes?.toInt() ?: 0)

    val obstacles = existing
        .filter { it.id != ignoreId }
        .map { it.containerStartMinute() to it.containerEndMinute() }
        .sortedBy { it.first }

    // The returned minute is the **task's** start; the journey reaches back
    // before it, so the earliest possible one is the first grid line that leaves
    // room for it.
    var candidate = if (snap) {
        maxOf(snapToGrid(preferredStart.coerceAtLeast(0)), ceilToGrid(lead))
    } else {
        maxOf(preferredStart.coerceAtLeast(0), lead)
    }
    while (candidate + length + tail <= MINUTES_PER_DAY) {
        val blocking = obstacles.firstOrNull { (from, to) ->
            overlaps(candidate - lead, candidate + length + tail, from, to)
        }
        if (blocking == null) return candidate
        // Jump to just after whatever was in the way — and far enough that the
        // journey clears it too, still on the grid where the caller wants that.
        candidate = if (snap) snapToGrid(blocking.second + lead) else blocking.second + lead
        if (snap && candidate < blocking.second + lead) candidate += SNAP_MINUTES
    }
    return null
}

/**
 * The night as it falls on one [weekday], in minutes from midnight.
 *
 * A night that crosses midnight shows up as two stretches — the tail of the
 * previous one in the morning, the start of this one in the evening. Going to bed
 * after midnight is the other case and needs only one. The planner shades these
 * rather than drawing them as blocks: sleep is configuration, not something to
 * check off.
 *
 * Per weekday because the weekend may have a night of its own: the two stretches
 * of a Friday then belong to two different nights — the ordinary one it wakes
 * from and the weekend one it goes into.
 */
fun UserSetup.sleepStretches(weekday: DayOfWeek): List<IntRange> {
    val endingToday = nightEndingOn(weekday)
    val endingTomorrow = nightEndingOn(weekday.shifted(1))

    return listOf(
        endingToday.sleepOffset() until endingToday.wake.minuteOfDay(),
        (MINUTES_PER_DAY + endingTomorrow.sleepOffset()) until
            (MINUTES_PER_DAY + endingTomorrow.wake.minuteOfDay()),
    ).mapNotNull { night ->
        val from = night.first.coerceAtLeast(0)
        val until = (night.last + 1).coerceAtMost(MINUTES_PER_DAY)
        (from until until).takeIf { !it.isEmpty() }
    }
}
