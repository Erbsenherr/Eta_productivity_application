package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.reevaluation.DiscardConsequence
import com.example.eta.domain.reevaluation.consequenceOf
import com.example.eta.domain.reevaluation.isOneOffBreak
import com.example.eta.domain.reevaluation.returnedToCollection
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayClosingTest {

    private val created = Instant.parse("2026-08-01T10:00:00Z")
    private val discardedAt = Instant.parse("2026-08-22T21:00:00Z")

    private fun todo() = Item.newTodo(
        name = "Steuererklärung",
        category = Category.FOKUS,
        priority = Priority.WANT,
        targetDate = LocalDate(2026, 9, 30),
        estimatedDuration = 3.hours,
        now = created,
    )

    private fun recurring() = Item.newRecurring(
        name = "Wäsche waschen",
        category = Category.NEBENBEI,
        recurrenceRule = RecurrenceRule.Weekly(DayOfWeek.MONDAY),
        startTime = LocalTime(18, 0),
        estimatedDuration = 1.hours,
        now = created,
    )

    @Test
    fun `a discarded todo goes back to the Sammelliste`() {
        assertEquals(DiscardConsequence.RETURN_TO_COLLECTION, consequenceOf(todo()))
    }

    @Test
    fun `a discarded break goes nowhere`() {
        val pause = Item.spontaneousBreak(1.hours, created)

        assertTrue(pause.isOneOffBreak)
        assertEquals(DiscardConsequence.NONE, consequenceOf(pause))
    }

    @Test
    fun `the standing Pause of the questionnaire is not a one-off break`() {
        val standing = recurring().copy(role = ItemRole.BREAK)

        assertFalse(standing.isOneOffBreak)
        assertEquals(DiscardConsequence.OFFER_MAKE_UP, consequenceOf(standing))
    }

    @Test
    fun `a discarded recurring occurrence offers a catch up`() {
        assertEquals(DiscardConsequence.OFFER_MAKE_UP, consequenceOf(recurring()))
    }

    @Test
    fun `deadlines and spends need no follow up`() {
        val deadline = Item.newDeadline("Abgabe", Category.FOKUS, discardedAt, created)
        val spend = Item.newSpend("Serie", -5.0, created)
        assertEquals(DiscardConsequence.NONE, consequenceOf(deadline))
        assertEquals(DiscardConsequence.NONE, consequenceOf(spend))
    }

    @Test
    fun `returning to the collection keeps the original Sperrliste clock`() {
        val planned = todo().copy(stage = Stage.DAY)
        val returned = planned.returnedToCollection(discardedAt)

        assertEquals(Stage.COLLECTION, returned.stage)
        assertEquals(created, returned.enteredCollectionAt)
        assertEquals(discardedAt, returned.updatedAt)
    }

    @Test
    fun `the catch up todo is named after what was missed`() {
        val makeUp = Item.newMakeUpTodo(recurring(), discardedAt)
        assertEquals("Nachholen von Wäsche waschen", makeUp.name)
    }

    @Test
    fun `the catch up todo lands in the Sammelliste with priority Muss geschehen`() {
        val makeUp = Item.newMakeUpTodo(recurring(), discardedAt)

        assertEquals(ItemType.TODO, makeUp.type)
        assertEquals(Stage.COLLECTION, makeUp.stage)
        assertEquals(Priority.MUST, makeUp.priority)
        assertEquals(discardedAt, makeUp.enteredCollectionAt)
    }

    @Test
    fun `the catch up todo inherits category and duration from the missed task`() {
        val missed = recurring()
        val makeUp = Item.newMakeUpTodo(missed, discardedAt)

        assertEquals(missed.category, makeUp.category)
        assertEquals(missed.estimatedDuration, makeUp.estimatedDuration)
        assertTrue(makeUp.isConcretized)
    }

    @Test
    fun `the catch up todo carries no target date so it can be planned right away`() {
        val makeUp = Item.newMakeUpTodo(recurring(), discardedAt)

        assertNull(makeUp.targetDate)
        assertTrue(makeUp.isAvailableOn(LocalDate(2026, 8, 23)))
    }

    @Test
    fun `a todo with a target date stays unavailable until a week before it`() {
        val item = todo()
        assertTrue(item.isAvailableOn(LocalDate(2026, 9, 23)))
        assertEquals(false, item.isAvailableOn(LocalDate(2026, 9, 22)))
    }
}
