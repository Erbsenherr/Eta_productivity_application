package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.recurrence.RecurringSlot
import com.example.eta.domain.recurrence.Rhythm
import com.example.eta.domain.recurrence.groupRecurring
import com.example.eta.domain.recurrence.reassignRows
import com.example.eta.domain.recurrence.recurringOverlaps
import com.example.eta.domain.recurrence.sharedWeekdays
import com.example.eta.domain.setup.isOwnedBySettings
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.recurringItems
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringScheduleTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")

    private fun def(
        id: String,
        rule: RecurrenceRule,
        at: LocalTime,
        duration: Duration = 1.hours,
        name: String = id,
    ) = Item.newRecurring(
        id = id,
        name = name,
        category = Category.FOKUS,
        recurrenceRule = rule,
        startTime = at,
        estimatedDuration = duration,
        now = now,
    )

    private fun slot(rule: RecurrenceRule, at: LocalTime, duration: Duration = 1.hours) =
        RecurringSlot(rule, at, duration)

    // --- Overlaps -------------------------------------------------------------

    @Test
    fun `the same weekday and overlapping hours is an overlap, on that weekday`() {
        val sport = def("sport", RecurrenceRule.Weekly(TUESDAY), LocalTime(18, 0))
        val overlaps = recurringOverlaps(
            candidates = listOf(slot(RecurrenceRule.Daily, LocalTime(18, 30))),
            definitions = listOf(sport),
        )
        assertEquals(1, overlaps.size)
        assertEquals(setOf(TUESDAY), overlaps.single().weekdays)
    }

    @Test
    fun `touching end to start is not an overlap`() {
        val sport = def("sport", RecurrenceRule.Weekly(TUESDAY), LocalTime(18, 0))
        assertTrue(
            recurringOverlaps(listOf(slot(RecurrenceRule.Weekly(TUESDAY), LocalTime(19, 0))), listOf(sport))
                .isEmpty(),
        )
    }

    @Test
    fun `margins count, the way they do in the planner`() {
        val sport = def("sport", RecurrenceRule.Weekly(TUESDAY), LocalTime(18, 0))
        val withJourney = RecurringSlot(
            RecurrenceRule.Weekly(TUESDAY),
            LocalTime(19, 15),
            1.hours,
            travelBefore = 30.minutes,
        )
        assertEquals(1, recurringOverlaps(listOf(withJourney), listOf(sport)).size)
    }

    @Test
    fun `fortnightly tasks in opposite weeks never meet`() {
        val even = RecurrenceRule.Biweekly(TUESDAY, WeekParity.EVEN)
        val odd = RecurrenceRule.Biweekly(TUESDAY, WeekParity.ODD)
        assertTrue(even.sharedWeekdays(odd).isEmpty())
        assertEquals(setOf(TUESDAY), even.sharedWeekdays(RecurrenceRule.Weekly(TUESDAY)))
        assertTrue(
            RecurrenceRule.Monthly(TUESDAY, 1).sharedWeekdays(RecurrenceRule.Monthly(TUESDAY, 3)).isEmpty(),
        )
    }

    @Test
    fun `the rows being edited do not collide with themselves, and retired ones are gone`() {
        val sport = def("sport", RecurrenceRule.Weekly(TUESDAY), LocalTime(18, 0))
        val old = def("old", RecurrenceRule.Weekly(TUESDAY), LocalTime(18, 0)).copy(completedAt = now)
        val candidate = listOf(slot(RecurrenceRule.Weekly(TUESDAY), LocalTime(18, 0)))
        assertTrue(recurringOverlaps(candidate, listOf(sport), ignoreIds = setOf("sport")).isEmpty())
        assertTrue(recurringOverlaps(candidate, listOf(old)).isEmpty())
    }

    // --- Groups ---------------------------------------------------------------

    @Test
    fun `one definition per weekday is listed as one task`() {
        val week = listOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).map { day ->
            def("work-$day", RecurrenceRule.Weekly(day), LocalTime(9, 0), name = "Arbeit")
        }
        val groups = groupRecurring(week + def("sport", RecurrenceRule.Weekly(TUESDAY), LocalTime(18, 0)))

        assertEquals(2, groups.size)
        val work = groups.first()
        assertEquals("Arbeit", work.representative.name)
        assertEquals(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), work.weekdays)
        assertEquals(Rhythm.Weekly, work.rhythm)
    }

    @Test
    fun `a different hour or a different rhythm is a different task`() {
        val groups = groupRecurring(
            listOf(
                def("a", RecurrenceRule.Weekly(MONDAY), LocalTime(9, 0), name = "Kochen"),
                def("b", RecurrenceRule.Weekly(TUESDAY), LocalTime(12, 0), name = "Kochen"),
                def("c", RecurrenceRule.Biweekly(WEDNESDAY, WeekParity.ODD), LocalTime(9, 0), name = "Kochen"),
            ),
        )
        assertEquals(3, groups.size)
    }

    @Test
    fun `a bare note in the Sammelliste is not part of the schedule`() {
        assertTrue(groupRecurring(listOf(Item.newQuickRecurring("Joggen", now))).isEmpty())
    }

    // --- Reassigning rows on an edit -----------------------------------------

    @Test
    fun `weekdays that stay keep their rows, a dropped one is left over, a new one takes it`() {
        val mon = def("mon", RecurrenceRule.Weekly(MONDAY), LocalTime(9, 0))
        val tue = def("tue", RecurrenceRule.Weekly(TUESDAY), LocalTime(9, 0))

        // Tuesday stays, Monday goes, Friday arrives: Friday reuses Monday's row.
        val (assigned, leftover) = reassignRows(
            existing = listOf(mon, tue),
            rules = listOf(RecurrenceRule.Weekly(TUESDAY), RecurrenceRule.Weekly(FRIDAY)),
            newId = { error("no new row needed") },
        )
        assertEquals(
            listOf("tue" to RecurrenceRule.Weekly(TUESDAY), "mon" to RecurrenceRule.Weekly(FRIDAY)),
            assigned,
        )
        assertTrue(leftover.isEmpty())
    }

    @Test
    fun `fewer weekdays leave rows over, more weekdays get new ones`() {
        val mon = def("mon", RecurrenceRule.Weekly(MONDAY), LocalTime(9, 0))
        val tue = def("tue", RecurrenceRule.Weekly(TUESDAY), LocalTime(9, 0))

        val (fewer, over) = reassignRows(listOf(mon, tue), listOf(RecurrenceRule.Weekly(TUESDAY))) { "x" }
        assertEquals(listOf("tue" to RecurrenceRule.Weekly(TUESDAY)), fewer)
        assertEquals(listOf(mon), over)

        var counter = 0
        val (more, none) = reassignRows(
            listOf(mon),
            listOf(RecurrenceRule.Weekly(MONDAY), RecurrenceRule.Weekly(WEDNESDAY)),
        ) { "new-${counter++}" }
        assertEquals(listOf("mon" to RecurrenceRule.Weekly(MONDAY), "new-0" to RecurrenceRule.Weekly(WEDNESDAY)), more)
        assertTrue(none.isEmpty())
    }

    @Test
    fun `a fortnightly rhythm survives an edit that only changes the weekdays`() {
        assertEquals(
            listOf(RecurrenceRule.Biweekly(MONDAY, WeekParity.EVEN), RecurrenceRule.Biweekly(FRIDAY, WeekParity.EVEN)),
            Rhythm.Biweekly(WeekParity.EVEN).rulesFor(setOf(FRIDAY, MONDAY)),
        )
    }

    // --- What the settings still own ------------------------------------------

    @Test
    fun `the settings own exactly bed preparation and the morning`() {
        val owned = UserSetup.draft(now).recurringItems(now).filter { isOwnedBySettings(it.id) }
        assertEquals(setOf(ItemRole.BED_PREP, ItemRole.MORNING), owned.map { it.role }.toSet())
    }
}
