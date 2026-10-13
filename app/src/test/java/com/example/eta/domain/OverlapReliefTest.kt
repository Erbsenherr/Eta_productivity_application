package com.example.eta.domain

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.planning.PlacementSpan
import com.example.eta.domain.planning.PlanRelief
import com.example.eta.domain.planning.collisions
import com.example.eta.domain.planning.fittedSpan
import com.example.eta.domain.planning.planRelief
import com.example.eta.domain.planning.trimmedAfter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A card put where something already stands: what it runs into, and the two
 * ways one of them can give way.
 */
class OverlapReliefTest {

    private val now = Instant.parse("2026-10-13T06:00:00Z")
    private val date = LocalDate(2026, 10, 13)

    private fun block(
        id: String,
        from: LocalTime,
        duration: Duration,
        origin: BlockOrigin = BlockOrigin.RECURRING,
        travelBefore: Duration? = null,
        breakAfter: Duration? = null,
    ) = PlannedBlock(
        id = id,
        itemId = "item-$id",
        date = date,
        start = from,
        plannedDuration = duration,
        origin = origin,
        travelBefore = travelBefore,
        breakAfter = breakAfter,
        createdAt = now,
        updatedAt = now,
    )

    private fun span(
        hour: Int,
        minute: Int = 0,
        duration: Duration = 2.hours,
        travelBefore: Duration? = null,
        breakAfter: Duration? = null,
    ) = PlacementSpan(hour * 60 + minute, duration, travelBefore, breakAfter = breakAfter)

    // --- what it runs into ---------------------------------------------------

    @Test
    fun `a card beside a block runs into nothing`() {
        val existing = listOf(block("a", LocalTime(12, 0), 1.hours))

        assertTrue(span(10).collisions(existing).isEmpty())
    }

    @Test
    fun `a break that reaches into the next block is a collision`() {
        val existing = listOf(block("a", LocalTime(12, 0), 1.hours))

        assertEquals(listOf("a"), span(10, breakAfter = 15.minutes).collisions(existing).map { it.id })
    }

    // --- the card gives way --------------------------------------------------

    @Test
    fun `a card running into a block is cut to end where that begins`() {
        val existing = listOf(block("a", LocalTime(11, 0), 2.hours))

        val fitted = fittedSpan(existing, span(10))

        assertEquals(10 * 60, fitted?.startMinute)
        assertEquals(1.hours, fitted?.duration)
    }

    @Test
    fun `a card put on the end of a block starts after it and keeps its length`() {
        val existing = listOf(block("a", LocalTime(9, 0), 150.minutes))

        val fitted = fittedSpan(existing, span(10))

        assertEquals(11 * 60 + 30, fitted?.startMinute)
        assertEquals(2.hours, fitted?.duration)
    }

    @Test
    fun `of two free stretches the one that leaves more of the card is taken`() {
        // A quarter of an hour in front of the block, plenty behind it.
        val existing = listOf(block("a", LocalTime(10, 15), 30.minutes))

        val fitted = fittedSpan(existing, span(10))

        assertEquals(10 * 60 + 45, fitted?.startMinute)
        assertEquals(2.hours, fitted?.duration)
    }

    @Test
    fun `a card covered whole goes to the next free stretch`() {
        val existing = listOf(block("a", LocalTime(9, 0), 3.hours))

        val fitted = fittedSpan(existing, span(10, duration = 1.hours))

        assertEquals(12 * 60, fitted?.startMinute)
        assertEquals(1.hours, fitted?.duration)
    }

    @Test
    fun `the break is given up before a minute of the task is`() {
        val existing = listOf(block("a", LocalTime(12, 0), 1.hours))
        val wanted = span(10, breakAfter = 15.minutes)

        val fitted = fittedSpan(existing, wanted)

        assertEquals(wanted.copy(breakAfter = null), fitted)
    }

    @Test
    fun `the journey is never given up`() {
        val existing = listOf(block("a", LocalTime(11, 0), 2.hours))

        val fitted = fittedSpan(existing, span(10, travelBefore = 30.minutes))

        // Still setting out at half past nine, and over when the block begins.
        assertEquals(10 * 60, fitted?.startMinute)
        assertEquals(30.minutes, fitted?.travelBefore)
        assertEquals(1.hours, fitted?.duration)
    }

    @Test
    fun `nothing is offered when the rest of the day is taken`() {
        val existing = listOf(block("a", LocalTime(9, 0), 15.hours))

        assertNull(fittedSpan(existing, span(10)))
    }

    // --- the plan gives way --------------------------------------------------

    @Test
    fun `a block reaching in from before is cut at its end`() {
        val existing = listOf(block("a", LocalTime(9, 0), 2.hours))

        val relief = planRelief(existing, span(10)).single() as PlanRelief.EndsEarlier

        assertEquals(LocalTime(9, 0), relief.result.start)
        assertEquals(1.hours, relief.result.plannedDuration)
    }

    @Test
    fun `a block reaching out behind is cut at its start and ends when it did`() {
        val existing = listOf(block("a", LocalTime(11, 0), 2.hours))

        val relief = planRelief(existing, span(10)).single() as PlanRelief.StartsLater

        assertEquals(LocalTime(12, 0), relief.result.start)
        assertEquals(1.hours, relief.result.plannedDuration)
    }

    @Test
    fun `a block inside the card is removed`() {
        val existing = listOf(block("a", LocalTime(10, 15), 45.minutes))

        assertTrue(planRelief(existing, span(10)).single() is PlanRelief.Removed)
    }

    @Test
    fun `a block around the card keeps its longer side, the front on a tie`() {
        val longBack = listOf(block("a", LocalTime(8, 0), 6.hours))
        val even = listOf(block("b", LocalTime(9, 0), 3.hours))
        val card = span(10, duration = 1.hours)

        val back = planRelief(longBack, card).single() as PlanRelief.StartsLater
        assertEquals(LocalTime(11, 0), back.result.start)
        assertEquals(3.hours, back.result.plannedDuration)

        val front = planRelief(even, card).single() as PlanRelief.EndsEarlier
        assertEquals(1.hours, front.result.plannedDuration)
    }

    @Test
    fun `what would be left under a quarter of an hour is removed instead`() {
        val existing = listOf(block("a", LocalTime(9, 50), 70.minutes))

        assertTrue(planRelief(existing, span(10)).single() is PlanRelief.Removed)
    }

    @Test
    fun `an appointment out of the calendar is never touched`() {
        val existing = listOf(block("a", LocalTime(9, 0), 2.hours, origin = BlockOrigin.CALENDAR_IMPORT))

        assertTrue(planRelief(existing, span(10)).single() is PlanRelief.Kept)
    }

    @Test
    fun `a block cut at its start still gets its journey`() {
        val trimmed = block("a", LocalTime(11, 0), 2.hours, travelBefore = 15.minutes)
            .trimmedAfter(12 * 60)

        assertEquals(LocalTime(12, 15), trimmed?.start)
        assertEquals(45.minutes, trimmed?.plannedDuration)
    }

    @Test
    fun `every block in the card's path gets an answer of its own`() {
        val existing = listOf(
            block("before", LocalTime(9, 0), 90.minutes),
            block("inside", LocalTime(10, 45), 30.minutes),
            block("after", LocalTime(11, 30), 2.hours),
            block("elsewhere", LocalTime(15, 0), 1.hours),
        )

        val relief = planRelief(existing, span(10))

        assertEquals(listOf("before", "inside", "after"), relief.map { it.block.id })
        assertTrue(relief[0] is PlanRelief.EndsEarlier)
        assertTrue(relief[1] is PlanRelief.Removed)
        assertTrue(relief[2] is PlanRelief.StartsLater)
    }
}
