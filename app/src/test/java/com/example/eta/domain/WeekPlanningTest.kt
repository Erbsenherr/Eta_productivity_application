package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.LONG_BREAK
import com.example.eta.domain.planning.SHORT_BREAK
import com.example.eta.domain.planning.breakFor
import com.example.eta.domain.planning.costOf
import com.example.eta.domain.planning.occupiedMinutesOfWeek
import com.example.eta.domain.planning.weekBudget
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPlanningTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")

    private fun todo(duration: Duration) = Item.newTodo(
        name = "Etwas",
        category = Category.FOKUS,
        priority = Priority.MUST,
        targetDate = LocalDate(2026, 9, 10),
        estimatedDuration = duration,
        now = now,
    )

    private fun freeTimeBlock(date: LocalDate, from: LocalTime, duration: Duration) =
        block(date, from, duration, ItemRole.FREE_TIME)

    private fun block(
        date: LocalDate,
        from: LocalTime,
        duration: Duration,
        role: ItemRole? = ItemRole.WORK,
    ): BlockWithItem {
        val item = Item.newRecurring(
            name = "Arbeit",
            category = Category.FOKUS,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = from,
            estimatedDuration = duration,
            now = now,
            role = role,
        )
        return BlockWithItem(
            block = PlannedBlock(
                itemId = item.id,
                date = date,
                start = from,
                plannedDuration = duration,
                origin = BlockOrigin.RECURRING,
                createdAt = now,
                updatedAt = now,
            ),
            item = item,
        )
    }

    /** Eight hours of night and four hours of social time a week. */
    private fun setup() = UserSetup.draft(now).copy(
        sleepTime = LocalTime(23, 0),
        wakeTime = LocalTime(7, 0),
        socialTimePerWeek = 4.hours,
    )

    @Test
    fun `short activities earn no break`() {
        assertEquals(Duration.ZERO, breakFor(45.minutes))
        assertEquals(Duration.ZERO, breakFor(59.minutes))
    }

    @Test
    fun `an hour earns a quarter of an hour`() {
        assertEquals(SHORT_BREAK, breakFor(1.hours))
        assertEquals(SHORT_BREAK, breakFor(89.minutes))
    }

    @Test
    fun `an hour and a half earns the longer break`() {
        assertEquals(LONG_BREAK, breakFor(90.minutes))
        assertEquals(LONG_BREAK, breakFor(4.hours))
    }

    @Test
    fun `the cost of an activity includes its break`() {
        assertEquals(75.minutes, costOf(1.hours))
        assertEquals(45.minutes, costOf(45.minutes))
    }

    @Test
    fun `overlapping blocks on one day count once, different days add up`() {
        val monday = LocalDate(2026, 9, 7)
        val tuesday = LocalDate(2026, 9, 8)
        val blocks = listOf(
            block(monday, LocalTime(9, 0), 2.hours),
            block(monday, LocalTime(10, 0), 2.hours),
            block(tuesday, LocalTime(9, 0), 1.hours),
        )
        assertEquals(3 * 60 + 60, occupiedMinutesOfWeek(blocks))
    }

    @Test
    fun `the week budget takes out sleep and the social lump first`() {
        val budget = weekBudget(setup(), emptyList(), emptyList())
        assertEquals((7 * 24 - 7 * 8) * 60 - 4 * 60, budget.availableMinutes)
        assertEquals(budget.availableMinutes, budget.freeMinutes)
        assertFalse(budget.overbooked)
    }

    @Test
    fun `the standing schedule and the week list both eat into the free hours`() {
        val blocks = listOf(block(LocalDate(2026, 9, 7), LocalTime(9, 0), 8.hours))
        val budget = weekBudget(setup(), blocks, listOf(todo(1.hours)))

        assertEquals(8 * 60, budget.committedMinutes)
        // The ToDo costs its hour plus the quarter hour of break it earns.
        assertEquals(75, budget.plannedMinutes)
        assertEquals(budget.availableMinutes - 8 * 60 - 75, budget.freeMinutes)
    }

    @Test
    fun `a week asked for more than it has is flagged as overbooked`() {
        val huge = List(20) { todo(8.hours) }
        val budget = weekBudget(setup(), emptyList(), huge)
        assertTrue(budget.overbooked)
        // Never reported as negative time; the flag carries that news instead.
        assertEquals(0, budget.freeMinutes)
    }

    @Test
    fun `a ToDo without an estimate costs nothing yet`() {
        val bare = Item.newQuickTodo("Noch unklar", now)
        assertEquals(0, weekBudget(setup(), emptyList(), listOf(bare)).plannedMinutes)
    }

    @Test
    fun `a week without a single free time block says so`() {
        val busy = weekBudget(setup(), listOf(block(LocalDate(2026, 9, 7), LocalTime(9, 0), 8.hours)), emptyList())
        assertEquals(0, busy.freeTimeMinutes)
        assertTrue(busy.withoutFreeTime)
    }

    @Test
    fun `free time in the week is counted and not warned about`() {
        val withFreeTime = weekBudget(
            setup = setup(),
            blocks = listOf(freeTimeBlock(LocalDate(2026, 9, 7), LocalTime(20, 0), 90.minutes)),
            weekList = emptyList(),
        )
        assertEquals(90, withFreeTime.freeTimeMinutes)
        assertFalse(withFreeTime.withoutFreeTime)
    }

    @Test
    fun `without a setup the week is simply the whole week`() {
        val budget = weekBudget(null, emptyList(), emptyList())
        assertEquals(7 * 24 * 60, budget.availableMinutes)
    }
}
