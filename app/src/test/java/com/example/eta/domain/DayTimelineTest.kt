package com.example.eta.domain

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.canPlace
import com.example.eta.domain.planning.collisionsFor
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.firstFreeStart
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.sleepStretches
import com.example.eta.domain.planning.snapToGrid
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayTimelineTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun block(
        id: String,
        from: LocalTime,
        duration: Duration,
        origin: BlockOrigin = BlockOrigin.DRAGGED,
    ) = PlannedBlock(
        id = id,
        itemId = "item-$id",
        date = date,
        start = from,
        plannedDuration = duration,
        origin = origin,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `blocks that only touch do not collide`() {
        val morning = block("a", LocalTime(9, 0), 1.hours)
        val next = block("b", LocalTime(10, 0), 1.hours)
        assertTrue(canPlace(listOf(morning), next))
    }

    @Test
    fun `a block cannot be placed on top of another`() {
        val existing = listOf(block("a", LocalTime(9, 0), 2.hours))
        val candidate = block("b", LocalTime(10, 0), 1.hours)

        assertFalse(canPlace(existing, candidate))
        assertEquals(listOf("a"), collisionsFor(existing, candidate).map { it.id })
    }

    @Test
    fun `moving a block does not collide with where it used to be`() {
        val existing = listOf(block("a", LocalTime(9, 0), 2.hours))
        val moved = block("a", LocalTime(10, 0), 2.hours)
        assertTrue(canPlace(existing, moved))
    }

    @Test
    fun `the corrected duration is what counts, not the planned one`() {
        val corrected = block("a", LocalTime(9, 0), 1.hours).copy(actualDuration = 3.hours)
        assertEquals(12 * 60, corrected.endMinute())
    }

    @Test
    fun `a block running past midnight is shown ending at the edge`() {
        val late = block("a", LocalTime(23, 0), 3.hours)
        assertEquals(MINUTES_PER_DAY, late.endMinute())
    }

    @Test
    fun `snapping rounds to the nearest quarter hour`() {
        assertEquals(600, snapToGrid(597))
        assertEquals(615, snapToGrid(608))
        assertEquals(0, snapToGrid(7))
    }

    @Test
    fun `a free start is the wanted one when nothing is in the way`() {
        assertEquals(9 * 60, firstFreeStart(emptyList(), 1.hours, 9 * 60))
    }

    @Test
    fun `an occupied slot pushes the block to just after the obstacle`() {
        val existing = listOf(block("a", LocalTime(9, 0), 90.minutes))
        assertEquals(10 * 60 + 30, firstFreeStart(existing, 1.hours, 9 * 60))
    }

    @Test
    fun `a gap too small is skipped rather than squeezed into`() {
        val existing = listOf(
            block("a", LocalTime(9, 0), 1.hours),
            block("b", LocalTime(10, 30), 1.hours),
        )
        // The half hour between them cannot hold an hour, so the search moves on.
        assertEquals(11 * 60 + 30, firstFreeStart(existing, 1.hours, 9 * 60))
    }

    @Test
    fun `a gap that does fit is used`() {
        val existing = listOf(
            block("a", LocalTime(9, 0), 1.hours),
            block("b", LocalTime(11, 0), 1.hours),
        )
        assertEquals(10 * 60, firstFreeStart(existing, 1.hours, 9 * 60))
    }

    @Test
    fun `a full rest of the day yields no start at all`() {
        val existing = listOf(block("a", LocalTime(22, 0), 2.hours))
        assertNull(firstFreeStart(existing, 1.hours, 22 * 60))
    }

    @Test
    fun `a night crossing midnight is shaded as two stretches`() {
        val setup = UserSetup.draft(now).copy(
            sleepTime = LocalTime(23, 0),
            wakeTime = LocalTime(7, 0),
        )
        assertEquals(listOf(0 until 420, 1380 until 1440), setup.sleepStretches(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun `going to bed after midnight needs only one stretch`() {
        val setup = UserSetup.draft(now).copy(
            sleepTime = LocalTime(1, 0),
            wakeTime = LocalTime(9, 0),
        )
        assertEquals(listOf(60 until 540), setup.sleepStretches(DayOfWeek.WEDNESDAY))
    }

    @Test
    fun `minutes and times convert both ways`() {
        assertEquals(LocalTime(13, 45), minuteToLocalTime(13 * 60 + 45))
        assertEquals(LocalTime(23, 59), minuteToLocalTime(MINUTES_PER_DAY + 500))
    }
}
