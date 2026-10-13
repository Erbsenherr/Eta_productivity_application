package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemExtras
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.model.extras
import com.example.eta.domain.model.withExtras
import com.example.eta.domain.recurrence.Rhythm
import com.example.eta.domain.recurrence.matches
import com.example.eta.domain.recurrence.rulesWithTimes
import com.example.eta.domain.recurrence.weekTaskSpans
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.bedPrepIsNextDay
import com.example.eta.domain.setup.withBedPrepNextDay
import com.example.eta.domain.setup.withSleepNextDay
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 46: the "Folgetag" box beside the hours of a night, a rhythm of every
 * other week, a colour of the task's own, and the Wochenschema's coloured bars.
 */
class SetupAndRhythmTest {

    private val now = Instant.parse("2026-10-13T06:00:00Z")

    // --- "Folgetag" ------------------------------------------------------------------

    private val evening = NightTimes(LocalTime(22, 30), LocalTime(23, 0), LocalTime(7, 0))

    @Test
    fun `the box follows the hour - before midnight it is not ticked`() {
        assertFalse(evening.sleepsAfterMidnight)
        assertFalse(evening.bedPrepIsNextDay)

        val late = NightTimes(LocalTime(0, 30), LocalTime(1, 0), LocalTime(8, 0))
        assertTrue(late.sleepsAfterMidnight)
        assertTrue(late.bedPrepIsNextDay)
    }

    @Test
    fun `ticking it beside the bedtime moves the bedtime past twelve, and winding down with it`() {
        val moved = evening.withSleepNextDay(true)

        assertEquals(LocalTime(0, 0), moved.sleep)
        assertTrue(moved.sleepsAfterMidnight)
        // Still half an hour of winding down, now from half past eleven.
        assertEquals(LocalTime(23, 30), moved.bedPrep)
        assertEquals(30.minutes, moved.bedPrepDuration())
        // And unticking brings it back to the evening.
        assertEquals(LocalTime(23, 0), moved.withSleepNextDay(false).sleep)
    }

    @Test
    fun `ticking it beside winding down takes the bedtime across midnight too`() {
        val moved = evening.withBedPrepNextDay(true)

        assertEquals(LocalTime(0, 30), moved.bedPrep)
        assertEquals(LocalTime(1, 0), moved.sleep)
        assertTrue(moved.bedPrepIsNextDay)
        assertEquals(30.minutes, moved.bedPrepDuration())
    }

    @Test
    fun `a bedtime already past twelve stays where it is when winding down joins it`() {
        val late = NightTimes(LocalTime(23, 30), LocalTime(1, 0), LocalTime(8, 0))

        val moved = late.withBedPrepNextDay(true)

        assertEquals(LocalTime(0, 30), moved.bedPrep)
        assertEquals(LocalTime(1, 0), moved.sleep)
        // Unticked again, winding down starts before midnight and the bedtime stays.
        assertEquals(LocalTime(23, 30), moved.withBedPrepNextDay(false).bedPrep)
        assertEquals(LocalTime(1, 0), moved.withBedPrepNextDay(false).sleep)
    }

    @Test
    fun `a box set to what it already says changes nothing`() {
        assertEquals(evening, evening.withSleepNextDay(false))
        assertEquals(evening, evening.withBedPrepNextDay(false))
    }

    // --- every other week --------------------------------------------------------------

    @Test
    fun `an even-week rhythm lays one rule per weekday down, each of that parity`() {
        val rules = rulesWithTimes(
            Rhythm.Biweekly(WeekParity.EVEN),
            setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            LocalTime(18, 0),
            emptyMap(),
        ).map { it.first }

        assertEquals(
            listOf(
                RecurrenceRule.Biweekly(DayOfWeek.TUESDAY, WeekParity.EVEN),
                RecurrenceRule.Biweekly(DayOfWeek.THURSDAY, WeekParity.EVEN),
            ),
            rules,
        )
    }

    @Test
    fun `even and odd calendar weeks take turns`() {
        val even = RecurrenceRule.Biweekly(DayOfWeek.TUESDAY, WeekParity.EVEN)
        val odd = RecurrenceRule.Biweekly(DayOfWeek.TUESDAY, WeekParity.ODD)
        // 13 October 2026 is a Tuesday in calendar week 42.
        val tuesday = LocalDate(2026, 10, 13)
        val next = LocalDate(2026, 10, 20)

        assertTrue(even.matches(tuesday))
        assertFalse(odd.matches(tuesday))
        assertFalse(even.matches(next))
        assertTrue(odd.matches(next))
    }

    // --- extras: colour and rhythm --------------------------------------------------------

    private fun task(id: String, rule: RecurrenceRule, start: LocalTime = LocalTime(18, 0)) = Item.newRecurring(
        id = id,
        name = id,
        category = Category.FOKUS,
        recurrenceRule = rule,
        startTime = start,
        estimatedDuration = 1.hours,
        now = now,
    )

    @Test
    fun `a colour of its own is an extra, and the rhythm is read but never written by it`() {
        val weekly = task("sport", RecurrenceRule.Weekly(DayOfWeek.TUESDAY))
        assertNull(weekly.extras.color)
        assertEquals(Rhythm.Weekly, weekly.extras.rhythm)

        val teal = 0xFF1F9E8F.toInt()
        val written = weekly.withExtras(
            ItemExtras(color = teal, rhythm = Rhythm.Biweekly(WeekParity.ODD)),
        )
        assertEquals(teal, written.color)
        // The rule is the save paths' to write; an extra does not touch it.
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.TUESDAY), written.recurrenceRule)

        val fortnightly = task("chor", RecurrenceRule.Biweekly(DayOfWeek.MONDAY, WeekParity.EVEN))
        assertEquals(Rhythm.Biweekly(WeekParity.EVEN), fortnightly.extras.rhythm)
    }

    // --- the Wochenschema's bars ------------------------------------------------------------

    @Test
    fun `every task is a bar of its own on each of its weekdays`() {
        val sport = task("sport", RecurrenceRule.Weekly(DayOfWeek.TUESDAY))
        val daily = task("lesen", RecurrenceRule.Daily, start = LocalTime(20, 0))

        val spans = weekTaskSpans(listOf(sport, daily))

        assertEquals(listOf("sport", "lesen"), spans.getValue(DayOfWeek.TUESDAY).map { it.second.id })
        assertEquals(18 * 60 until 19 * 60, spans.getValue(DayOfWeek.TUESDAY).first().first)
        assertEquals(listOf("lesen"), spans.getValue(DayOfWeek.SUNDAY).map { it.second.id })
    }

    @Test
    fun `a task that is ended draws no bar`() {
        val ended = task("alt", RecurrenceRule.Weekly(DayOfWeek.MONDAY)).copy(completedAt = now)

        assertTrue(weekTaskSpans(listOf(ended)).values.all { it.isEmpty() })
    }
}
