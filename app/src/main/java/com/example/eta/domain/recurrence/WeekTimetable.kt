package com.example.eta.domain.recurrence

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.setup.WEEK
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus

/** The Monday of the week [date] lies in — the week the timetable shows as "Aktuell". */
fun mondayOf(date: LocalDate): LocalDate = date.minus(DatePeriod(days = WEEK.indexOf(date.dayOfWeek)))

/**
 * The standing week on one weekday, as the blocks it would lay down.
 *
 * What "Generell" in the week timetable draws. No occurrence exists for it —
 * it is the definitions themselves, read as a day — so each block is made up
 * here and wears the **definition's id**: whatever is done to it on screen is
 * done to the task, and the id is how the screen says which one.
 *
 * A fortnightly or monthly task stands on its weekday every week, as in the
 * Wochenschema: this is the week things are placed against, and a slot taken
 * every other week is not a free one.
 *
 * [unlocked] is carried as `flexible`, which is what gives a block the rounded
 * corners of something that may be dragged.
 */
fun standingBlocksOn(
    day: DayOfWeek,
    date: LocalDate,
    definitions: List<Item>,
    unlocked: Boolean = false,
): List<BlockWithItem> =
    definitions
        .filter { it.completedAt == null && it.isConcretized }
        .filter { it.stage != Stage.COLLECTION && it.stage != Stage.LOCKED }
        .filter { day in it.recurrenceRule?.weekdaysOf().orEmpty() }
        .mapNotNull { item ->
            val slot = RecurringSlot.of(item) ?: return@mapNotNull null
            BlockWithItem(
                block = PlannedBlock(
                    id = item.id,
                    itemId = item.id,
                    date = date,
                    start = slot.startTime,
                    plannedDuration = slot.duration,
                    origin = BlockOrigin.RECURRING,
                    travelBefore = slot.travelBefore,
                    returnAfter = slot.returnAfter,
                    breakAfter = slot.breakAfter,
                    flexible = unlocked,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt,
                ),
                item = item,
            )
        }
        .sortedBy { it.block.start }

/** What [rescheduleRows] answers: the rows to write, and which of them are copies of which. */
data class RescheduledRows(
    val rows: List<Item>,
    /** New row id → the row it was split off from, whose steps it has to be given. */
    val copiedFrom: Map<String, String> = emptyMap(),
)

/**
 * A standing task given a new hour and length on **some** of its weekdays — what
 * a drag or a pinch in the week timetable asks for.
 *
 * The model stores one definition per weekday for exactly this, so that moving
 * the Tuesday does not drag the Thursday along: a row whose weekday is among
 * [days] is rewritten, one that is not is left out of the answer altogether.
 * Only a **daily** row stands on several days at once. Moved on all seven it
 * stays daily; moved in part, it is split into one weekly row per weekday — the
 * row itself keeps a day that stays where it was, and with it its history, and
 * the other six are copies of it under ids from [newId].
 */
fun rescheduleRows(
    existing: List<Item>,
    days: Set<DayOfWeek>,
    startTime: LocalTime,
    duration: Duration,
    now: Instant,
    newId: () -> String,
): RescheduledRows {
    val rows = mutableListOf<Item>()
    val copiedFrom = mutableMapOf<String, String>()

    existing.filter { it.completedAt == null }.forEach { row ->
        val own = row.recurrenceRule?.weekdaysOf() ?: return@forEach
        val moved = own intersect days
        if (moved.isEmpty()) return@forEach
        if (moved == own) {
            rows += row.copy(startTime = startTime, estimatedDuration = duration, updatedAt = now)
            return@forEach
        }
        val keeper = WEEK.first { it in own && it !in days }
        WEEK.filter { it in own }.forEach { day ->
            val base = if (day == keeper) {
                row
            } else {
                row.copy(id = newId(), createdAt = now).also { copiedFrom[it.id] = row.id }
            }
            rows += base.copy(
                recurrenceRule = RecurrenceRule.Weekly(day),
                startTime = if (day in days) startTime else row.startTime,
                estimatedDuration = if (day in days) duration else row.estimatedDuration,
                updatedAt = now,
            )
        }
    }
    return RescheduledRows(rows, copiedFrom)
}

/**
 * What a standing task would collide with on each of [days], were it moved to
 * [startTime] and made [duration] long there.
 *
 * Asked before a move that reaches further than the day it was made on: the
 * user dragged Tuesday and can see Tuesday, but "all days" also moves a
 * Thursday nobody is looking at. Only days that do collide are in the answer.
 *
 * The same comparison the overlap warning in the form makes — containers, the
 * rhythm kept, so a fortnightly task is not told about one in the other week —
 * and [group] itself is left out of it, or the task would collide with where it
 * stands now.
 */
fun rescheduleClashes(
    group: List<Item>,
    days: Set<DayOfWeek>,
    startTime: LocalTime,
    duration: Duration,
    definitions: List<Item>,
): Map<DayOfWeek, List<Item>> {
    val ids = group.map { it.id }.toSet()
    return WEEK.filter { it in days }.mapNotNull { day ->
        val row = group.firstOrNull { day in it.recurrenceRule?.weekdaysOf().orEmpty() }
            ?: return@mapNotNull null
        val rule = Rhythm.of(row.recurrenceRule ?: return@mapNotNull null).rulesFor(setOf(day)).firstOrNull()
            ?: return@mapNotNull null
        val slot = RecurringSlot(rule, startTime, duration, row.travelBefore, row.returnAfter, row.breakAfter)
        val others = recurringOverlaps(listOf(slot), definitions, ignoreIds = ids).map { it.other }
        if (others.isEmpty()) null else day to others
    }.toMap()
}
