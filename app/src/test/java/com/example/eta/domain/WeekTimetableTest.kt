package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.TimelineDraft
import com.example.eta.domain.planning.pinchedSpan
import com.example.eta.domain.recurrence.groupRecurring
import com.example.eta.domain.recurrence.mondayOf
import com.example.eta.domain.recurrence.rescheduleClashes
import com.example.eta.domain.recurrence.rescheduleRows
import com.example.eta.domain.recurrence.standingBlocksOn
import com.example.eta.domain.recurrence.weekdaysOf
import com.example.eta.domain.setup.WEEK
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The week timetable: what it draws, and what a drag or a pinch on it writes. */
class WeekTimetableTest {

    private val now = Instant.parse("2026-10-01T06:00:00Z")
    private val later = Instant.parse("2026-10-11T09:00:00Z")

    private fun definition(
        id: String,
        rule: RecurrenceRule,
        start: LocalTime = LocalTime(9, 0),
        name: String = id,
        stage: Stage = Stage.DAY,
    ) = Item(
        id = id,
        type = ItemType.RECURRING,
        name = name,
        stage = stage,
        category = Category.FOKUS,
        recurrenceRule = rule,
        startTime = start,
        estimatedDuration = 1.hours,
        createdAt = now,
        updatedAt = now,
    )

    private fun ids(): () -> String {
        var next = 0
        return { "new-${next++}" }
    }

    // --- what is drawn ------------------------------------------------------

    @Test
    fun `the week begins on its Monday`() {
        // 11 October 2026 is a Sunday.
        assertEquals(LocalDate(2026, 10, 5), mondayOf(LocalDate(2026, 10, 11)))
        assertEquals(LocalDate(2026, 10, 5), mondayOf(LocalDate(2026, 10, 5)))
        assertEquals(LocalDate(2026, 10, 12), mondayOf(LocalDate(2026, 10, 12)))
    }

    @Test
    fun `a weekday shows the tasks that stand on it, in the order of the day`() {
        val definitions = listOf(
            definition("abends", RecurrenceRule.Daily, LocalTime(20, 0)),
            definition("sport", RecurrenceRule.Weekly(DayOfWeek.TUESDAY), LocalTime(17, 0)),
            definition("putzen", RecurrenceRule.Weekly(DayOfWeek.SATURDAY)),
        )
        val tuesday = standingBlocksOn(DayOfWeek.TUESDAY, LocalDate(2026, 10, 6), definitions)

        assertEquals(listOf("sport", "abends"), tuesday.map { it.item.id })
        // The block wears the definition's id: what is done to it is done to the task.
        assertEquals(listOf("sport", "abends"), tuesday.map { it.block.id })
        assertEquals(LocalTime(17, 0), tuesday.first().block.start)
        assertFalse(tuesday.first().block.isMovable)
    }

    @Test
    fun `unlocked, a standing block may be dragged`() {
        val blocks = standingBlocksOn(
            DayOfWeek.TUESDAY,
            LocalDate(2026, 10, 6),
            listOf(definition("sport", RecurrenceRule.Weekly(DayOfWeek.TUESDAY))),
            unlocked = true,
        )
        assertTrue(blocks.single().block.isMovable)
    }

    @Test
    fun `a note, an ended task and one without an hour are not on the timetable`() {
        val definitions = listOf(
            definition("notiz", RecurrenceRule.Daily, stage = Stage.COLLECTION),
            definition("beendet", RecurrenceRule.Daily).copy(completedAt = now),
            definition("ohne zeit", RecurrenceRule.Daily).copy(startTime = null),
        )
        assertTrue(standingBlocksOn(DayOfWeek.MONDAY, LocalDate(2026, 10, 5), definitions).isEmpty())
    }

    @Test
    fun `a fortnightly task stands on its weekday every week`() {
        val definitions = listOf(
            definition("alle zwei", RecurrenceRule.Biweekly(DayOfWeek.FRIDAY, WeekParity.ODD)),
        )
        assertEquals(1, standingBlocksOn(DayOfWeek.FRIDAY, LocalDate(2026, 10, 9), definitions).size)
    }

    // --- what a move writes -------------------------------------------------

    @Test
    fun `moving one weekday leaves the others of the task alone`() {
        val rows = WEEK.take(5).map { definition("arbeit-$it", RecurrenceRule.Weekly(it), name = "Arbeit") }

        val result = rescheduleRows(rows, setOf(DayOfWeek.TUESDAY), LocalTime(10, 0), 2.hours, later, ids())

        val written = result.rows.single()
        assertEquals("arbeit-TUESDAY", written.id)
        assertEquals(LocalTime(10, 0), written.startTime)
        assertEquals(2.hours, written.estimatedDuration)
        assertTrue(result.copiedFrom.isEmpty())
    }

    @Test
    fun `the moved weekday becomes an entry of its own`() {
        val rows = WEEK.take(5).map { definition("arbeit-$it", RecurrenceRule.Weekly(it), name = "Arbeit") }
        val moved = rescheduleRows(rows, setOf(DayOfWeek.TUESDAY), LocalTime(10, 0), 1.hours, later, ids()).rows
        val after = rows.filterNot { row -> moved.any { it.id == row.id } } + moved

        val groups = groupRecurring(after)
        assertEquals(2, groups.size)
        assertEquals(setOf(DayOfWeek.TUESDAY), groups.first { it.representative.startTime == LocalTime(10, 0) }.weekdays)
    }

    @Test
    fun `a daily task moved on every day stays one daily row`() {
        val daily = definition("pause", RecurrenceRule.Daily)

        val result = rescheduleRows(listOf(daily), WEEK.toSet(), LocalTime(13, 0), 30.minutes, later, ids())

        val written = result.rows.single()
        assertEquals("pause", written.id)
        assertEquals(RecurrenceRule.Daily, written.recurrenceRule)
        assertEquals(LocalTime(13, 0), written.startTime)
    }

    @Test
    fun `a daily task moved on one day is split into its weekdays`() {
        val daily = definition("pause", RecurrenceRule.Daily)

        val result = rescheduleRows(listOf(daily), setOf(DayOfWeek.MONDAY), LocalTime(13, 0), 30.minutes, later, ids())

        assertEquals(7, result.rows.size)
        assertEquals(WEEK.toSet(), result.rows.flatMap { it.recurrenceRule!!.weekdaysOf() }.toSet())
        assertTrue(result.rows.all { it.recurrenceRule is RecurrenceRule.Weekly })

        val monday = result.rows.single { it.recurrenceRule == RecurrenceRule.Weekly(DayOfWeek.MONDAY) }
        assertEquals(LocalTime(13, 0), monday.startTime)
        assertEquals(30.minutes, monday.estimatedDuration)
        result.rows.filterNot { it === monday }.forEach { other ->
            assertEquals(LocalTime(9, 0), other.startTime)
            assertEquals(1.hours, other.estimatedDuration)
        }

        // The row itself stays on a day that did not move, so its history does.
        val kept = result.rows.single { it.id == "pause" }
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.TUESDAY), kept.recurrenceRule)
        // The other six are copies and have to be given its steps.
        assertEquals(6, result.copiedFrom.size)
        assertTrue(result.copiedFrom.values.all { it == "pause" })
        assertEquals(7, result.rows.map { it.id }.toSet().size)
    }

    @Test
    fun `an ended row and a day the task does not stand on write nothing`() {
        val rows = listOf(
            definition("sport", RecurrenceRule.Weekly(DayOfWeek.TUESDAY)),
            definition("alt", RecurrenceRule.Weekly(DayOfWeek.FRIDAY)).copy(completedAt = now),
        )
        val result = rescheduleRows(rows, setOf(DayOfWeek.FRIDAY), LocalTime(10, 0), 1.hours, later, ids())
        assertTrue(result.rows.isEmpty())
    }

    // --- what is asked about before "all days" ------------------------------

    @Test
    fun `only the days that collide are reported, with what they collide with`() {
        val work = WEEK.take(5).map { definition("arbeit-$it", RecurrenceRule.Weekly(it), name = "Arbeit") }
        val sport = definition("sport", RecurrenceRule.Weekly(DayOfWeek.THURSDAY), LocalTime(17, 0), name = "Sport")
        val everything = work + sport

        val clashes = rescheduleClashes(
            group = work,
            days = WEEK.take(5).toSet() - DayOfWeek.TUESDAY,
            startTime = LocalTime(16, 30),
            duration = 1.hours,
            definitions = everything,
        )

        assertEquals(setOf(DayOfWeek.THURSDAY), clashes.keys)
        assertEquals(listOf("Sport"), clashes.getValue(DayOfWeek.THURSDAY).map { it.name })
    }

    @Test
    fun `a task does not collide with where it stands now`() {
        val work = WEEK.take(5).map { definition("arbeit-$it", RecurrenceRule.Weekly(it), name = "Arbeit") }

        val clashes = rescheduleClashes(work, WEEK.take(5).toSet(), LocalTime(9, 30), 1.hours, work)

        assertTrue(clashes.isEmpty())
    }

    @Test
    fun `a fortnightly task is not told about one in the other week`() {
        val odd = definition("ungerade", RecurrenceRule.Biweekly(DayOfWeek.FRIDAY, WeekParity.ODD))
        val even = definition("gerade", RecurrenceRule.Biweekly(DayOfWeek.FRIDAY, WeekParity.EVEN), LocalTime(14, 0))

        val clashes = rescheduleClashes(listOf(odd), setOf(DayOfWeek.FRIDAY), LocalTime(14, 0), 1.hours, listOf(odd, even))

        assertTrue(clashes.isEmpty())
    }

    // --- the box, and the pinch ---------------------------------------------

    @Test
    fun `a box starts on the grid line at or before the finger and is an hour long`() {
        assertEquals(TimelineDraft(9 * 60, 60), TimelineDraft.at(9 * 60 + 14))
        assertEquals(TimelineDraft(9 * 60 + 15, 60), TimelineDraft.at(9 * 60 + 15))
    }

    @Test
    fun `a box held into the last hour ends with the day`() {
        val draft = TimelineDraft.at(23 * 60 + 40)
        assertEquals(23 * 60 + 30, draft.startMinute)
        assertEquals(MINUTES_PER_DAY, draft.endMinute)
    }

    @Test
    fun `a pushed box lands on the grid and stays inside the day`() {
        val draft = TimelineDraft(9 * 60, 90)
        assertEquals(10 * 60 + 15, draft.movedTo(10 * 60 + 8).startMinute)
        assertEquals(0, draft.movedTo(-40).startMinute)
        assertEquals(MINUTES_PER_DAY - 90, draft.movedTo(MINUTES_PER_DAY).startMinute)
    }

    @Test
    fun `each edge follows its own finger`() {
        // Fingers apart: the top goes up half an hour, the bottom down a quarter.
        assertEquals(8 * 60 + 30 to 10 * 60 + 15, pinchedSpan(9 * 60, 10 * 60, -30f, 15f))
        // Only the lower finger moves: the start stays.
        assertEquals(9 * 60 to 9 * 60 + 30, pinchedSpan(9 * 60, 10 * 60, 0f, -30f))
    }

    @Test
    fun `an edge that has not moved keeps a start off the grid`() {
        assertEquals(9 * 60 + 5 to 11 * 60, pinchedSpan(9 * 60 + 5, 10 * 60 + 5, 2f, 55f))
    }

    @Test
    fun `a squeeze stops at a quarter of an hour`() {
        val (from, to) = pinchedSpan(9 * 60, 10 * 60, 0f, -200f)
        assertEquals(9 * 60, from)
        assertEquals(9 * 60 + 15, to)

        val (fromTop, toTop) = pinchedSpan(9 * 60, 10 * 60, 200f, 0f)
        assertEquals(10 * 60, toTop)
        assertEquals(10 * 60 - 15, fromTop)
    }

    @Test
    fun `a pinch cannot pull a block out of the day`() {
        assertEquals(0 to 60, pinchedSpan(30, 60, -500f, 0f))
        assertEquals(23 * 60 to MINUTES_PER_DAY, pinchedSpan(23 * 60, 23 * 60 + 30, 0f, 500f))
    }
}
