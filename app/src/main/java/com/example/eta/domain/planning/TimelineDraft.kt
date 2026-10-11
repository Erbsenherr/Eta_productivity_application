package com.example.eta.domain.planning

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The shortest a block can be pinched to: one step of the grid. */
const val MIN_BLOCK_MINUTES = SNAP_MINUTES

/** What a box held into an empty stretch starts out as. */
const val DEFAULT_DRAFT_MINUTES = 60

/**
 * A block that does not exist yet: the box a long press on an empty stretch
 * leaves behind, to be pushed and pinched into shape before anything is asked.
 *
 * Minutes rather than a time and a duration, because all that happens to it
 * until it is confirmed is arithmetic on a day.
 */
data class TimelineDraft(
    val startMinute: Int,
    val durationMinutes: Int = DEFAULT_DRAFT_MINUTES,
) {
    val endMinute: Int get() = startMinute + durationMinutes

    val duration: Duration get() = durationMinutes.minutes

    /** The same box starting at [minute], kept on the grid and inside the day. */
    fun movedTo(minute: Int): TimelineDraft =
        copy(startMinute = snapToGrid(minute).coerceIn(0, MINUTES_PER_DAY - durationMinutes))

    companion object {
        /**
         * The box for a press on [minute]: an hour from the grid line at or
         * before it, or what is left of the day when that is less.
         *
         * At or before, not the nearest: the finger is *in* the quarter of an
         * hour it means, and rounding up would start the box below it.
         */
        fun at(minute: Int): TimelineDraft {
            val start = ((minute.coerceIn(0, MINUTES_PER_DAY - 1) / SNAP_MINUTES) * SNAP_MINUTES)
                .coerceAtMost(MINUTES_PER_DAY - MIN_BLOCK_MINUTES)
            return TimelineDraft(start, DEFAULT_DRAFT_MINUTES.coerceAtMost(MINUTES_PER_DAY - start))
        }
    }
}

/**
 * Where a span stands after a pinch moved its two edges.
 *
 * Each edge follows its own finger — [topTravel] and [bottomTravel] are how far
 * the upper and the lower one have come, in minutes — and lands on the grid. An
 * edge whose finger has not moved half a step stays exactly where it was, so a
 * task that begins at five past keeps its five past while only its end is
 * pulled. The span never gets shorter than [MIN_BLOCK_MINUTES] and never leaves
 * the day; squeezed past that, the edge that travelled further is the one that
 * stops.
 *
 * Returns start and end, end exclusive.
 */
fun pinchedSpan(
    startMinute: Int,
    endMinute: Int,
    topTravel: Float,
    bottomTravel: Float,
): Pair<Int, Int> {
    fun edge(at: Int, travel: Float): Int =
        if (abs(travel) < SNAP_MINUTES / 2f) at else snapToGrid((at + travel).roundToInt())

    var from = edge(startMinute, topTravel).coerceIn(0, MINUTES_PER_DAY - MIN_BLOCK_MINUTES)
    var to = edge(endMinute, bottomTravel).coerceIn(MIN_BLOCK_MINUTES, MINUTES_PER_DAY)
    if (to - from < MIN_BLOCK_MINUTES) {
        if (abs(topTravel) >= abs(bottomTravel)) {
            from = (to - MIN_BLOCK_MINUTES).coerceAtLeast(0)
            to = from + MIN_BLOCK_MINUTES
        } else {
            to = (from + MIN_BLOCK_MINUTES).coerceAtMost(MINUTES_PER_DAY)
            from = to - MIN_BLOCK_MINUTES
        }
    }
    return from to to
}
