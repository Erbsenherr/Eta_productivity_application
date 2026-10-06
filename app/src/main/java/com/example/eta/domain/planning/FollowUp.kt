package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The break the follow-up question offers to slip in before the next task. */
val SPONTANEOUS_BREAK: Duration = 15.minutes

/**
 * Whether ticking [confirmed] off is a statement about the past rather than about
 * what is happening now.
 *
 * The rule confirmed with the user: if a **later block has already begun**, the day
 * has moved on and the question "what about the next one" has nothing to do with
 * this tick. Ticking something off punctually — or a few minutes late — still asks.
 * A fixed grace period would have been arbitrary and would break on a day with long
 * gaps in it.
 */
fun confirmedRetroactively(
    blocks: List<BlockWithItem>,
    confirmed: PlannedBlock,
    minuteOfDay: Int,
): Boolean = blocks.any {
    it.block.id != confirmed.id &&
        it.occupiesTime() &&
        it.block.startMinute() > confirmed.startMinute() &&
        it.block.startMinute() <= minuteOfDay
}

/**
 * The block the follow-up question is about: the next one still ahead.
 *
 * Measured from **now**, not from the plan's order — the point of the question is
 * what to do with the time that has just come free, and that is decided by the
 * clock. Only blocks still open and still claiming their slot count.
 */
fun nextAfterNow(blocks: List<BlockWithItem>, minuteOfDay: Int): BlockWithItem? = blocks
    .filter { it.block.isOpen && it.claimsItsSlot() }
    .filter { it.block.containerStartMinute() > minuteOfDay }
    .minByOrNull { it.block.containerStartMinute() }

/** Where a pulled-forward task lands, and the break in front of it if there is one. */
data class PullForward(
    /** The minute the inserted break starts at, or null when none was asked for. */
    val breakStart: Int?,
    /** The task's own new start. Its journey reaches back before this. */
    val taskStart: Int,
)

/**
 * Pulls one task forward to [fromMinute] — as far as it fits, and no further.
 *
 * Only the next task moves, which is what the user chose: the rest of the day stays
 * where it is. So the task is placed at the earliest minute from [fromMinute] on
 * that holds its whole container without running into anything, and if that is no
 * earlier than where it already stands, there is nothing to gain and the answer is
 * null — the plan is left alone rather than rewritten to look the same.
 *
 * With [withBreak] the break is laid down first, starting at [fromMinute], and the
 * task follows it. If the break itself does not fit, the whole answer is null: the
 * user asked for a break and then the task, and quietly giving them only the task
 * would be answering a different question.
 *
 * Nothing here is snapped to the grid — see `firstFreeStart`'s `snap`.
 */
fun pullForward(
    obstacles: List<PlannedBlock>,
    block: PlannedBlock,
    fromMinute: Int,
    withBreak: Duration? = null,
): PullForward? {
    val others = obstacles.filterNot { it.id == block.id }
    val breakMinutes = withBreak?.inWholeMinutes?.toInt() ?: 0
    if (breakMinutes > 0) {
        val until = fromMinute + breakMinutes
        val blocked = others.any {
            overlaps(fromMinute, until, it.containerStartMinute(), it.containerEndMinute())
        }
        if (blocked || until >= MINUTES_PER_DAY) return null
    }

    val lead = block.travelBefore?.inWholeMinutes?.toInt() ?: 0
    val start = firstFreeStart(
        others,
        block.effectiveDuration,
        fromMinute + breakMinutes + lead,
        leadIn = block.travelBefore,
        tailOut = block.returnAfter,
        tailOutExtra = block.breakAfter,
        snap = false,
    ) ?: return null
    if (start >= block.startMinute()) return null

    return PullForward(breakStart = if (breakMinutes > 0) fromMinute else null, taskStart = start)
}

/**
 * What a task finished ahead of its planned end is billed at.
 *
 * Finishing early is not to be punished, so the plan's full length may still be
 * billed — but **at most twice the time actually used**. An hour's task done in
 * twenty minutes is billed forty; the last twenty lapse. Done in forty, it is
 * billed the whole hour. Without the cap, ticking everything off in its first
 * minute would pay the same as doing it.
 */
data class EarlyBilling(
    /** What the block would be billed at had it run its course. */
    val planned: Duration,
    /** From the block's start to the tick. */
    val used: Duration,
) {
    /** "Voll abrechnen": the plan's length, capped at twice the time used. */
    val full: Duration get() = minOf(planned, used * 2)

    val isCapped: Boolean get() = full < planned
}

/**
 * The billing question for ticking [block] off at [minuteOfDay], or null when the
 * tick is not early: before the block began there is no time used to measure, and
 * from its planned end on there is nothing left to give away.
 *
 * Measured against `effectiveDuration`, so a length the user already corrected by
 * hand is what "the whole task" means.
 */
fun earlyBilling(block: PlannedBlock, minuteOfDay: Int): EarlyBilling? {
    val planned = block.effectiveDuration
    val used = minuteOfDay - block.startMinute()
    if (used < 0 || used >= planned.inWholeMinutes) return null
    return EarlyBilling(planned = planned, used = used.minutes)
}
