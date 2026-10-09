package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemTest {

    private val now = Instant.fromEpochSeconds(0)

    @Test
    fun `a todo unlocks on the day it says, not a week before`() {
        val item = Item.newTodo(
            name = "Steuererklärung",
            category = Category.FOKUS,
            priority = Priority.MUST,
            targetDate = LocalDate(2026, 8, 21),
            estimatedDuration = 3.hours,
            now = now,
        )
        assertEquals(LocalDate(2026, 8, 21), item.availableFrom)
        assertEquals(false, item.isAvailableOn(LocalDate(2026, 8, 20)))
        assertEquals(true, item.isAvailableOn(LocalDate(2026, 8, 21)))
    }

    @Test
    fun `a quick added recurring note is not concretized until it can be expanded`() {
        val quick = Item.newQuickRecurring("Wäsche waschen", now)
        assertFalse(quick.isConcretized)

        // The three `expandRecurring` refuses to guess at, and no others: a
        // recurring task has no priority and no target date to be waiting for.
        assertFalse(quick.copy(recurrenceRule = RecurrenceRule.Daily).isConcretized)
        assertFalse(
            quick.copy(
                recurrenceRule = RecurrenceRule.Daily,
                startTime = LocalTime(18, 0),
            ).isConcretized,
        )
        assertTrue(
            quick.copy(
                recurrenceRule = RecurrenceRule.Daily,
                startTime = LocalTime(18, 0),
                estimatedDuration = 1.hours,
            ).isConcretized,
        )
    }

    @Test
    fun `a quick added recurring note waits in the Sammelliste like any other`() {
        val quick = Item.newQuickRecurring("Wäsche waschen", now)
        assertEquals(Stage.COLLECTION, quick.stage)
        // The one-month clock runs on it too — an unanswered note is hoarding.
        assertNotNull(quick.enteredCollectionAt)
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
