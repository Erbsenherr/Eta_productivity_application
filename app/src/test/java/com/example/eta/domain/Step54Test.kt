package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemExtras
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.extras
import com.example.eta.domain.model.withExtras
import com.example.eta.domain.planning.PlanRelief
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.domain.planning.planningWindowOpen
import com.example.eta.domain.setup.UserSetup
import com.example.eta.ui.planner.blockConflict
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 54: the planning alarm past midnight, a conflict in the edit window, Ultra-High. */
class Step54Test {

    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private val date = LocalDate(2026, 11, 12)

    private fun setup() = UserSetup.draft(now).copy(dailyPlanningTime = LocalTime(20, 0))

    private fun entry(
        name: String,
        start: LocalTime,
        origin: BlockOrigin = BlockOrigin.DRAGGED,
    ): BlockWithItem {
        val item = Item.newQuickTodo(name, now).copy(stage = Stage.DAY)
        return BlockWithItem(
            PlannedBlock(
                itemId = item.id,
                date = date,
                start = start,
                plannedDuration = 1.hours,
                origin = origin,
                createdAt = now,
                updatedAt = now,
            ),
            item,
        )
    }

    // --- The planning alarm ------------------------------------------------------

    @Test
    fun `the evening's phase stays open until midnight`() {
        assertTrue(planningWindowOpen(setup(), PlanningPhase.DAILY, LocalDateTime.parse("2026-11-11T20:00")))
        assertTrue(planningWindowOpen(setup(), PlanningPhase.DAILY, LocalDateTime.parse("2026-11-11T23:55")))
    }

    @Test
    fun `past midnight the evening that was asked about is gone`() {
        assertFalse(planningWindowOpen(setup(), PlanningPhase.DAILY, LocalDateTime.parse("2026-11-12T00:05")))
        assertFalse(planningWindowOpen(setup(), PlanningPhase.DAILY, LocalDateTime.parse("2026-11-12T14:00")))
    }

    @Test
    fun `the weekly phase is not cut off at midnight`() {
        val weekly = setup().copy(weeklyPlanningDay = DayOfWeek.SUNDAY)
        assertTrue(planningWindowOpen(weekly, PlanningPhase.WEEKLY, LocalDateTime.parse("2026-11-12T00:05")))
    }

    // --- A conflict in the edit window --------------------------------------------

    @Test
    fun `a block on its own has no conflict`() {
        val a = entry("Lesen", LocalTime(9, 0))
        val b = entry("Kochen", LocalTime(11, 0))
        assertNull(blockConflict(a, listOf(a, b)))
    }

    @Test
    fun `a block in a conflict names what is in the way and where it fits next`() {
        val a = entry("Lesen", LocalTime(9, 30))
        val b = entry("Kochen", LocalTime(9, 0))
        val conflict = assertNotNullAndGet(blockConflict(a, listOf(a, b)))

        assertEquals(listOf(b.block.id), conflict.relief.map { it.block.id })
        assertTrue(conflict.movable)
        // The next stretch it fits starts once Kochen is over.
        assertEquals(10 * 60, conflict.nextFree)
    }

    @Test
    fun `a standing occurrence in a conflict is not offered a move`() {
        val a = entry("Sport", LocalTime(9, 30), origin = BlockOrigin.RECURRING)
        val b = entry("Kochen", LocalTime(9, 0))
        val conflict = assertNotNullAndGet(blockConflict(a, listOf(a, b)))

        assertFalse(conflict.movable)
        assertNull(conflict.nextFree)
        // Overwriting still can: Kochen is cut short to make room.
        assertTrue(conflict.relief.single() !is PlanRelief.Kept)
    }

    @Test
    fun `a finished block holds no time and so no conflict`() {
        val a = entry("Lesen", LocalTime(9, 30)).let { it.copy(block = it.block.copy(completedAt = now)) }
        val b = entry("Kochen", LocalTime(9, 0))
        assertNull(blockConflict(a, listOf(a, b)))
    }

    // --- Ultra-High ------------------------------------------------------------------

    @Test
    fun `Ultra-High travels with the extras`() {
        val item = Item.newQuickTodo("Steuern", now)
        val flagged = item.withExtras(item.extras.copy(ultraHigh = true))
        assertTrue(flagged.ultraHigh)
        assertTrue(flagged.extras.ultraHigh)
        assertFalse(flagged.withExtras(ItemExtras()).ultraHigh)
    }

    @Test
    fun `a standing task cannot be Ultra-High`() {
        val item = Item.newQuickTodo("Sport", now).copy(recurrenceRule = RecurrenceRule.Daily)
        assertFalse(item.withExtras(item.extras.copy(ultraHigh = true)).ultraHigh)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
