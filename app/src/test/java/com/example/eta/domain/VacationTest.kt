package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Vacation
import com.example.eta.domain.model.VacationRule
import com.example.eta.domain.model.VacationTreatment
import com.example.eta.domain.recurrence.expandRecurring
import com.example.eta.domain.vacation.VacationPlan
import com.example.eta.domain.vacation.startOverrideFor
import com.example.eta.domain.vacation.suspends
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VacationTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")

    private val sport = Item.newRecurring(
        id = "sport",
        name = "Sport",
        category = Category.FOKUS,
        recurrenceRule = RecurrenceRule.Daily,
        startTime = LocalTime(18, 0),
        estimatedDuration = 1.hours,
        now = now,
    )

    private val cooking = Item.newRecurring(
        id = "cooking",
        name = "Kochen",
        category = Category.NEBENBEI,
        recurrenceRule = RecurrenceRule.Daily,
        startTime = LocalTime(12, 0),
        estimatedDuration = 1.hours,
        now = now,
    )

    /** A week off, from the 7th to the 13th inclusive. */
    private fun holiday(rules: List<VacationRule>) = VacationPlan(
        vacation = Vacation(
            id = "urlaub",
            label = "Urlaub",
            from = LocalDate(2026, 9, 7),
            to = LocalDate(2026, 9, 13),
            createdAt = now,
            updatedAt = now,
        ),
        rules = rules,
    )

    private fun rule(itemId: String, treatment: VacationTreatment, at: LocalTime? = null) =
        VacationRule(vacationId = "urlaub", itemId = itemId, treatment = treatment, movedStart = at)

    @Test
    fun `both ends of the range are inside it`() {
        val plan = holiday(emptyList())
        assertTrue(plan.covers(LocalDate(2026, 9, 7)))
        assertTrue(plan.covers(LocalDate(2026, 9, 13)))
        assertFalse(plan.covers(LocalDate(2026, 9, 6)))
        assertFalse(plan.covers(LocalDate(2026, 9, 14)))
    }

    @Test
    fun `a suspended task produces no block during the holiday`() {
        val plans = listOf(holiday(listOf(rule("sport", VacationTreatment.SUSPEND))))

        val blocks = expandRecurring(
            definitions = listOf(sport),
            from = LocalDate(2026, 9, 5),
            to = LocalDate(2026, 9, 15),
            existing = emptySet(),
            now = now,
            vacations = plans,
        )

        // The two days before and the two after; the week between is off.
        assertEquals(
            listOf(
                LocalDate(2026, 9, 5),
                LocalDate(2026, 9, 6),
                LocalDate(2026, 9, 14),
                LocalDate(2026, 9, 15),
            ),
            blocks.map { it.date },
        )
    }

    @Test
    fun `a task with no rule carries on as usual`() {
        val plans = listOf(holiday(listOf(rule("sport", VacationTreatment.SUSPEND))))

        val blocks = expandRecurring(
            definitions = listOf(cooking),
            from = LocalDate(2026, 9, 7),
            to = LocalDate(2026, 9, 13),
            existing = emptySet(),
            now = now,
            vacations = plans,
        )
        assertEquals(7, blocks.size)
        assertTrue(blocks.all { it.start == LocalTime(12, 0) })
    }

    @Test
    fun `a moved task keeps happening, at the other time`() {
        val plans = listOf(
            holiday(listOf(rule("sport", VacationTreatment.MOVE, LocalTime(10, 0)))),
        )

        val blocks = expandRecurring(
            definitions = listOf(sport),
            from = LocalDate(2026, 9, 6),
            to = LocalDate(2026, 9, 14),
            existing = emptySet(),
            now = now,
            vacations = plans,
        )

        val duringHoliday = blocks.filter { it.date in LocalDate(2026, 9, 7)..LocalDate(2026, 9, 13) }
        assertEquals(7, duringHoliday.size)
        assertTrue(duringHoliday.all { it.start == LocalTime(10, 0) })

        // Outside it, the ordinary time is back — nothing had to be restored.
        assertTrue(
            blocks.filterNot { it.date in LocalDate(2026, 9, 7)..LocalDate(2026, 9, 13) }
                .all { it.start == LocalTime(18, 0) },
        )
    }

    @Test
    fun `a move without a time changes nothing rather than dropping the task`() {
        val plans = listOf(holiday(listOf(rule("sport", VacationTreatment.MOVE, at = null))))
        assertNull(plans.startOverrideFor("sport", LocalDate(2026, 9, 8)))
        assertFalse(plans.suspends("sport", LocalDate(2026, 9, 8)))
    }

    @Test
    fun `rules do not leak outside the range`() {
        val plans = listOf(holiday(listOf(rule("sport", VacationTreatment.SUSPEND))))
        assertFalse(plans.suspends("sport", LocalDate(2026, 9, 6)))
        assertTrue(plans.suspends("sport", LocalDate(2026, 9, 7)))
    }

    @Test
    fun `two holidays in one expanded range are each honoured`() {
        val second = VacationPlan(
            vacation = Vacation(
                id = "zweiter",
                label = "Kurztrip",
                from = LocalDate(2026, 9, 20),
                to = LocalDate(2026, 9, 21),
                createdAt = now,
                updatedAt = now,
            ),
            rules = listOf(
                VacationRule(
                    vacationId = "zweiter",
                    itemId = "sport",
                    treatment = VacationTreatment.MOVE,
                    movedStart = LocalTime(7, 0),
                ),
            ),
        )
        val plans = listOf(holiday(listOf(rule("sport", VacationTreatment.SUSPEND))), second)

        assertTrue(plans.suspends("sport", LocalDate(2026, 9, 8)))
        assertEquals(LocalTime(7, 0), plans.startOverrideFor("sport", LocalDate(2026, 9, 20)))
        assertNull(plans.startOverrideFor("sport", LocalDate(2026, 9, 25)))
    }

    @Test
    fun `a holiday is over once its last day has passed`() {
        val vacation = holiday(emptyList()).vacation
        assertFalse(vacation.hasEnded(LocalDate(2026, 9, 13)))
        assertTrue(vacation.hasEnded(LocalDate(2026, 9, 14)))
    }
}
