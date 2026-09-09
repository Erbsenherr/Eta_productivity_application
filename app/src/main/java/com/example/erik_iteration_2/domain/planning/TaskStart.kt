package com.example.erik_iteration_2.domain.planning

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.PlannedBlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * How far either side of the intended minute a block still counts as "this one".
 *
 * Not slack for a late alarm — the minute an alarm was laid down for travels with
 * it, so lateness is already answered. This only absorbs the rounding between an
 * epoch millisecond and a wall-clock minute.
 */
val SAME_MINUTE: Duration = 1.minutes

/**
 * When a block begins, as a point on the absolute timeline.
 *
 * A block stores wall-clock time on purpose — "07:00 on that day", not an
 * instant — so anything that has to *ring* has to make the conversion, and it can
 * only be made against a time zone.
 */
fun PlannedBlock.startsAt(timeZone: TimeZone): Instant =
    date.atTime(start).toInstant(timeZone)

/**
 * When its planned time runs out. Start plus what the plan allots, so a duration
 * corrected on the dashboard moves the announcement with it.
 */
fun PlannedBlock.endsAt(timeZone: TimeZone): Instant =
    startsAt(timeZone) + effectiveDuration

/** The two things a block can announce about itself. */
enum class TaskEventKind { START, END }

/** One announcement waiting to be made. */
data class TaskEvent(
    val entry: BlockWithItem,
    val kind: TaskEventKind,
    val at: Instant,
)

/**
 * Every announcement a day's blocks still owe.
 *
 * Only **open** blocks: one already ticked off or dropped has been dealt with,
 * and announcing it would be telling the user something they have already told
 * the app. The end is offered only where the definition asked for it — that is
 * `Item.endSound`, off for everything that predates the switch.
 */
private fun eventsOf(blocks: List<BlockWithItem>, timeZone: TimeZone): List<TaskEvent> =
    blocks.filter { it.block.isOpen }.flatMap { entry ->
        buildList {
            add(TaskEvent(entry, TaskEventKind.START, entry.block.startsAt(timeZone)))
            if (entry.item.endSound) {
                add(TaskEvent(entry, TaskEventKind.END, entry.block.endsAt(timeZone)))
            }
        }
    }

/**
 * The next minute at which something on the plan begins, after [now].
 *
 * Null means nothing is left to announce, which is the signal to cancel the alarm
 * rather than to guess at a next one.
 */
fun nextTaskStart(
    blocks: List<BlockWithItem>,
    now: Instant,
    timeZone: TimeZone,
): Instant? = blocks
    .filter { it.block.isOpen }
    .map { it.block.startsAt(timeZone) }
    .filter { it > now }
    .minOrNull()

/**
 * The next minute at which *anything* is to be announced — a start or an end,
 * whichever comes first.
 *
 * One alarm covers both, which is the property that made the start alarm cheap:
 * a day of ten blocks is still one pending intent, however many things it has to
 * say. Splitting them would mean keeping two schedules in step with every edit.
 */
fun nextTaskEvent(
    blocks: List<BlockWithItem>,
    now: Instant,
    timeZone: TimeZone,
): Instant? = eventsOf(blocks, timeZone)
    .map { it.at }
    .filter { it > now }
    .minOrNull()

/**
 * What was meant to begin at [at].
 *
 * A list, not one block: two things starting on the same minute is ordinary, and
 * announcing only one of them would be arbitrary about which.
 */
fun blocksStartingAt(
    blocks: List<BlockWithItem>,
    at: Instant,
    timeZone: TimeZone,
): List<BlockWithItem> = blocks
    .filter { it.block.isOpen }
    .filter { (it.block.startsAt(timeZone) - at).absoluteValue <= SAME_MINUTE }

/**
 * Everything to be announced at [at] — matched against the minute the alarm was
 * *laid down for*, never against the clock, so a late delivery still says the
 * right thing rather than the nearest thing.
 *
 * A block whose start and end fall on the same minute cannot happen: a duration
 * is at least a quarter of an hour.
 */
fun eventsAt(
    blocks: List<BlockWithItem>,
    at: Instant,
    timeZone: TimeZone,
): List<TaskEvent> = eventsOf(blocks, timeZone)
    .filter { (it.at - at).absoluteValue <= SAME_MINUTE }
