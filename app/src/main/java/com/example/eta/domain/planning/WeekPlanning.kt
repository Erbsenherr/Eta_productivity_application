package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.sleepMinutesPerWeek
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The break an hour of work earns. */
val SHORT_BREAK: Duration = 15.minutes

/** The break anything from an hour and a half upwards earns. */
val LONG_BREAK: Duration = 25.minutes

/** Days in the week the planning phase lays out. */
const val DAYS_PER_WEEK = 7

/**
 * The break `Planungsphase.md` books alongside an activity.
 *
 * Read as two tiers rather than as a rate: an hour earns 15 minutes, an hour and
 * a half or more earns 25. The alternative reading — 25 minutes for *every* 1.5
 * hours — would make a long task cost far more than the sentence next to it
 * implies, so this is the conservative one. Flagged as an assumption.
 */
fun breakFor(duration: Duration): Duration = when {
    duration >= 90.minutes -> LONG_BREAK
    duration >= 60.minutes -> SHORT_BREAK
    else -> Duration.ZERO
}

/** What planning an activity really costs: the activity plus the break it earns. */
fun costOf(duration: Duration): Duration = duration + breakFor(duration)

/**
 * The week's time budget as the planning screen shows it.
 *
 * [committedMinutes] is the standing schedule as it actually exists — the blocks
 * already materialized for those days — rather than what the setup answers imply.
 * The two agree for a fresh setup, but a recurring task created afterwards only
 * shows up in the blocks, and a week that ignored it would be lying.
 */
data class WeekBudget(
    val availableMinutes: Int,
    val committedMinutes: Int,
    val plannedMinutes: Int,
    /** How much of the week is free time. Zero is worth saying out loud. */
    val freeTimeMinutes: Int = 0,
) {
    /**
     * A week with no free time in it at all.
     *
     * Deliberately having none is allowed — the concept pays for giving it up —
     * but it should be a decision rather than an oversight, so the screen says so
     * instead of quietly accepting it.
     */
    val withoutFreeTime: Boolean get() = freeTimeMinutes == 0

    val freeMinutes: Int get() = (availableMinutes - committedMinutes - plannedMinutes).coerceAtLeast(0)

    /** True once the week list asks for more than the week has left. */
    val overbooked: Boolean get() = availableMinutes - committedMinutes - plannedMinutes < 0
}

/** Occupied minutes across several days, overlaps within a day counted once. */
fun occupiedMinutesOfWeek(blocks: List<BlockWithItem>): Int =
    blocks.groupBy { it.block.date }.values.sumOf { mergedMinutes(it) }

private fun mergedMinutes(blocks: List<BlockWithItem>): Int {
    val spans = blocks
        // Container spans, so a task that brings a journey and a break with it
        // costs the week what it actually costs.
        .map { it.block.containerStartMinute() to it.block.containerEndMinute() }
        .filter { it.second > it.first }
        .sortedBy { it.first }
    if (spans.isEmpty()) return 0

    var total = 0
    var (from, to) = spans.first()
    for ((start, end) in spans.drop(1)) {
        if (start > to) {
            total += to - from
            from = start
            to = end
        } else {
            to = maxOf(to, end)
        }
    }
    return total + (to - from)
}

/**
 * What the week has left.
 *
 * Sleep and the social budget come out first, as `Planungsphase.md` asks — social
 * time has no fixed hour, so it can only be reserved as a lump.
 */
fun weekBudget(
    setup: UserSetup?,
    blocks: List<BlockWithItem>,
    weekList: List<Item>,
): WeekBudget {
    val wholeWeek = DAYS_PER_WEEK * MINUTES_PER_DAY
    // Night by night: a weekend that sleeps longer has fewer hours to give.
    val sleep = setup?.sleepMinutesPerWeek() ?: 0
    val social = setup?.socialTimePerWeek?.inWholeMinutes?.toInt() ?: 0

    return WeekBudget(
        availableMinutes = (wholeWeek - sleep - social).coerceAtLeast(0),
        committedMinutes = occupiedMinutesOfWeek(blocks),
        plannedMinutes = weekList.sumOf {
            costOf(it.estimatedDuration ?: Duration.ZERO).inWholeMinutes.toInt()
        },
        freeTimeMinutes = blocks
            .filter { it.item.role == ItemRole.FREE_TIME }
            .sumOf { it.block.effectiveDuration.inWholeMinutes.toInt() },
    )
}
