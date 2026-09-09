package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemType
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.model.Stage
import com.example.erik_iteration_2.domain.planning.TaskEventKind
import com.example.erik_iteration_2.domain.planning.endsAt
import com.example.erik_iteration_2.domain.planning.eventsAt
import com.example.erik_iteration_2.domain.planning.nextTaskEvent
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The end announcement, which shares the start alarm.
 *
 * Two gates decide whether a block's end ever rings: `Item.endSound`, which is
 * off for everything that predates the switch, and the block still being open.
 */
class TaskEndTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)
    private val zone = TimeZone.UTC

    private fun entry(
        id: String,
        atHour: Int,
        endSound: Boolean,
        completed: Boolean = false,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = id,
            itemId = "item-$id",
            date = date,
            start = LocalTime(atHour, 0),
            plannedDuration = 1.hours,
            completedAt = if (completed) now else null,
            origin = BlockOrigin.DRAGGED,
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = "item-$id",
            type = ItemType.TODO,
            name = "Aufgabe $id",
            stage = Stage.DAY,
            category = Category.FOKUS,
            endSound = endSound,
            createdAt = now,
            updatedAt = now,
        ),
    )

    @Test
    fun `the end is start plus what the plan allots`() {
        val e = entry("a", 9, endSound = true)

        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), e.block.endsAt(zone))
    }

    @Test
    fun `the next event is the nearer of a start and an end`() {
        val nine = entry("a", 9, endSound = true)
        val eleven = entry("b", 11, endSound = false)

        // 09:00 start, 10:00 end, 11:00 start. From 10:30 the next is 11:00.
        assertEquals(
            Instant.parse("2026-09-01T10:00:00Z"),
            nextTaskEvent(listOf(nine, eleven), Instant.parse("2026-09-01T09:30:00Z"), zone),
        )
        assertEquals(
            Instant.parse("2026-09-01T11:00:00Z"),
            nextTaskEvent(listOf(nine, eleven), Instant.parse("2026-09-01T10:30:00Z"), zone),
        )
    }

    @Test
    fun `a card without the switch never books an end`() {
        val quiet = entry("a", 9, endSound = false)

        assertNull(nextTaskEvent(listOf(quiet), Instant.parse("2026-09-01T09:30:00Z"), zone))
        assertTrue(eventsAt(listOf(quiet), Instant.parse("2026-09-01T10:00:00Z"), zone).isEmpty())
    }

    @Test
    fun `a block already ticked off says nothing at all`() {
        val done = entry("a", 9, endSound = true, completed = true)

        assertTrue(eventsAt(listOf(done), Instant.parse("2026-09-01T10:00:00Z"), zone).isEmpty())
        assertNull(nextTaskEvent(listOf(done), now, zone))
    }

    @Test
    fun `the minute the alarm was laid down for picks the kind`() {
        val e = entry("a", 9, endSound = true)

        val atStart = eventsAt(listOf(e), Instant.parse("2026-09-01T09:00:00Z"), zone)
        val atEnd = eventsAt(listOf(e), Instant.parse("2026-09-01T10:00:00Z"), zone)

        assertEquals(listOf(TaskEventKind.START), atStart.map { it.kind })
        assertEquals(listOf(TaskEventKind.END), atEnd.map { it.kind })
    }
}
