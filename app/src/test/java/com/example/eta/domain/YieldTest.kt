package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Priority
import com.example.eta.domain.reward.CUSTOM_EARN_POINTS_PER_HOUR
import com.example.eta.domain.reward.CUSTOM_SPEND_POINTS_PER_HOUR
import com.example.eta.domain.reward.plannedYield
import com.example.eta.domain.reward.yieldOf
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YieldTest {

    private val now = Instant.fromEpochSeconds(0)
    private val date = LocalDate(2026, 8, 21)

    private fun todo(category: Category) = Item.newTodo(
        name = "Aufgabe",
        category = category,
        priority = Priority.MUST,
        targetDate = date,
        estimatedDuration = 1.hours,
        now = now,
    )

    private fun block(
        itemId: String,
        planned: Duration,
        origin: BlockOrigin = BlockOrigin.DRAGGED,
        actual: Duration? = null,
    ) = PlannedBlock(
        itemId = itemId,
        date = date,
        start = LocalTime(9, 0),
        plannedDuration = planned,
        origin = origin,
        actualDuration = actual,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `Fokus yields one point per hour`() {
        val item = todo(Category.FOKUS)
        assertEquals(2.0, yieldOf(item, block(item.id, 2.hours)), 0.0001)
    }

    @Test
    fun `Nebenbei yields half a point per hour`() {
        val item = todo(Category.NEBENBEI)
        assertEquals(1.0, yieldOf(item, block(item.id, 2.hours)), 0.0001)
    }

    @Test
    fun `Achtsam yields like Fokus`() {
        val item = todo(Category.ACHTSAM)
        assertEquals(2.0, yieldOf(item, block(item.id, 2.hours)), 0.0001)
    }

    @Test
    fun `actual duration overrides the planned duration`() {
        val item = todo(Category.FOKUS)
        val b = block(item.id, planned = 3.hours, actual = 1.hours)
        assertEquals(1.0, yieldOf(item, b), 0.0001)
    }

    @Test
    fun `imported calendar events pay only per full hour`() {
        val item = todo(Category.FOKUS)
        val b = block(item.id, planned = 1.5.hours, origin = BlockOrigin.CALENDAR_IMPORT)
        assertEquals(0.5, yieldOf(item, b), 0.0001)
    }

    @Test
    fun `custom earn adds points and custom spend removes them`() {
        val earn = Item.newSpend("Bonus", CUSTOM_EARN_POINTS_PER_HOUR, now)
        val spend = Item.newSpend("Serie schauen", CUSTOM_SPEND_POINTS_PER_HOUR, now)
        assertEquals(1.5, yieldOf(earn, block(earn.id, 1.hours)), 0.0001)
        assertEquals(-5.0, yieldOf(spend, block(spend.id, 1.hours)), 0.0001)
    }

    @Test
    fun `the forecast counts a spend before it is ticked off, and follows its rate`() {
        val work = todo(Category.FOKUS)
        val gaming = Item.newSpend("Zocken", -0.5, now)
        val day = listOf(
            BlockWithItem(block(work.id, 2.hours).copy(completedAt = now), work),
            // Placed, not yet checked: the harvest ignores it, the forecast must not.
            BlockWithItem(block(gaming.id, 1.hours), gaming),
        )
        assertEquals(1.5, plannedYield(day), 0.0001)

        val pricier = day.map {
            if (it.item.id == gaming.id) it.copy(item = gaming.copy(pointsPerHour = -5.0)) else it
        }
        assertEquals(-3.0, plannedYield(pricier), 0.0001)
    }

    @Test
    fun `a called-off block is not part of the forecast`() {
        val work = todo(Category.FOKUS)
        val day = listOf(BlockWithItem(block(work.id, 2.hours).copy(discardedAt = now), work))
        assertEquals(0.0, plannedYield(day), 0.0001)
    }

    @Test
    fun `recurring blocks are not movable but dragged ones are`() {
        val item = todo(Category.FOKUS)
        assertFalse(block(item.id, 1.hours, BlockOrigin.RECURRING).isMovable)
        assertFalse(block(item.id, 1.hours, BlockOrigin.CALENDAR_IMPORT).isMovable)
        assertTrue(block(item.id, 1.hours, BlockOrigin.DRAGGED).isMovable)
    }
}
