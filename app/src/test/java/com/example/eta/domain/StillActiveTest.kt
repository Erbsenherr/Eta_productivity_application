package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.STILL_ACTIVE_EDGE_MINUTES
import com.example.eta.domain.planning.TaskEventKind
import com.example.eta.domain.planning.canAskStillActive
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.eventsAt
import com.example.eta.domain.planning.nextTaskEvent
import com.example.eta.domain.planning.startMinute
import com.example.eta.domain.planning.stillActiveMinutes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillActiveTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)
    private val zone = TimeZone.UTC

    private fun entry(
        id: String,
        at: LocalTime,
        duration: Duration,
        role: ItemRole? = null,
        spend: Boolean = false,
        completed: Boolean = false,
        discarded: Boolean = false,
    ): BlockWithItem {
        val item = if (spend) {
            Item.newSpend(name = id, pointsPerHour = -5.0, now = now).copy(id = "item-$id")
        } else {
            Item.newRecurring(
                id = "item-$id",
                name = id,
                category = Category.FOKUS,
                recurrenceRule = RecurrenceRule.Daily,
                startTime = at,
                estimatedDuration = duration,
                now = now,
                role = role,
            )
        }
        return BlockWithItem(
            block = PlannedBlock(
                id = id,
                itemId = item.id,
                date = date,
                start = at,
                plannedDuration = duration,
                origin = BlockOrigin.RECURRING,
                completedAt = if (completed) now else null,
                discardedAt = if (discarded) now else null,
                createdAt = now,
                updatedAt = now,
            ),
            item = item,
        )
    }

    @Test
    fun `an hour exactly is long enough, less is not`() {
        assertTrue(entry("a", LocalTime(9, 0), 1.hours).canAskStillActive())
        assertFalse(entry("b", LocalTime(9, 0), 45.minutes).canAskStillActive())
    }

    @Test
    fun `breaks, free time and points entries are never asked about`() {
        assertFalse(entry("p", LocalTime(9, 0), 2.hours, role = ItemRole.BREAK).canAskStillActive())
        assertFalse(entry("f", LocalTime(9, 0), 2.hours, role = ItemRole.FREE_TIME).canAskStillActive())
        assertFalse(entry("s", LocalTime(9, 0), 2.hours, spend = true).canAskStillActive())
        assertTrue(entry("w", LocalTime(9, 0), 2.hours, role = ItemRole.WORK).canAskStillActive())
    }

    @Test
    fun `the count is honoured, inside the tasks and clear of their edges`() {
        val blocks = listOf(
            entry("work", LocalTime(9, 0), 3.hours, role = ItemRole.WORK),
            entry("lunch", LocalTime(12, 0), 1.hours, role = ItemRole.BREAK),
            entry("study", LocalTime(14, 0), 2.hours),
        )
        val picks = stillActiveMinutes(blocks, date, perDay = 5)

        assertEquals(5, picks.size)
        assertEquals(5, picks.map { it.second }.toSet().size)
        picks.forEach { (entry, minute) ->
            assertTrue(entry.item.role != ItemRole.BREAK)
            assertTrue(minute >= entry.block.startMinute() + STILL_ACTIVE_EDGE_MINUTES)
            assertTrue(minute < entry.block.endMinute() - STILL_ACTIVE_EDGE_MINUTES)
        }
    }

    @Test
    fun `the draw is the same every time it is asked, so re-aiming the alarm cannot lose it`() {
        val blocks = listOf(entry("work", LocalTime(9, 0), 8.hours))
        assertEquals(
            stillActiveMinutes(blocks, date, perDay = 4),
            stillActiveMinutes(blocks, date, perDay = 4),
        )
    }

    @Test
    fun `ticking a task off does not reshuffle the rest of the day`() {
        val open = listOf(entry("a", LocalTime(9, 0), 2.hours), entry("b", LocalTime(13, 0), 2.hours))
        val oneDone = listOf(
            entry("a", LocalTime(9, 0), 2.hours, completed = true),
            entry("b", LocalTime(13, 0), 2.hours),
        )
        assertEquals(
            stillActiveMinutes(open, date, 6).map { it.second },
            stillActiveMinutes(oneDone, date, 6).map { it.second },
        )
    }

    @Test
    fun `a day with nothing long enough asks nothing, and zero asks nothing`() {
        assertTrue(stillActiveMinutes(listOf(entry("a", LocalTime(9, 0), 30.minutes)), date, 3).isEmpty())
        assertTrue(stillActiveMinutes(listOf(entry("a", LocalTime(9, 0), 3.hours)), date, 0).isEmpty())
    }

    @Test
    fun `a question rings only for a task still open, and only when switched on`() {
        val blocks = listOf(entry("work", LocalTime(9, 0), 4.hours))
        val (_, minute) = stillActiveMinutes(blocks, date, 1).single()
        val at = date.atTime(LocalTime(minute / 60, minute % 60)).toInstant(zone)

        assertEquals(listOf(TaskEventKind.STILL_ACTIVE), eventsAt(blocks, at, zone, stillActivePerDay = 1).map { it.kind })
        assertTrue(eventsAt(blocks, at, zone, stillActivePerDay = 0).isEmpty())

        val done = listOf(entry("work", LocalTime(9, 0), 4.hours, completed = true))
        assertTrue(eventsAt(done, at, zone, stillActivePerDay = 1).isEmpty())
        assertEquals(null, nextTaskEvent(done, now, zone, stillActivePerDay = 1))
    }
}
