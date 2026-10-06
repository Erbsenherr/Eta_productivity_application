package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.BlockPhase
import com.example.eta.domain.planning.breakStartMinute
import com.example.eta.domain.planning.containerEndMinute
import com.example.eta.domain.planning.containerStartMinute
import com.example.eta.domain.planning.firstFreeStart
import com.example.eta.domain.planning.fitsSomewhere
import com.example.eta.domain.planning.isCancelled
import com.example.eta.domain.planning.nowAndNext
import com.example.eta.domain.planning.occupiesTime
import com.example.eta.domain.planning.phaseAt
import com.example.eta.domain.planning.returnStartMinute
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The third margin, and what the "now" box makes of it.
 *
 * The order is the part worth pinning: task, then the way back, then the break.
 */
class ReturnJourneyTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun block(
        id: String,
        atHour: Int,
        atMinute: Int = 0,
        duration: Duration = 1.hours,
        travelBefore: Duration? = null,
        returnAfter: Duration? = null,
        breakAfter: Duration? = null,
        discarded: Boolean = false,
    ) = PlannedBlock(
        id = id,
        itemId = "item-$id",
        date = date,
        start = LocalTime(atHour, atMinute),
        plannedDuration = duration,
        travelBefore = travelBefore,
        returnAfter = returnAfter,
        breakAfter = breakAfter,
        discardedAt = if (discarded) now else null,
        origin = BlockOrigin.DRAGGED,
        createdAt = now,
        updatedAt = now,
    )

    private fun entry(b: PlannedBlock, role: ItemRole? = null) = BlockWithItem(
        block = b,
        item = Item(
            id = b.itemId,
            type = ItemType.TODO,
            name = b.id,
            stage = Stage.DAY,
            category = Category.FOKUS,
            role = role,
            createdAt = now,
            updatedAt = now,
        ),
    )

    @Test
    fun `the way back comes before the break`() {
        val b = block("a", 9, returnAfter = 20.minutes, breakAfter = 10.minutes)

        assertEquals(10 * 60, b.returnStartMinute())
        assertEquals(10 * 60 + 20, b.breakStartMinute())
        assertEquals(10 * 60 + 30, b.containerEndMinute())
    }

    @Test
    fun `all three margins stack in order`() {
        val b = block(
            "a",
            9,
            travelBefore = 15.minutes,
            returnAfter = 15.minutes,
            breakAfter = 30.minutes,
        )

        assertEquals(8 * 60 + 45, b.containerStartMinute())
        assertEquals(10 * 60 + 45, b.containerEndMinute())
    }

    @Test
    fun `placement has to clear the way back as well as the break`() {
        // 12:00-13:00 stands. A one-hour task dropped at 11:00 fits exactly, with
        // nothing to spare — so the same task with half an hour of way back does
        // not, and slides past the obstacle instead of overlapping it.
        val standing = listOf(block("a", 12))

        assertEquals(11 * 60, firstFreeStart(standing, 1.hours, 11 * 60))
        assertEquals(
            13 * 60,
            firstFreeStart(standing, 1.hours, 11 * 60, tailOut = 30.minutes),
        )
    }

    @Test
    fun `the break yields to a full day but the way back never does`() {
        val standing = listOf(block("a", 0, duration = 9.hours), block("b", 11, duration = 13.hours))

        // 09:00-11:00 free. Task plus its way back fits; add a break and it does not.
        assertTrue(fitsSomewhere(standing, 1.hours, 9 * 60, tailOut = 30.minutes))
        assertFalse(
            fitsSomewhere(
                standing,
                1.hours,
                9 * 60,
                tailOut = 30.minutes,
                tailOutExtra = 45.minutes,
            ),
        )
    }

    @Test
    fun `the phase says which part of the container a minute is in`() {
        val e = entry(
            block("a", 9, travelBefore = 15.minutes, returnAfter = 15.minutes, breakAfter = 15.minutes),
        )

        assertNull(e.phaseAt(8 * 60 + 30))
        assertEquals(BlockPhase.TRAVEL, e.phaseAt(8 * 60 + 50))
        assertEquals(BlockPhase.TASK, e.phaseAt(9 * 60 + 30))
        assertEquals(BlockPhase.RETURN, e.phaseAt(10 * 60 + 5))
        assertEquals(BlockPhase.BREAK, e.phaseAt(10 * 60 + 20))
        assertNull(e.phaseAt(10 * 60 + 45))
    }

    @Test
    fun `the next thing is the journey, not the appointment it leads to`() {
        // The user's own example: 17:15 with a quarter of an hour of travel.
        val day = listOf(entry(block("zahnarzt", 17, 15, travelBefore = 15.minutes)))
        val result = nowAndNext(day, 16 * 60)

        assertEquals("zahnarzt", result.next?.entry?.item?.name)
        assertEquals(BlockPhase.TRAVEL, result.next?.phase)
        assertNull(result.current)
    }

    @Test
    fun `once the journey has begun it is what you are doing now`() {
        val day = listOf(entry(block("zahnarzt", 17, 15, travelBefore = 15.minutes)))
        val result = nowAndNext(day, 17 * 60 + 5)

        assertEquals(BlockPhase.TRAVEL, result.current?.phase)
    }

    @Test
    fun `a break is something you are in, never the next thing`() {
        val day = listOf(entry(block("a", 9, breakAfter = 30.minutes)))

        // Inside the break: current, and named.
        assertEquals(BlockPhase.BREAK, nowAndNext(day, 10 * 60 + 10).current?.phase)
        // Before the block: what is coming is the task itself, not its break.
        assertEquals(BlockPhase.TASK, nowAndNext(day, 8 * 60).next?.phase)
    }

    @Test
    fun `a cancellation leaves the day, except when it is free time`() {
        val ordinary = entry(block("a", 9, discarded = true))
        val freeTime = entry(block("f", 20, discarded = true), role = ItemRole.FREE_TIME)

        assertTrue(ordinary.isCancelled())
        assertFalse(ordinary.occupiesTime())

        assertFalse(freeTime.isCancelled())
        assertTrue(freeTime.occupiesTime())
    }
}
