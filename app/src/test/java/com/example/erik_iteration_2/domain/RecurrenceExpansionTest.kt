package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.RecurrenceRule
import com.example.erik_iteration_2.domain.model.WeekParity
import com.example.erik_iteration_2.domain.recurrence.expandRecurring
import com.example.erik_iteration_2.domain.recurrence.isoWeekNumber
import com.example.erik_iteration_2.domain.recurrence.matches
import com.example.erik_iteration_2.domain.recurrence.occurrencesBetween
import com.example.erik_iteration_2.domain.recurrence.rulesForWeekdays
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceExpansionTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")

    private fun recurring(rule: RecurrenceRule) = Item.newRecurring(
        name = "Wäsche waschen",
        category = Category.NEBENBEI,
        recurrenceRule = rule,
        startTime = LocalTime(18, 0),
        estimatedDuration = 1.hours,
        now = now,
    )

    @Test
    fun `all seven weekdays collapse into the daily rule`() {
        assertEquals(
            listOf(RecurrenceRule.Daily),
            rulesForWeekdays(DayOfWeek.entries.toSet()),
        )
    }

    @Test
    fun `fewer weekdays become one weekly rule each, in weekday order`() {
        assertEquals(
            listOf(
                RecurrenceRule.Weekly(DayOfWeek.TUESDAY),
                RecurrenceRule.Weekly(DayOfWeek.THURSDAY),
            ),
            rulesForWeekdays(setOf(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY)),
        )
    }

    @Test
    fun `no weekday is no answer, and yields no definition`() {
        assertTrue(rulesForWeekdays(emptySet()).isEmpty())
    }

    @Test
    fun `weekly fires on its weekday only`() {
        val rule = RecurrenceRule.Weekly(DayOfWeek.MONDAY)
        assertTrue(rule.matches(LocalDate(2026, 9, 7)))   // Montag
        assertFalse(rule.matches(LocalDate(2026, 9, 8)))  // Dienstag
    }

    @Test
    fun `daily fires on every date in the range`() {
        val dates = RecurrenceRule.Daily
            .occurrencesBetween(LocalDate(2026, 9, 1), LocalDate(2026, 9, 7))
        assertEquals(7, dates.size)
    }

    @Test
    fun `weekly yields one occurrence per week`() {
        val rule = RecurrenceRule.Weekly(DayOfWeek.MONDAY)
        val dates = rule.occurrencesBetween(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        assertEquals(
            listOf(
                LocalDate(2026, 9, 7),
                LocalDate(2026, 9, 14),
                LocalDate(2026, 9, 21),
                LocalDate(2026, 9, 28),
            ),
            dates,
        )
    }

    @Test
    fun `biweekly follows the ISO week parity`() {
        val evenRule = RecurrenceRule.Biweekly(DayOfWeek.MONDAY, WeekParity.EVEN)
        val oddRule = RecurrenceRule.Biweekly(DayOfWeek.MONDAY, WeekParity.ODD)

        val sept7 = LocalDate(2026, 9, 7)
        val isEvenWeek = sept7.isoWeekNumber() % 2 == 0

        assertEquals(isEvenWeek, evenRule.matches(sept7))
        assertEquals(!isEvenWeek, oddRule.matches(sept7))
    }

    @Test
    fun `biweekly skips every other week`() {
        val rule = RecurrenceRule.Biweekly(DayOfWeek.MONDAY, WeekParity.EVEN)
        val dates = rule.occurrencesBetween(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        assertEquals(2, dates.size)
        assertEquals(14, dates[0].daysUntil(dates[1]))
    }

    @Test
    fun `monthly picks the nth weekday of the month`() {
        val rule = RecurrenceRule.Monthly(DayOfWeek.MONDAY, weekOfMonth = 2)
        val dates = rule.occurrencesBetween(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30))
        assertEquals(listOf(LocalDate(2026, 9, 14)), dates)
    }

    @Test
    fun `expansion creates one square cornered block per occurrence`() {
        val item = recurring(RecurrenceRule.Weekly(DayOfWeek.MONDAY))
        val blocks = expandRecurring(
            definitions = listOf(item),
            from = LocalDate(2026, 9, 1),
            to = LocalDate(2026, 9, 30),
            existing = emptySet(),
            now = now,
        )

        assertEquals(4, blocks.size)
        assertTrue(blocks.all { it.origin == BlockOrigin.RECURRING })
        assertTrue(blocks.none { it.isMovable })
        assertTrue(blocks.all { it.start == LocalTime(18, 0) })
        assertTrue(blocks.all { it.itemId == item.id })
    }

    @Test
    fun `expansion is idempotent for occurrences that already exist`() {
        val item = recurring(RecurrenceRule.Weekly(DayOfWeek.MONDAY))
        val alreadyThere = setOf(
            item.id to LocalDate(2026, 9, 7),
            item.id to LocalDate(2026, 9, 14),
        )

        val blocks = expandRecurring(
            definitions = listOf(item),
            from = LocalDate(2026, 9, 1),
            to = LocalDate(2026, 9, 30),
            existing = alreadyThere,
            now = now,
        )

        assertEquals(
            listOf(LocalDate(2026, 9, 21), LocalDate(2026, 9, 28)),
            blocks.map { it.date },
        )
    }

    @Test
    fun `definitions without a start time are skipped rather than guessed`() {
        val incomplete = recurring(RecurrenceRule.Weekly(DayOfWeek.MONDAY))
            .copy(startTime = null)

        val blocks = expandRecurring(
            definitions = listOf(incomplete),
            from = LocalDate(2026, 9, 1),
            to = LocalDate(2026, 9, 30),
            existing = emptySet(),
            now = now,
        )

        assertTrue(blocks.isEmpty())
    }
}
