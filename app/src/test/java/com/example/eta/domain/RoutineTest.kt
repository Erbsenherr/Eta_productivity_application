package com.example.eta.domain

import com.example.eta.domain.model.ItemExtras
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.extras
import com.example.eta.domain.model.withExtras
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.SetupLabels
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.isMorningRoutine
import com.example.eta.domain.setup.isOwnedBySettings
import com.example.eta.domain.setup.recurringItems
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.domain.subtask.matchedTo
import com.example.eta.domain.subtask.routineProgress
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Routine-Modus: steps worked through one at a time, in order — and the
 * morning, which is the first task to be one.
 */
class RoutineTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")

    private fun row(id: String, position: Int, name: String = id, itemId: String = "routine") = Subtask(
        id = id,
        itemId = itemId,
        position = position,
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    private val steps = listOf(row("wasser", 0), row("duschen", 1), row("anziehen", 2))

    @Test
    fun `a routine begins at its first step`() {
        val progress = steps.routineProgress(emptySet())
        assertEquals("wasser", progress.current?.id)
        assertNull(progress.previous)
        assertEquals(0, progress.done)
        assertFalse(progress.isFinished)
    }

    @Test
    fun `ticking the step that is due brings up the next`() {
        val progress = steps.routineProgress(setOf("wasser"))
        assertEquals("duschen", progress.current?.id)
        assertEquals("wasser", progress.previous?.id)
        assertEquals(1, progress.done)
        assertEquals(3, progress.total)
    }

    @Test
    fun `the order is the list's, however the rows arrive`() {
        val progress = steps.reversed().routineProgress(setOf("wasser", "duschen"))
        assertEquals("anziehen", progress.current?.id)
        assertEquals("duschen", progress.previous?.id)
    }

    @Test
    fun `with every step ticked the routine is finished`() {
        val progress = steps.routineProgress(steps.map { it.id }.toSet())
        assertNull(progress.current)
        assertEquals("anziehen", progress.previous?.id)
        assertTrue(progress.isFinished)
    }

    @Test
    fun `no steps is not a finished routine`() {
        assertFalse(emptyList<Subtask>().routineProgress(emptySet()).isFinished)
    }

    @Test
    fun `the mode travels with the extras`() {
        val item = UserSetup.draft(now).recurringItems(now).first { it.name == SetupLabels.SPORT }
        assertFalse(item.routineMode)

        val routine = item.withExtras(item.extras.copy(routineMode = true))
        assertTrue(routine.routineMode)
        assertTrue(routine.extras.routineMode)
        // And an edit that says nothing about it leaves it where it was.
        assertTrue(routine.withExtras(ItemExtras.of(routine)).routineMode)
    }

    @Test
    fun `the morning is a routine by name and by mode`() {
        val morning = UserSetup.draft(now).recurringItems(now).single { isMorningRoutine(it.id) }
        assertEquals("Morgenroutine", morning.name)
        assertTrue(morning.routineMode)
        assertTrue(isOwnedBySettings(morning.id))
    }

    @Test
    fun `with a weekend night every morning is one, and nothing else is`() {
        val setup = UserSetup.draft(now).copy(
            weekendNight = NightTimes(LocalTime(23, 30), LocalTime(0, 30), LocalTime(9, 0)),
        )
        val items = setup.recurringItems(now)
        val mornings = items.filter { isMorningRoutine(it.id) }

        assertEquals(7, mornings.size)
        assertTrue(mornings.all { it.routineMode && it.name == "Morgenroutine" })
        assertTrue(items.filterNot { isMorningRoutine(it.id) }.none { it.routineMode })
        assertFalse(isMorningRoutine("setup:bedprep-monday"))
    }

    @Test
    fun `a draft keeps the row whose id it carries`() {
        val drafts = listOf(
            SubtaskDraft(id = "duschen", name = "Kalt duschen"),
            SubtaskDraft(id = "wasser", name = "wasser"),
            SubtaskDraft(name = "Lüften"),
        )
        assertEquals(listOf("duschen", "wasser", null), drafts.matchedTo(steps))
    }

    @Test
    fun `a list saved onto another weekday lands on that weekday's own rows`() {
        // Monday's steps, as the form holds them, written onto Tuesday.
        val monday = steps.map { SubtaskDraft(id = it.id, name = it.name) }
        val tuesday = listOf(
            row("t-wasser", 0, name = "wasser", itemId = "tuesday"),
            row("t-duschen", 1, name = "duschen", itemId = "tuesday"),
        )

        // Matched by name, so Tuesday's ticks survive — and never by Monday's
        // ids, which would move Monday's rows over instead.
        assertEquals(listOf("t-wasser", "t-duschen", null), monday.matchedTo(tuesday))
    }

    @Test
    fun `two steps of one name take two rows, not the same one twice`() {
        val twice = listOf(row("a", 0, name = "Dehnen"), row("b", 1, name = "Dehnen"))
        val drafts = listOf(SubtaskDraft(name = "Dehnen"), SubtaskDraft(name = "Dehnen"), SubtaskDraft(name = "Dehnen"))
        assertEquals(listOf("a", "b", null), drafts.matchedTo(twice))
    }

    @Test
    fun `a row already claimed by id is not handed out again by name`() {
        val rows = listOf(row("a", 0, name = "Dehnen"))
        val drafts = listOf(SubtaskDraft(name = "Dehnen"), SubtaskDraft(id = "a", name = "Dehnen"))
        assertEquals(listOf(null, "a"), drafts.matchedTo(rows))
    }
}
