package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.domain.model.RecurrenceRule
import com.example.erik_iteration_2.domain.model.WeekParity
import com.example.erik_iteration_2.domain.setup.DailySlot
import com.example.erik_iteration_2.domain.setup.HousekeepingPlan
import com.example.erik_iteration_2.domain.setup.MealPlan
import com.example.erik_iteration_2.domain.setup.MindfulnessPlan
import com.example.erik_iteration_2.domain.setup.TimeSpan
import com.example.erik_iteration_2.domain.setup.WeeklySlot
import com.example.erik_iteration_2.domain.setup.WorkBlock
import com.example.erik_iteration_2.domain.setup.WorkSchedule
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The setup answers live in single string columns, so a broken round trip would
 * silently corrupt the user's whole configuration on the next app start.
 */
class SetupEncodingTest {

    private val slot = WeeklySlot(DayOfWeek.THURSDAY, LocalTime(18, 30), 90.minutes)

    @Test
    fun `weekly slot survives the round trip`() {
        assertEquals(slot, WeeklySlot.decode(slot.encode()))
    }

    @Test
    fun `a slot on several weekdays survives the round trip`() {
        val many = WeeklySlot(
            setOf(DayOfWeek.FRIDAY, DayOfWeek.TUESDAY),
            LocalTime(7, 0),
            45.minutes,
        )
        assertEquals(many, WeeklySlot.decode(many.encode()))
    }

    @Test
    fun `daily slot survives the round trip`() {
        val daily = DailySlot(LocalTime(6, 45), 20.minutes)
        assertEquals(daily, DailySlot.decode(daily.encode()))
    }

    @Test
    fun `meal plans survive the round trip`() {
        val prep: MealPlan = MealPlan.MealPrep(slot)
        assertEquals(prep, MealPlan.decode(prep.encode()))

        val daily: MealPlan = MealPlan.DailyCooking(
            listOf(DailySlot(LocalTime(12, 0), 45.minutes), DailySlot(LocalTime(18, 0), 1.hours)),
        )
        assertEquals(daily, MealPlan.decode(daily.encode()))
    }

    @Test
    fun `housekeeping keeps its parity`() {
        val biweekly: HousekeepingPlan = HousekeepingPlan.Biweekly(slot, WeekParity.ODD)
        assertEquals(biweekly, HousekeepingPlan.decode(biweekly.encode()))

        val weekly: HousekeepingPlan = HousekeepingPlan.Weekly(slot)
        assertEquals(weekly, HousekeepingPlan.decode(weekly.encode()))
    }

    @Test
    fun `mindfulness plans survive the round trip`() {
        val daily: MindfulnessPlan = MindfulnessPlan.EveryDay(DailySlot(LocalTime(21, 0), 15.minutes))
        assertEquals(daily, MindfulnessPlan.decode(daily.encode()))

        val weekly: MindfulnessPlan = MindfulnessPlan.Weekly(slot)
        assertEquals(weekly, MindfulnessPlan.decode(weekly.encode()))
    }

    @Test
    fun `work schedules survive the round trip, with and without a break`() {
        assertEquals(WorkSchedule.None, WorkSchedule.decode(WorkSchedule.None.encode()))

        val uniform: WorkSchedule = WorkSchedule.EveryWorkday(
            WorkBlock(
                span = TimeSpan(LocalTime(8, 15), LocalTime(16, 45)),
                pause = TimeSpan(LocalTime(12, 0), LocalTime(12, 30)),
            ),
        )
        assertEquals(uniform, WorkSchedule.decode(uniform.encode()))

        val withoutPause: WorkSchedule = WorkSchedule.EveryWorkday(
            WorkBlock(TimeSpan(LocalTime(8, 15), LocalTime(16, 45))),
        )
        assertEquals(withoutPause, WorkSchedule.decode(withoutPause.encode()))

        val perDay: WorkSchedule = WorkSchedule.PerWeekday(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    WorkBlock(TimeSpan(LocalTime(8, 0), LocalTime(12, 0))),
                    WorkBlock(
                        span = TimeSpan(LocalTime(14, 0), LocalTime(18, 0)),
                        pause = TimeSpan(LocalTime(16, 0), LocalTime(16, 15)),
                    ),
                ),
                DayOfWeek.WEDNESDAY to listOf(
                    WorkBlock(TimeSpan(LocalTime(10, 0), LocalTime(15, 0))),
                ),
            ),
        )
        assertEquals(perDay, WorkSchedule.decode(perDay.encode()))
    }

    @Test
    fun `empty weekdays are dropped rather than encoded`() {
        val perDay = WorkSchedule.PerWeekday(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    WorkBlock(TimeSpan(LocalTime(9, 0), LocalTime(17, 0))),
                ),
                DayOfWeek.FRIDAY to emptyList(),
            ),
        )
        val decoded = WorkSchedule.decode(perDay.encode()) as WorkSchedule.PerWeekday
        assertEquals(setOf(DayOfWeek.MONDAY), decoded.blocks.keys)
    }

    @Test
    fun `the daily recurrence rule survives the round trip`() {
        val rule: RecurrenceRule = RecurrenceRule.Daily
        assertEquals(rule, RecurrenceRule.decode(rule.encode()))
    }
}
