package com.example.erik_iteration_2.domain.planning

import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.setup.UserSetup
import kotlin.time.Duration
import kotlinx.datetime.LocalTime

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

/** Half-open ranges, so a block ending at 10:00 and one starting there do not clash. */
fun overlaps(fromA: Int, toA: Int, fromB: Int, toB: Int): Boolean = fromA < toB && fromB < toA

fun PlannedBlock.overlaps(other: PlannedBlock): Boolean =
    overlaps(startMinute(), endMinute(), other.startMinute(), other.endMinute())

fun snapToGrid(minute: Int, step: Int = SNAP_MINUTES): Int =
    ((minute + step / 2) / step) * step

/**
 * Whether [candidate] can go where it wants to.
 *
 * Blocks the day already holds are the obstacles; the candidate is compared
 * against everything except itself, so moving a block around does not collide
 * with where it used to be. Completed and discarded occurrences still count —
 * that time was spent either way.
 */
fun canPlace(existing: List<PlannedBlock>, candidate: PlannedBlock): Boolean =
    existing.none { it.id != candidate.id && it.overlaps(candidate) }

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
): Int? {
    val length = duration.inWholeMinutes.toInt()
    if (length <= 0) return null

    val obstacles = existing
        .filter { it.id != ignoreId }
        .map { it.startMinute() to it.endMinute() }
        .sortedBy { it.first }

    var candidate = snapToGrid(preferredStart.coerceAtLeast(0))
    while (candidate + length <= MINUTES_PER_DAY) {
        val blocking = obstacles.firstOrNull { (from, to) ->
            overlaps(candidate, candidate + length, from, to)
        }
        if (blocking == null) return candidate
        // Jump to just after whatever was in the way, still on the grid.
        candidate = snapToGrid(blocking.second)
        if (candidate < blocking.second) candidate += SNAP_MINUTES
    }
    return null
}

/**
 * The night as it falls on one calendar day, in minutes from midnight.
 *
 * A night that crosses midnight shows up as two stretches — the tail of the
 * previous one in the morning, the start of this one in the evening. Going to bed
 * after midnight is the other case and needs only one. The planner shades these
 * rather than drawing them as blocks: sleep is configuration, not something to
 * check off.
 */
fun UserSetup.sleepStretches(): List<IntRange> {
    val sleepStart = sleepTime.minuteOfDay()
    val wake = wakeTime.minuteOfDay()

    return if (sleepStart > wake) {
        buildList {
            if (wake > 0) add(0 until wake)
            if (sleepStart < MINUTES_PER_DAY) add(sleepStart until MINUTES_PER_DAY)
        }
    } else {
        listOfNotNull((sleepStart until wake).takeIf { !it.isEmpty() })
    }
}
