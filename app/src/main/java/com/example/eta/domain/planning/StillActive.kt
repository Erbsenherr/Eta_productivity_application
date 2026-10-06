package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate

/** A task has to be at least this long before it is worth asking whether it still has you. */
val STILL_ACTIVE_MIN_DURATION: Duration = 1.hours

/**
 * How far from either end of a task no question is asked.
 *
 * The start already announces itself, and so may the end; a question in the same
 * breath would only read as a second copy of those.
 */
const val STILL_ACTIVE_EDGE_MINUTES = 10

/** The range the settings offer. */
const val STILL_ACTIVE_MAX_PER_DAY = 20
const val DEFAULT_STILL_ACTIVE_PER_DAY = 3

/**
 * Whether a block is the kind of thing the question is about.
 *
 * An hour or more of something that is meant to hold attention. A break, free
 * time and a points entry (Custom Earn, Custom Spend, Social) are not — being
 * asked "are you still on it" during a break has the question backwards.
 *
 * Called-off blocks are not; ticked-off ones still *are*, so that ticking one off
 * does not reshuffle the whole day's questions. Whether a block is still open is
 * asked at the moment one rings instead.
 */
fun BlockWithItem.canAskStillActive(): Boolean =
    !block.isDiscarded &&
        block.effectiveDuration >= STILL_ACTIVE_MIN_DURATION &&
        item.type != ItemType.SPEND &&
        item.role != ItemRole.BREAK &&
        item.role != ItemRole.FREE_TIME

/**
 * The minutes of one day at which to ask, with the block each one falls in.
 *
 * Random, and deliberately **repeatable**: the alarm is re-aimed many times a day
 * — every edit, every ring — and a draw that came out differently each time
 * would never let a question arrive at all. Seeding by the date and the count
 * keeps the answer the same for as long as the day's plan is.
 *
 * Spread over the eligible minutes as one stretch, so a long task gets more of
 * the questions than a short one — "zufällig" across the working time rather
 * than evenly per task. No two land on the same minute; a day with fewer
 * eligible minutes than questions asks fewer.
 */
fun stillActiveMinutes(
    blocks: List<BlockWithItem>,
    date: LocalDate,
    perDay: Int,
): List<Pair<BlockWithItem, Int>> {
    if (perDay <= 0) return emptyList()

    // Each block contributes its task minus the two quiet edges.
    val windows = blocks
        .filter { it.block.date == date && it.canAskStillActive() }
        .sortedBy { it.block.startMinute() }
        .mapNotNull { entry ->
            val from = entry.block.startMinute() + STILL_ACTIVE_EDGE_MINUTES
            val to = entry.block.endMinute() - STILL_ACTIVE_EDGE_MINUTES
            if (to > from) Triple(entry, from, to) else null
        }
    val total = windows.sumOf { it.third - it.second }
    if (total <= 0) return emptyList()

    val random = Random(date.toEpochDays().toLong() * 1_000 + perDay)
    val offsets = sortedSetOf<Int>()
    val wanted = perDay.coerceAtMost(total)
    while (offsets.size < wanted) offsets += random.nextInt(total)

    return offsets.map { offset ->
        var remaining = offset
        val window = windows.first { (_, from, to) ->
            val length = to - from
            if (remaining < length) true else {
                remaining -= length
                false
            }
        }
        window.first to window.second + remaining
    }
}
