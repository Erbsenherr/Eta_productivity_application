package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.domain.planning.priorityTier
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityGateTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")

    private fun todo(name: String, priority: Priority) = Item.newTodo(
        name = name,
        category = Category.FOKUS,
        priority = priority,
        targetDate = LocalDate(2026, 9, 10),
        estimatedDuration = 1.hours,
        now = now,
    )

    @Test
    fun `nothing to offer yields an empty tier`() {
        val tier = priorityTier(emptyList())
        assertNull(tier.priority)
        assertTrue(tier.items.isEmpty())
        assertEquals(0, tier.lockedBelow)
    }

    @Test
    fun `only the highest outstanding priority is offered`() {
        val candidates = listOf(
            todo("später", Priority.WANT),
            todo("dringend", Priority.URGENT_MUST),
            todo("muss", Priority.MUST),
        )
        val tier = priorityTier(candidates)

        assertEquals(Priority.URGENT_MUST, tier.priority)
        assertEquals(listOf("dringend"), tier.items.map { it.name })
        assertEquals(2, tier.lockedBelow)
    }

    @Test
    fun `everything of one priority is offered at once`() {
        val candidates = listOf(
            todo("a", Priority.MUST),
            todo("b", Priority.MUST),
            todo("c", Priority.WANT),
        )
        val tier = priorityTier(candidates)
        assertEquals(setOf("a", "b"), tier.items.map { it.name }.toSet())
    }

    @Test
    fun `the next tier unlocks once the one above is gone`() {
        // Placing the urgent one takes it out of the candidates entirely.
        val tier = priorityTier(listOf(todo("muss", Priority.MUST), todo("später", Priority.WANT)))
        assertEquals(Priority.MUST, tier.priority)
        assertEquals(1, tier.lockedBelow)
    }

    @Test
    fun `skipping a tier by hand reveals the next one`() {
        val candidates = listOf(
            todo("dringend", Priority.URGENT_MUST),
            todo("muss", Priority.MUST),
            todo("später", Priority.WANT),
        )
        assertEquals(Priority.MUST, priorityTier(candidates, skipped = 1).priority)
        assertEquals(Priority.WANT, priorityTier(candidates, skipped = 2).priority)
    }

    @Test
    fun `skipping past the last tier stays on the last tier`() {
        val candidates = listOf(todo("a", Priority.MUST), todo("b", Priority.WANT))
        val tier = priorityTier(candidates, skipped = 99)

        assertEquals(Priority.WANT, tier.priority)
        assertEquals(0, tier.lockedBelow)
    }

    @Test
    fun `items without a priority come last`() {
        val bare = Item.newQuickTodo("noch unklar", now)
        val tier = priorityTier(listOf(bare, todo("später", Priority.WANT)))

        assertEquals(Priority.WANT, tier.priority)
        assertEquals(Priority.WANT, priorityTier(listOf(bare, todo("x", Priority.WANT))).priority)
        assertNull(priorityTier(listOf(bare)).priority)
    }
}
