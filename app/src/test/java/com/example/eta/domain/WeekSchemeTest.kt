package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.recurrence.RecurringSlot
import com.example.eta.domain.recurrence.freeSpans
import com.example.eta.domain.recurrence.nextFreeStart
import com.example.eta.domain.recurrence.slotOccupancy
import com.example.eta.domain.recurrence.weekOccupancy
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Wochenschema's arithmetic (step 25). */
class WeekSchemeTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")

    private fun definition(
        id: String,
        rule: RecurrenceRule,
        start: LocalTime,
        stage: Stage = Stage.DAY,
    ) = Item(
        id = id,
        type = ItemType.RECURRING,
        name = id,
        stage = stage,
        category = Category.FOKUS,
        recurrenceRule = rule,
        startTime = start,
        estimatedDuration = 1.hours,
        travelBefore = 15.minutes,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a task books its container on its own weekday only`() {
        val booked = weekOccupancy(
            listOf(definition("sport", RecurrenceRule.Weekly(DayOfWeek.TUESDAY), LocalTime(18, 0))),
            setup = null,
        )
        assertEquals(listOf((17 * 60 + 45) until 19 * 60), booked.getValue(DayOfWeek.TUESDAY))
        assertTrue(booked.getValue(DayOfWeek.MONDAY).isEmpty())
    }

    @Test
    fun `a daily task books every day, and touching spans merge`() {
        val booked = weekOccupancy(
            listOf(
                definition("a", RecurrenceRule.Daily, LocalTime(8, 15)),
                definition("b", RecurrenceRule.Daily, LocalTime(9, 30)),
            ),
            setup = null,
        )
        DayOfWeek.entries.forEach { day ->
            assertEquals(listOf(8 * 60 until (10 * 60 + 30)), booked.getValue(day))
        }
    }

    @Test
    fun `notes, retired tasks and the edited task itself book nothing`() {
        val note = definition("note", RecurrenceRule.Daily, LocalTime(8, 0), stage = Stage.COLLECTION)
        val retired = definition("old", RecurrenceRule.Daily, LocalTime(8, 0)).copy(completedAt = now)
        val edited = definition("edited", RecurrenceRule.Daily, LocalTime(8, 0))
        val booked = weekOccupancy(listOf(note, retired, edited), setup = null, ignoreIds = setOf("edited"))
        assertTrue(booked.values.all { it.isEmpty() })
    }

    @Test
    fun `free is the complement of booked`() {
        assertEquals(listOf(0 until 1440), freeSpans(emptyList()))
        assertEquals(
            listOf(0 until 60, 120 until 1440),
            freeSpans(listOf(60 until 120)),
        )
        assertEquals(listOf(60 until 1380), freeSpans(listOf(0 until 60, 1380 until 1440)))
    }

    @Test
    fun `a form's slots are drawn on their weekdays`() {
        val slot = RecurringSlot(RecurrenceRule.Weekly(DayOfWeek.FRIDAY), LocalTime(7, 0), 30.minutes)
        val draft = slotOccupancy(listOf(slot))
        assertEquals(listOf(420 until 450), draft.getValue(DayOfWeek.FRIDAY))
        assertTrue(draft.getValue(DayOfWeek.MONDAY).isEmpty())
    }

    // Sport on Tuesday, 18:00 for an hour with a quarter of an hour's journey:
    // booked 17:45–19:00.
    private val tuesdaySport = weekOccupancy(
        listOf(definition("sport", RecurrenceRule.Weekly(DayOfWeek.TUESDAY), LocalTime(18, 0))),
        setup = null,
    )

    @Test
    fun `the next free slot chains onto what was in the way, journey included`() {
        val slot = RecurringSlot(
            RecurrenceRule.Weekly(DayOfWeek.TUESDAY),
            LocalTime(18, 30),
            1.hours,
            travelBefore = 15.minutes,
        )
        // The journey begins where Sport ends, so the task itself starts at 19:15.
        assertEquals(LocalTime(19, 15), nextFreeStart(listOf(slot), tuesdaySport))
    }

    @Test
    fun `the next free slot has to be free on every weekday of the form`() {
        val slots = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY).map {
            RecurringSlot(RecurrenceRule.Weekly(it), LocalTime(18, 0), 1.hours)
        }
        assertEquals(LocalTime(19, 0), nextFreeStart(slots, tuesdaySport))
    }

    @Test
    fun `a slot that is already free stays where it is`() {
        val slot = RecurringSlot(RecurrenceRule.Weekly(DayOfWeek.MONDAY), LocalTime(18, 0), 1.hours)
        assertEquals(LocalTime(18, 0), nextFreeStart(listOf(slot), tuesdaySport))
    }

    @Test
    fun `no slot is offered when the rest of the day cannot hold the task`() {
        // Booked 22:15–23:30 every day; two hours from 22:00 fit nowhere after it.
        val booked = weekOccupancy(
            listOf(definition("late", RecurrenceRule.Daily, LocalTime(22, 30))),
            setup = null,
        )
        val slot = RecurringSlot(RecurrenceRule.Weekly(DayOfWeek.TUESDAY), LocalTime(22, 0), 2.hours)
        assertNull(nextFreeStart(listOf(slot), booked))
    }
}
