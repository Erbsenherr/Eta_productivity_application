package com.example.eta.domain

import com.example.eta.domain.model.Subtask
import com.example.eta.domain.subtask.MergePart
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.domain.subtask.drafts
import com.example.eta.domain.subtask.foldedWith
import com.example.eta.domain.subtask.orderedParts
import com.example.eta.domain.subtask.mergedDuration
import com.example.eta.domain.subtask.mergedGroup
import com.example.eta.domain.subtask.moved
import com.example.eta.domain.subtask.progress
import com.example.eta.domain.subtask.remainderDuration
import com.example.eta.domain.subtask.stillOpen
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind grouping, and the two decisions it records.
 *
 * **Margins stay margins.** The group's length is the sum of the *pure* durations,
 * and only the outer journeys survive — absorbing a commute into the duration
 * would pay points for driving and make grouping a way to earn them.
 *
 * **The remainder is a share.** A subtask has no length of its own, so what the
 * evening carries over can only be proportional.
 */
class SubtaskTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")

    private fun row(id: String, position: Int, name: String = id) = Subtask(
        id = id,
        itemId = "group",
        position = position,
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    private fun part(
        name: String,
        duration: kotlin.time.Duration,
        travel: kotlin.time.Duration? = null,
        back: kotlin.time.Duration? = null,
        pause: kotlin.time.Duration? = null,
    ) = MergePart(
        name = name,
        duration = duration,
        travelBefore = travel,
        returnAfter = back,
        breakAfter = pause,
    )

    @Test
    fun `merged duration is the sum of the pure durations`() {
        val merged = mergedGroup(
            listOf(
                part("Einkaufen", 45.minutes, travel = 15.minutes, pause = 25.minutes),
                part("Apotheke", 30.minutes, travel = 10.minutes, back = 10.minutes),
            ),
        )

        assertEquals(75.minutes, merged.duration)
    }

    @Test
    fun `the group inherits the outer margins and drops the inner ones`() {
        val merged = mergedGroup(
            listOf(
                part("Einkaufen", 45.minutes, travel = 15.minutes, back = 5.minutes, pause = 25.minutes),
                part("Apotheke", 30.minutes, travel = 10.minutes, back = 20.minutes, pause = 15.minutes),
            ),
        )

        assertEquals(15.minutes, merged.travelBefore)
        assertEquals(20.minutes, merged.returnAfter)
        assertEquals(15.minutes, merged.breakAfter)
    }

    @Test
    fun `a group without margins anywhere gets none`() {
        val merged = mergedGroup(listOf(part("A", 30.minutes), part("B", 30.minutes)))

        assertNull(merged.travelBefore)
        assertNull(merged.returnAfter)
        assertNull(merged.breakAfter)
    }

    @Test
    fun `the parts become subtasks in the order they were given, without ids`() {
        val merged = mergedGroup(listOf(part("Erst", 30.minutes), part("Dann", 30.minutes)))

        assertEquals(listOf("Erst", "Dann"), merged.subtasks.map { it.name })
        assertTrue(merged.subtasks.all { it.id == null })
    }

    @Test
    fun `a merge never falls below the shortest block the planner can hold`() {
        assertEquals(15.minutes, mergedDuration(listOf(5.minutes, 5.minutes)))
    }

    @Test
    fun `moving a row keeps every other row and its order`() {
        val list = listOf("a", "b", "c", "d")

        assertEquals(listOf("b", "c", "a", "d"), list.moved(0, 2))
        assertEquals(listOf("a", "d", "b", "c"), list.moved(3, 1))
        assertEquals(list, list.moved(1, 1))
        assertEquals(list, list.moved(1, 9))
    }

    @Test
    fun `progress counts the ticks of that one day`() {
        val rows = listOf(row("one", 0), row("two", 1), row("three", 2))

        val half = rows.progress(setOf("one"))
        assertEquals(1, half.done)
        assertEquals(3, half.total)
        assertEquals(2, half.open)
        assertFalse(half.allDone)

        assertTrue(rows.progress(setOf("one", "two", "three")).allDone)
        assertFalse(emptyList<Subtask>().progress(emptySet()).allDone)
        assertTrue(emptyList<Subtask>().progress(emptySet()).isEmpty)
    }

    @Test
    fun `what is still open comes back in the builder's order`() {
        val rows = listOf(row("c", 2), row("a", 0), row("b", 1))

        assertEquals(listOf("a", "c"), rows.stillOpen(setOf("b")).map { it.id })
    }

    @Test
    fun `drafts carry the id, so saving keeps the ticks that stand against a row`() {
        val rows = listOf(row("b", 1), row("a", 0))

        assertEquals(listOf("a", "b"), rows.drafts().map { it.id })
    }

    @Test
    fun `a plain task joins a group at the end, as one step`() {
        val group = listOf(row("a", 0, "Erst"), row("b", 1, "Dann"))

        val folded = group.foldedWith("Zuletzt", "mit Notiz")

        assertEquals(listOf("Erst", "Dann", "Zuletzt"), folded.map { it.name })
        // The rows that were already saved keep their ids, so their ticks survive;
        // the newcomer has none yet.
        assertEquals(listOf("a", "b", null), folded.map { it.id })
        assertEquals("mit Notiz", folded.last().note)
    }

    @Test
    fun `a dissolved group's steps keep their order inside the survivor`() {
        val survivor = listOf(row("s", 0, "Survivor"))
        val dissolved = listOf(row("x", 1, "Zweitens"), row("y", 0, "Erstens"))

        val folded = survivor.foldedWith(dissolved.drafts())

        assertEquals(listOf("Survivor", "Erstens", "Zweitens"), folded.map { it.name })
        // Copies, not moves: the dissolved rows belong to a retired card.
        assertEquals(listOf("s", null, null), folded.map { it.id })
    }

    @Test
    fun `the margins follow whichever part the builder put first and last`() {
        val parts = listOf(
            part("Einkaufen", 45.minutes, travel = 15.minutes, back = 5.minutes, pause = 25.minutes),
            part("Apotheke", 30.minutes, travel = 10.minutes, back = 20.minutes, pause = 15.minutes),
        )
        // The user dragged the Apotheke to the top before saving.
        val reordered = listOf(SubtaskDraft(name = "Apotheke"), SubtaskDraft(name = "Einkaufen"))

        val merged = mergedGroup(orderedParts(parts, reordered))

        assertEquals(10.minutes, merged.travelBefore)
        assertEquals(5.minutes, merged.returnAfter)
        assertEquals(25.minutes, merged.breakAfter)
        assertEquals(listOf("Apotheke", "Einkaufen"), merged.subtasks.map { it.name })
    }

    @Test
    fun `a step added in the builder cannot change which margins the group gets`() {
        val parts = listOf(part("Erst", 30.minutes, travel = 15.minutes), part("Dann", 30.minutes))
        val withExtra = listOf(
            SubtaskDraft(name = "Von Hand"),
            SubtaskDraft(name = "Erst"),
            SubtaskDraft(name = "Dann"),
        )

        val merged = mergedGroup(orderedParts(parts, withExtra))

        assertEquals(15.minutes, merged.travelBefore)
    }

    @Test
    fun `the remainder is the share that is left, rounded to five minutes`() {
        // Three of four steps open, out of two hours: 90 minutes.
        assertEquals(90.minutes, remainderDuration(2.hours, open = 3, total = 4))
        // 50 minutes of three, two open: 33.3 → 35.
        assertEquals(35.minutes, remainderDuration(50.minutes, open = 2, total = 3))
    }

    @Test
    fun `a remainder is never longer than its group nor shorter than a block`() {
        assertEquals(1.hours, remainderDuration(1.hours, open = 4, total = 4))
        assertEquals(15.minutes, remainderDuration(1.hours, open = 1, total = 9))
        assertEquals(10.minutes, remainderDuration(10.minutes, open = 1, total = 3))
        assertEquals(15.minutes, remainderDuration(1.hours, open = 0, total = 3))
    }
}
