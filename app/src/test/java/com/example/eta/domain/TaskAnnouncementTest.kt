package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.TaskEvent
import com.example.eta.domain.planning.TaskEventKind
import com.example.eta.domain.planning.spokenAnnouncement
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** What is read out when a task event is spoken instead of sounded. */
class TaskAnnouncementTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")

    private fun event(
        name: String,
        kind: TaskEventKind,
        itemNote: String? = null,
        blockNote: String? = null,
    ): TaskEvent {
        val item = Item.newRecurring(
            id = "item-$name",
            name = name,
            category = Category.FOKUS,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = LocalTime(9, 0),
            estimatedDuration = 1.hours,
            now = now,
        ).copy(note = itemNote)
        val block = PlannedBlock(
            id = "block-$name",
            itemId = item.id,
            date = LocalDate(2026, 9, 1),
            start = LocalTime(9, 0),
            plannedDuration = 1.hours,
            origin = BlockOrigin.RECURRING,
            note = blockNote,
            createdAt = now,
            updatedAt = now,
        )
        return TaskEvent(BlockWithItem(block = block, item = item), kind, now)
    }

    @Test
    fun `a start names the task`() {
        assertEquals(
            "Jetzt: Sport.",
            spokenAnnouncement(listOf(event("Sport", TaskEventKind.START)), withNotes = false),
        )
    }

    @Test
    fun `notes are read only when asked for`() {
        val start = event("Sport", TaskEventKind.START, itemNote = "Schuhe mitnehmen")

        assertEquals("Jetzt: Sport.", spokenAnnouncement(listOf(start), withNotes = false))
        assertEquals(
            "Jetzt: Sport. Schuhe mitnehmen",
            spokenAnnouncement(listOf(start), withNotes = true),
        )
    }

    @Test
    fun `the note of the definition comes before the one of the day, and blank ones are skipped`() {
        val both = event("Sport", TaskEventKind.START, itemNote = "Schuhe", blockNote = " Heute Halle 2 ")
        val blank = event("Lesen", TaskEventKind.START, itemNote = "  ", blockNote = null)

        assertEquals("Jetzt: Sport. Schuhe Heute Halle 2", spokenAnnouncement(listOf(both), true))
        assertEquals("Jetzt: Lesen.", spokenAnnouncement(listOf(blank), true))
    }

    @Test
    fun `notes are not repeated at the end or inside a pomodoro`() {
        val note = "Schuhe mitnehmen"

        assertEquals(
            "Sport: die Zeit ist um.",
            spokenAnnouncement(listOf(event("Sport", TaskEventKind.END, itemNote = note)), true),
        )
        assertEquals(
            "Pause bei Sport.",
            spokenAnnouncement(listOf(event("Sport", TaskEventKind.POMODORO_PAUSE, itemNote = note)), true),
        )
        assertEquals(
            "Weiter mit Sport.",
            spokenAnnouncement(listOf(event("Sport", TaskEventKind.POMODORO_WORK)), true),
        )
        assertEquals(
            "Bist du noch bei Sport?",
            spokenAnnouncement(listOf(event("Sport", TaskEventKind.STILL_ACTIVE)), true),
        )
    }

    @Test
    fun `two tasks on the same minute are both named`() {
        val events = listOf(
            event("Arbeit", TaskEventKind.END),
            event("Sport", TaskEventKind.START),
        )

        assertEquals("Arbeit: die Zeit ist um. Jetzt: Sport.", spokenAnnouncement(events, false))
    }

    @Test
    fun `no events, nothing to say`() {
        assertEquals("", spokenAnnouncement(emptyList(), withNotes = true))
    }
}
