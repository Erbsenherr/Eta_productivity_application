package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.reminder.taskReminderPlans
import com.example.eta.domain.reminder.taskReminderText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRemindersTest {

    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val today = LocalDate(2026, 9, 1)
    private val tomorrow = LocalDate(2026, 9, 2)

    private fun entry(
        blockId: String,
        itemId: String,
        date: LocalDate,
        start: LocalTime,
        lead: Int?,
        message: String? = null,
        travelBefore: Duration? = null,
        completedAt: Instant? = null,
        discardedAt: Instant? = null,
        name: String = itemId,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = blockId,
            itemId = itemId,
            date = date,
            start = start,
            plannedDuration = 1.hours,
            travelBefore = travelBefore,
            origin = BlockOrigin.RECURRING,
            completedAt = completedAt,
            discardedAt = discardedAt,
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = itemId,
            type = ItemType.RECURRING,
            name = name,
            stage = Stage.DAY,
            category = Category.FOKUS,
            reminderLeadHours = lead,
            reminderMessage = message,
            createdAt = now,
            updatedAt = now,
        ),
    )

    @Test
    fun `a task without the extra owes nothing`() {
        val plans = taskReminderPlans(
            listOf(entry("b", "i", today, LocalTime(12, 0), lead = null)),
            now,
            zone,
        )
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `the reminder lands an hour before the task`() {
        val plans = taskReminderPlans(
            listOf(entry("b", "i", today, LocalTime(12, 0), lead = 1)),
            now,
            zone,
        )
        assertEquals(Instant.parse("2026-09-01T11:00:00Z"), plans.single().at)
    }

    @Test
    fun `a journey is counted from, not the task`() {
        // 12:00 with half an hour of travel is claimed from 11:30, so an hour
        // before is 10:30 — warning about a drive already under way is not one.
        val plans = taskReminderPlans(
            listOf(entry("b", "i", today, LocalTime(12, 0), lead = 1, travelBefore = 30.minutes)),
            now,
            zone,
        )
        assertEquals(Instant.parse("2026-09-01T10:30:00Z"), plans.single().at)
    }

    @Test
    fun `only the next occurrence of a task is owed`() {
        val plans = taskReminderPlans(
            listOf(
                entry("b2", "i", tomorrow, LocalTime(12, 0), lead = 1),
                entry("b1", "i", today, LocalTime(12, 0), lead = 1),
            ),
            now,
            zone,
        )
        assertEquals(1, plans.size)
        assertEquals("b1", plans.single().blockId)
    }

    @Test
    fun `a moment already gone by is not owed`() {
        // 06:30 today, an hour before, is 05:30 — behind "now".
        val plans = taskReminderPlans(
            listOf(
                entry("past", "i", today, LocalTime(6, 30), lead = 1),
                entry("next", "i", tomorrow, LocalTime(12, 0), lead = 1),
            ),
            now,
            zone,
        )
        assertEquals("next", plans.single().blockId)
    }

    @Test
    fun `a ticked-off or called-off occurrence is skipped`() {
        val plans = taskReminderPlans(
            listOf(
                entry("done", "a", today, LocalTime(12, 0), lead = 1, completedAt = now),
                entry("off", "b", today, LocalTime(13, 0), lead = 1, discardedAt = now),
            ),
            now,
            zone,
        )
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `one plan per task, soonest first`() {
        val plans = taskReminderPlans(
            listOf(
                entry("b", "later", today, LocalTime(15, 0), lead = 1),
                entry("a", "sooner", today, LocalTime(12, 0), lead = 1),
            ),
            now,
            zone,
        )
        assertEquals(listOf("sooner", "later"), plans.map { it.itemId })
    }

    @Test
    fun `the text is the name, and the message when there is one`() {
        val plain = entry("b", "i", today, LocalTime(12, 0), lead = 1, name = "Zahnarzt")
        assertEquals("Erinnerung: Zahnarzt", taskReminderText(plain.item))

        val withMessage = entry(
            "b", "i", today, LocalTime(12, 0), lead = 1,
            name = "Zahnarzt", message = "Karte mitnehmen",
        )
        assertEquals("Erinnerung: Zahnarzt — Karte mitnehmen", taskReminderText(withMessage.item))
    }
}
