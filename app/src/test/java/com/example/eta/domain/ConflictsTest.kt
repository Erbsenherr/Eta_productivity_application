package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.conflictKey
import com.example.eta.domain.planning.conflictsOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictsTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun entry(
        id: String,
        start: LocalTime,
        duration: Duration = 1.hours,
        travelBefore: Duration? = null,
        discardedAt: Instant? = null,
        role: ItemRole? = null,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = id,
            itemId = "item-$id",
            date = date,
            start = start,
            plannedDuration = duration,
            travelBefore = travelBefore,
            origin = BlockOrigin.RECURRING,
            discardedAt = discardedAt,
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = "item-$id",
            type = ItemType.RECURRING,
            name = id,
            stage = Stage.DAY,
            category = Category.FOKUS,
            role = role,
            createdAt = now,
            updatedAt = now,
        ),
    )

    @Test
    fun `a day that stacks nothing has no conflicts`() {
        val day = listOf(entry("a", LocalTime(9, 0)), entry("b", LocalTime(10, 0)))
        assertTrue(conflictsOf(day).isEmpty())
    }

    @Test
    fun `two blocks on the same hour conflict, earliest first`() {
        val day = listOf(entry("late", LocalTime(9, 30)), entry("early", LocalTime(9, 0)))
        val conflict = conflictsOf(day).single()
        assertEquals("early", conflict.earlier.block.id)
        assertEquals("late", conflict.later.block.id)
        assertEquals(date, conflict.date)
    }

    @Test
    fun `a journey is part of what collides`() {
        // 09:00–10:00, then 10:00–11:00 with half an hour of travel in front of
        // it: the travel starts at 09:30, inside the first block.
        val day = listOf(
            entry("a", LocalTime(9, 0)),
            entry("b", LocalTime(10, 0), travelBefore = 30.minutes),
        )
        assertEquals(1, conflictsOf(day).size)
    }

    @Test
    fun `a block ending where the next begins does not collide`() {
        val day = listOf(
            entry("a", LocalTime(9, 0)),
            entry("b", LocalTime(10, 0)),
        )
        assertTrue(conflictsOf(day).isEmpty())
    }

    @Test
    fun `a called-off block holds nothing and so collides with nothing`() {
        val day = listOf(
            entry("a", LocalTime(9, 0)),
            entry("b", LocalTime(9, 30), discardedAt = now),
        )
        assertTrue(conflictsOf(day).isEmpty())
    }

    @Test
    fun `given-up free time keeps its slot, and its collisions`() {
        // Free time is priced the other way round, so a discarded free-time block
        // still holds its hours — and something planned into them still clashes.
        val day = listOf(
            entry("a", LocalTime(9, 0)),
            entry("free", LocalTime(9, 30), discardedAt = now, role = ItemRole.FREE_TIME),
        )
        assertEquals(1, conflictsOf(day).size)
    }

    @Test
    fun `three blocks on one hour make three pairs`() {
        val day = listOf(
            entry("a", LocalTime(9, 0)),
            entry("b", LocalTime(9, 15)),
            entry("c", LocalTime(9, 30)),
        )
        assertEquals(3, conflictsOf(day).size)
    }

    @Test
    fun `the key is the same whichever way round the pair is found`() {
        assertEquals(conflictKey("x", "y"), conflictKey("y", "x"))
    }
}
