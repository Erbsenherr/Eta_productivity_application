package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.recurrence.weekdaysOf
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.TodoAttributes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared attribute form, and the one property that made it necessary: a card
 * that goes through it comes out finished.
 */
class ItemAttributesTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val today = LocalDate(2026, 9, 1)

    @Test
    fun `a bare quick-add gets defaults that finish it`() {
        val bare = Item.newQuickTodo("Steuer", now)
        assertFalse(bare.isConcretized)

        val attributes = TodoAttributes.of(bare, today)
        val finished = bare.concretized(
            category = attributes.category,
            priority = attributes.priority,
            targetDate = today,
            estimatedDuration = attributes.duration,
        )

        assertTrue(finished.isConcretized)
    }

    @Test
    fun `no route through the form can leave a ToDo without a category`() {
        // `TodoAttributes.category` is not nullable, which is the whole guard: the
        // form has no "ohne Kategorie", so saving cannot produce an unplannable
        // card the way a free-form editor could.
        val finished = Item.newQuickTodo("Steuer", now).concretized(
            category = TodoAttributes().category,
            priority = TodoAttributes().priority,
            targetDate = today,
            estimatedDuration = TodoAttributes().duration,
        )

        assertEquals(Category.FOKUS, finished.category)
        assertEquals(Priority.MUST, finished.priority)
        assertTrue(finished.isConcretized)
    }

    @Test
    fun `editing starts from what the card already says`() {
        val card = Item.newTodo(
            name = "Einkaufen",
            category = Category.NEBENBEI,
            priority = Priority.WANT,
            targetDate = LocalDate(2026, 9, 4),
            estimatedDuration = 90.minutes,
            now = now,
        ).copy(travelBefore = 15.minutes, breakAfter = 20.minutes, endSound = true)

        val attributes = TodoAttributes.of(card, today)

        assertEquals(Category.NEBENBEI, attributes.category)
        assertEquals(Priority.WANT, attributes.priority)
        assertEquals(LocalDate(2026, 9, 4), attributes.unlockFrom)
        assertEquals(90.minutes, attributes.duration)
        assertEquals(15.minutes, attributes.travelBefore)
        assertEquals(20.minutes, attributes.breakAfter)
        assertTrue(attributes.endSound)
    }

    @Test
    fun `an unlock day already past is kept, because the ban clock counts from it`() {
        val overdue = Item.newTodo(
            name = "Steuer",
            category = Category.FOKUS,
            priority = Priority.MUST,
            targetDate = LocalDate(2026, 8, 20),
            estimatedDuration = 1.hours,
            now = now,
        )

        // Dropping it on an edit would wind the one-month clock back to the day
        // the card was written down.
        assertEquals(LocalDate(2026, 8, 20), TodoAttributes.of(overdue, today).unlockFrom)
    }

    @Test
    fun `a new card is free at once and carries no unlock day`() {
        assertEquals(null, TodoAttributes().unlockFrom)
    }

    @Test
    fun `a bare recurring note is offered a weekday rather than none`() {
        val bare = Item.newQuickRecurring("Joggen", now)
        assertFalse(bare.isConcretized)

        // An empty set produces no rule at all, so the form must never open on one:
        // "Sichern" would be enabled with nothing to save.
        assertTrue(RecurringAttributes.of(bare).weekdays.isNotEmpty())
    }

    @Test
    fun `a rule reads back as the weekdays it fires on`() {
        assertEquals(setOf(DayOfWeek.TUESDAY), RecurrenceRule.Weekly(DayOfWeek.TUESDAY).weekdaysOf())
        assertEquals(DayOfWeek.entries.toSet(), RecurrenceRule.Daily.weekdaysOf())
    }

    @Test
    fun `recurring attributes carry a card's own start and length`() {
        val card = Item.newRecurring(
            name = "Joggen",
            category = Category.ACHTSAM,
            recurrenceRule = RecurrenceRule.Weekly(DayOfWeek.THURSDAY),
            startTime = LocalTime(7, 30),
            estimatedDuration = 45.minutes,
            now = now,
        ).copy(travelBefore = 10.minutes)

        val attributes = RecurringAttributes.of(card)

        assertEquals(Category.ACHTSAM, attributes.category)
        assertEquals(setOf(DayOfWeek.THURSDAY), attributes.weekdays)
        assertEquals(LocalTime(7, 30), attributes.startTime)
        assertEquals(45.minutes, attributes.duration)
        assertEquals(10.minutes, attributes.travelBefore)
    }
}
