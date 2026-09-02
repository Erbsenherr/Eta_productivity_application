package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.domain.model.Stage
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemTest {

    private val now = Instant.fromEpochSeconds(0)

    @Test
    fun `a todo unlocks one week before its target date`() {
        val item = Item.newTodo(
            name = "Steuererklärung",
            category = Category.FOKUS,
            priority = Priority.MUST,
            targetDate = LocalDate(2026, 8, 21),
            estimatedDuration = 3.hours,
            now = now,
        )
        assertEquals(LocalDate(2026, 8, 14), item.availableFrom)
    }

    @Test
    fun `quick added todos are not concretized until they have their attributes`() {
        val quick = Item.newQuickTodo("Irgendwas notieren", now)
        assertFalse(quick.isConcretized)

        val concretized = quick.copy(
            category = Category.NEBENBEI,
            priority = Priority.WANT,
            estimatedDuration = 1.hours,
        )
        assertTrue(concretized.isConcretized)
    }

    @Test
    fun `non todo types are always considered concretized`() {
        val spend = Item.newSpend("Pause", -5.0, now)
        assertTrue(spend.isConcretized)
    }

    @Test
    fun `new todos land in the Sammelliste and record when they got there`() {
        val quick = Item.newQuickTodo("Notiz", now)
        assertEquals(Stage.COLLECTION, quick.stage)
        assertNotNull(quick.enteredCollectionAt)
    }

    @Test
    fun `normalized name ignores case and surrounding whitespace`() {
        val item = Item.newQuickTodo("  Wäsche Waschen ", now)
        assertEquals("wäsche waschen", item.normalizedName)
    }
}
