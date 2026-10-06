package com.example.eta.domain

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.recurrence.expandRecurring
import com.example.eta.domain.reward.yieldOf
import com.example.eta.domain.setup.DailySlot
import com.example.eta.domain.setup.HousekeepingPlan
import com.example.eta.domain.setup.MealPlan
import com.example.eta.domain.setup.MindfulnessPlan
import com.example.eta.domain.setup.SetupLabels
import com.example.eta.domain.setup.TimeSpan
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WeeklySlot
import com.example.eta.domain.setup.WorkBlock
import com.example.eta.domain.setup.WorkSchedule
import com.example.eta.domain.setup.recurringItems
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupItemsTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")

    private fun setup() = UserSetup.draft(now)

    @Test
    fun `the free time task carries the role the settlement looks for`() {
        val freeTime = setup().recurringItems(now).first { it.name == SetupLabels.FREE_TIME }
        assertEquals(ItemRole.FREE_TIME, freeTime.role)
    }

    @Test
    fun `every generated task says what part it plays`() {
        // The roles are what the rules key off; a generated task without one would
        // be invisible to them.
        assertTrue(setup().recurringItems(now).all { it.role != null })
    }

    @Test
    fun `sleep never becomes a task`() {
        val items = setup().recurringItems(now)
        assertTrue(items.none { it.name == SetupLabels.SLEEP })
    }

    @Test
    fun `every generated item is a recurring definition with a stable id`() {
        val items = setup().recurringItems(now)
        assertTrue(items.all { it.type == ItemType.RECURRING })
        assertTrue(items.all { it.id.startsWith("setup:") })

        // Same answers, same ids — that is what keeps a second run from duplicating.
        assertEquals(items.map { it.id }, setup().recurringItems(now).map { it.id })
        assertEquals(items.size, items.map { it.id }.distinct().size)
    }

    @Test
    fun `the frame of the day occupies time without paying points`() {
        val items = setup().recurringItems(now)
        val frame = listOf(
            SetupLabels.BED_PREP,
            SetupLabels.MORNING,
            SetupLabels.PAUSE,
            SetupLabels.FREE_TIME,
        )
        frame.forEach { label ->
            val item = items.firstOrNull { it.name == label }
            assertNotNull("$label fehlt", item)
            assertNull("$label sollte keine Kategorie haben", item!!.category)
        }

        val freeTime = items.first { it.name == SetupLabels.FREE_TIME }
        val block = expandRecurring(
            definitions = listOf(freeTime),
            from = LocalDate(2026, 9, 1),
            to = LocalDate(2026, 9, 1),
            existing = emptySet(),
            now = now,
        ).single()
        assertEquals(0.0, yieldOf(freeTime, block), 0.0001)
    }

    @Test
    fun `work becomes one definition per weekday and stretch`() {
        val items = setup()
            .copy(
                work = WorkSchedule.PerWeekday(
                    mapOf(
                        DayOfWeek.MONDAY to listOf(
                            WorkBlock(TimeSpan(LocalTime(8, 0), LocalTime(12, 0))),
                            WorkBlock(TimeSpan(LocalTime(14, 0), LocalTime(18, 0))),
                        ),
                        DayOfWeek.FRIDAY to listOf(
                            WorkBlock(TimeSpan(LocalTime(9, 0), LocalTime(13, 0))),
                        ),
                    ),
                ),
            )
            .recurringItems(now)
            .filter { it.name == SetupLabels.WORK }

        assertEquals(3, items.size)
        assertTrue(items.all { it.category == Category.FOKUS })
        assertEquals(
            listOf(
                RecurrenceRule.Weekly(DayOfWeek.MONDAY),
                RecurrenceRule.Weekly(DayOfWeek.MONDAY),
                RecurrenceRule.Weekly(DayOfWeek.FRIDAY),
            ),
            items.map { it.recurrenceRule },
        )
        assertEquals(4.hours, items.first().estimatedDuration)
    }

    @Test
    fun `a working day with a break becomes work, break, work`() {
        val items = setup()
            .copy(
                work = WorkSchedule.PerWeekday(
                    mapOf(
                        DayOfWeek.MONDAY to listOf(
                            WorkBlock(
                                span = TimeSpan(LocalTime(9, 0), LocalTime(17, 0)),
                                pause = TimeSpan(LocalTime(12, 30), LocalTime(13, 15)),
                            ),
                        ),
                    ),
                ),
            )
            .recurringItems(now)
            .filter { it.name == SetupLabels.WORK || it.name == SetupLabels.PAUSE }

        assertEquals(
            listOf(SetupLabels.WORK, SetupLabels.PAUSE, SetupLabels.WORK),
            items.map { it.name },
        )
        assertEquals(listOf(LocalTime(9, 0), LocalTime(12, 30), LocalTime(13, 15)), items.map { it.startTime })
        // The break belongs to the frame of the day and pays nothing.
        assertNull(items[1].category)
        assertEquals(Category.FOKUS, items[0].category)
        assertEquals(Category.FOKUS, items[2].category)
        assertEquals(items.map { it.id }.distinct().size, items.size)
    }

    @Test
    fun `the uniform answer fills the five workdays`() {
        val items = setup()
            .copy(
                work = WorkSchedule.EveryWorkday(
                    WorkBlock(TimeSpan(LocalTime(9, 0), LocalTime(17, 0))),
                ),
            )
            .recurringItems(now)
            .filter { it.name == SetupLabels.WORK }

        assertEquals(5, items.size)
        assertTrue(items.none { it.recurrenceRule == RecurrenceRule.Weekly(DayOfWeek.SATURDAY) })
    }

    @Test
    fun `no work means no break either`() {
        val items = setup().copy(work = WorkSchedule.None).recurringItems(now)
        assertTrue(items.none { it.name == SetupLabels.PAUSE })
        assertTrue(items.none { it.name == SetupLabels.WORK })
    }

    @Test
    fun `biweekly housekeeping keeps its parity`() {
        val item = setup()
            .copy(
                housekeeping = HousekeepingPlan.Biweekly(
                    WeeklySlot(DayOfWeek.SATURDAY, LocalTime(10, 0), 2.hours),
                    WeekParity.ODD,
                ),
            )
            .recurringItems(now)
            .first { it.name == SetupLabels.HOUSEKEEPING }

        assertEquals(
            RecurrenceRule.Biweekly(DayOfWeek.SATURDAY, WeekParity.ODD),
            item.recurrenceRule,
        )
        assertEquals(Category.NEBENBEI, item.category)
    }

    @Test
    fun `a slot on several weekdays becomes one definition per day`() {
        val items = setup()
            .copy(
                sport = WeeklySlot(
                    setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
                    LocalTime(17, 0),
                    1.hours,
                ),
            )
            .recurringItems(now)
            .filter { it.name == SetupLabels.SPORT }

        assertEquals(2, items.size)
        assertEquals(
            listOf(
                RecurrenceRule.Weekly(DayOfWeek.TUESDAY),
                RecurrenceRule.Weekly(DayOfWeek.FRIDAY),
            ),
            items.map { it.recurrenceRule },
        )
        // Distinct ids, or the second would overwrite the first on re-answering.
        assertEquals(2, items.map { it.id }.toSet().size)
    }

    @Test
    fun `mealprep and daily cooking produce different rules`() {
        val prep = setup()
            .copy(
                meals = MealPlan.MealPrep(WeeklySlot(DayOfWeek.SUNDAY, LocalTime(11, 0), 3.hours)),
            )
            .recurringItems(now)
            .first { it.name == SetupLabels.MEAL_PREP }
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.SUNDAY), prep.recurrenceRule)

        val cooking = setup()
            .copy(
                meals = MealPlan.DailyCooking(
                    listOf(
                        DailySlot(LocalTime(12, 0), 1.hours),
                        DailySlot(LocalTime(18, 0), 1.hours),
                    ),
                ),
            )
            .recurringItems(now)
            .filter { it.name == SetupLabels.COOKING }
        assertEquals(2, cooking.size)
        assertTrue(cooking.all { it.recurrenceRule == RecurrenceRule.Daily })
    }

    @Test
    fun `an answer of no produces no definition`() {
        val items = setup()
            .copy(sport = null, housekeeping = null, mindfulness = null)
            .recurringItems(now)

        assertTrue(items.none { it.name == SetupLabels.SPORT })
        assertTrue(items.none { it.name == SetupLabels.HOUSEKEEPING })
        assertTrue(items.none { it.name == SetupLabels.MINDFULNESS })
    }

    @Test
    fun `a zero duration answer is skipped rather than materialized`() {
        val items = setup().copy(morningDuration = Duration.ZERO).recurringItems(now)
        assertTrue(items.none { it.name == SetupLabels.MORNING })
    }

    @Test
    fun `mindfulness every day is a daily rule`() {
        val item = setup()
            .copy(mindfulness = MindfulnessPlan.EveryDay(DailySlot(LocalTime(21, 0), 1.hours)))
            .recurringItems(now)
            .first { it.name == SetupLabels.MINDFULNESS }

        assertEquals(RecurrenceRule.Daily, item.recurrenceRule)
        assertEquals(Category.ACHTSAM, item.category)
    }
}
