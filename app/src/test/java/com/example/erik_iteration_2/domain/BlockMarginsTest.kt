package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemRole
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.model.Stage
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.canPlace
import com.example.erik_iteration_2.domain.planning.containerEndMinute
import com.example.erik_iteration_2.domain.planning.containerStartMinute
import com.example.erik_iteration_2.domain.planning.firstFreeStart
import com.example.erik_iteration_2.domain.planning.fitsSomewhere
import com.example.erik_iteration_2.domain.reevaluation.plannedMinutes
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
 * The container a block occupies once it brings a journey and a break with it.
 *
 * The whole point of the two columns is that every question about *time* sees the
 * container while every question about *points* still sees the task, so these are
 * the cases worth pinning: overlap, placement, and how much of a day is carried.
 */
class BlockMarginsTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun block(
        id: String,
        atHour: Int,
        duration: Duration = 1.hours,
        travelBefore: Duration? = null,
        breakAfter: Duration? = null,
    ) = PlannedBlock(
        id = id,
        itemId = "item-$id",
        date = date,
        start = LocalTime(atHour, 0),
        plannedDuration = duration,
        travelBefore = travelBefore,
        breakAfter = breakAfter,
        origin = BlockOrigin.DRAGGED,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `the container reaches back before the task and past its end`() {
        val b = block("a", 9, travelBefore = 15.minutes, breakAfter = 30.minutes)

        assertEquals(8 * 60 + 45, b.containerStartMinute())
        assertEquals(10 * 60 + 30, b.containerEndMinute())
    }

    @Test
    fun `a block without margins is its own container`() {
        val b = block("a", 9)

        assertEquals(9 * 60, b.containerStartMinute())
        assertEquals(10 * 60, b.containerEndMinute())
    }

    @Test
    fun `margins are clamped to the day at both ends`() {
        val early = block("a", 0, travelBefore = 30.minutes)
        val late = block("b", 23, duration = 1.hours, breakAfter = 30.minutes)

        assertEquals(0, early.containerStartMinute())
        assertEquals(MINUTES_PER_DAY, late.containerEndMinute())
    }

    @Test
    fun `a journey collides with what sits in front of the task`() {
        // 08:00–09:00 stands; the candidate's task is at 09:00 but its journey
        // reaches back into 08:45, so it does not fit.
        val standing = block("a", 8)
        val candidate = block("b", 9, travelBefore = 15.minutes)

        assertFalse(canPlace(listOf(standing), candidate))
    }

    @Test
    fun `without the journey the same pair is fine`() {
        val standing = block("a", 8)

        assertTrue(canPlace(listOf(standing), block("b", 9)))
    }

    @Test
    fun `firstFreeStart leaves room for the journey after an obstacle`() {
        val standing = listOf(block("a", 9))

        // The task may not start before 10:15: its 15 minutes of travel have to
        // clear the block that ends at 10:00.
        assertEquals(
            10 * 60 + 15,
            firstFreeStart(standing, 1.hours, 9 * 60, leadIn = 15.minutes),
        )
    }

    @Test
    fun `firstFreeStart never lets the journey start before midnight`() {
        assertEquals(
            30,
            firstFreeStart(emptyList(), 1.hours, 0, leadIn = 30.minutes),
        )
    }

    @Test
    fun `the break has to fit too`() {
        // 09:00–10:00 free, then 10:30 onwards taken. A one-hour task fits at
        // 09:00; the same task with a 45-minute break does not fit anywhere.
        val standing = listOf(block("a", 0, duration = 9.hours), block("b", 10, duration = 14.hours))

        assertEquals(9 * 60, firstFreeStart(standing, 1.hours, 9 * 60))
        assertNull(firstFreeStart(standing, 1.hours, 9 * 60, tailOut = 45.minutes))
    }

    @Test
    fun `the prompt asks only when dropping the break is what would save it`() {
        val standing = listOf(block("a", 0, duration = 9.hours), block("b", 10, duration = 14.hours))

        // With the break: nowhere. Without it: 09:00. That gap is the prompt.
        assertFalse(fitsSomewhere(standing, 1.hours, 9 * 60, tailOut = 45.minutes))
        assertTrue(fitsSomewhere(standing, 1.hours, 9 * 60, tailOut = null))
    }

    @Test
    fun `a day that has no room even without the break is a plain refusal`() {
        val full = listOf(block("a", 0, duration = 24.hours))

        assertFalse(fitsSomewhere(full, 1.hours, 9 * 60, tailOut = null))
    }

    @Test
    fun `planned time counts the margins`() {
        val entry = BlockWithItem(
            block = block("a", 9, travelBefore = 15.minutes, breakAfter = 15.minutes),
            item = Item(
                id = "item-a",
                type = com.example.erik_iteration_2.domain.model.ItemType.TODO,
                name = "Einkaufen",
                stage = Stage.DAY,
                category = Category.FOKUS,
                role = ItemRole.WORK,
                createdAt = now,
                updatedAt = now,
            ),
        )

        // One hour of task, half an hour of margins: the day carries 90 minutes.
        assertEquals(90, plannedMinutes(listOf(entry)))
    }
}
