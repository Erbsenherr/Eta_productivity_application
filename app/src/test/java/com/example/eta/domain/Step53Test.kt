package com.example.eta.domain

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PlanSnapshot
import com.example.eta.domain.planning.planRestore
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 53: "Rückgängig" in the day planner, and a note turned into the other kind of card. */
class Step53Test {

    private val before = Instant.parse("2026-10-28T08:00:00Z")
    private val after = Instant.parse("2026-10-28T08:00:05Z")
    private val date = LocalDate(2026, 10, 29)

    private fun todo(name: String, stage: Stage = Stage.WEEK, created: Instant = before) =
        Item.newQuickTodo(name, created).copy(stage = stage)

    private fun block(item: Item, start: LocalTime = LocalTime(9, 0)) = PlannedBlock(
        itemId = item.id,
        date = date,
        start = start,
        plannedDuration = 1.hours,
        origin = BlockOrigin.DRAGGED,
        createdAt = before,
        updatedAt = before,
    )

    // --- Rückgängig -------------------------------------------------------------

    @Test
    fun `a move is undone by writing the old block back`() {
        val item = todo("Lesen", Stage.DAY)
        val old = block(item)
        val moved = old.copy(start = LocalTime(14, 0), updatedAt = after)

        val restore = planRestore(
            PlanSnapshot(listOf(old), listOf(item), before),
            PlanSnapshot(listOf(moved), listOf(item), after),
        )

        assertEquals(listOf(old), restore.restoreBlocks)
        assertTrue(restore.removeBlocks.isEmpty())
        assertTrue(restore.restoreItems.isEmpty())
        assertTrue(restore.deleteItems.isEmpty())
    }

    @Test
    fun `a card placed from the week list goes back to it, and its block goes`() {
        val inWeek = todo("Steuern")
        val placed = inWeek.copy(stage = Stage.DAY, updatedAt = after)
        val laid = block(placed)

        val restore = planRestore(
            PlanSnapshot(emptyList(), listOf(inWeek), before),
            PlanSnapshot(listOf(laid), listOf(placed), after),
        )

        assertEquals(listOf(laid), restore.removeBlocks)
        assertEquals(listOf(inWeek), restore.restoreItems)
        // It was there before: handed back, never deleted.
        assertTrue(restore.deleteItems.isEmpty())
    }

    @Test
    fun `a card made by the change is deleted, one that was merely there is not`() {
        val made = todo("Spontan", Stage.DAY, created = after)
        val elsewhere = todo("Alt", created = Instant.parse("2026-10-01T08:00:00Z"))
        val laid = block(made)

        val restore = planRestore(
            PlanSnapshot(emptyList(), emptyList(), before),
            PlanSnapshot(listOf(laid), listOf(made, elsewhere), after),
        )

        assertEquals(listOf(laid), restore.removeBlocks)
        assertEquals(listOf(made), restore.deleteItems)
    }

    @Test
    fun `a break taken off the day comes back, card first`() {
        val breakItem = Item.spontaneousBreak(30.minutes, before).copy(stage = Stage.DAY)
        val laid = block(breakItem)

        val restore = planRestore(
            PlanSnapshot(listOf(laid), listOf(breakItem), before),
            PlanSnapshot(emptyList(), emptyList(), after),
        )

        assertEquals(listOf(breakItem), restore.restoreItems)
        assertEquals(listOf(laid), restore.restoreBlocks)
    }

    @Test
    fun `a change that changed nothing is not one to undo`() {
        val item = todo("Lesen", Stage.DAY)
        val old = block(item)
        val first = PlanSnapshot(listOf(old), listOf(item), before)

        assertTrue(first.sameRowsAs(PlanSnapshot(listOf(old), listOf(item), after)))
        assertFalse(first.sameRowsAs(PlanSnapshot(listOf(old.copy(updatedAt = after)), listOf(item), after)))
    }

    // --- Einmalig · Wiederkehrend -----------------------------------------------

    @Test
    fun `a ToDo note becomes a standing task and loses what a standing task cannot carry`() {
        val note = todo("Sport", Stage.COLLECTION).copy(priority = Priority.MUST, targetDate = date, note = "Laufen")

        val recurring = note.asKind(ItemType.RECURRING, after)

        assertEquals(ItemType.RECURRING, recurring.type)
        assertNull(recurring.priority)
        assertNull(recurring.targetDate)
        assertEquals("Laufen", recurring.note)
        assertEquals(note.enteredCollectionAt, recurring.enteredCollectionAt)
        assertEquals(after, recurring.updatedAt)
    }

    @Test
    fun `and back, without a rule or an hour`() {
        val standing = Item.newQuickRecurring("Sport", before).copy(
            recurrenceRule = RecurrenceRule.Weekly(DayOfWeek.MONDAY),
            startTime = LocalTime(18, 0),
        )

        val todo = standing.asKind(ItemType.TODO, after)

        assertEquals(ItemType.TODO, todo.type)
        assertNull(todo.recurrenceRule)
        assertNull(todo.startTime)
        assertSame(todo, todo.asKind(ItemType.TODO, after))
    }
}
