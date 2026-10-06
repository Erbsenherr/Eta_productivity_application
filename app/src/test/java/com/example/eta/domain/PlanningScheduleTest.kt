package com.example.eta.domain

import com.example.eta.domain.planning.DailyPhaseStatus
import com.example.eta.domain.planning.MAX_EMERGENCY_HOURS
import com.example.eta.domain.planning.PLANNING_SNOOZE
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.domain.planning.deferredFrom
import com.example.eta.domain.planning.nextPlanning
import com.example.eta.domain.planning.nextWake
import com.example.eta.domain.planning.shouldRing
import com.example.eta.domain.planning.snoozedFrom
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningScheduleTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")

    /** Daily planning at 20:00, weekly on Sunday at 18:00. */
    private fun setup() = UserSetup.draft(now).copy(
        dailyPlanningTime = LocalTime(20, 0),
        weeklyPlanningDay = DayOfWeek.SUNDAY,
        weeklyPlanningTime = LocalTime(18, 0),
    )

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun phase(owedFrom: LocalDateTime?) = DailyPhaseStatus(
        todaySettled = false,
        tomorrowConfirmed = false,
        tomorrowHasBlocks = false,
        dueAt = null,
        owedFrom = owedFrom,
    )

    @Test
    fun `the daily phase is owed only from its planning time on`() {
        val status = phase(owedFrom = at("2026-09-07T20:00"))
        // Closing early is allowed, but before the hour it is not yet owed.
        assertFalse(status.isOwed(at("2026-09-07T19:59")))
        assertTrue(status.isOwed(at("2026-09-07T20:00")))
        // Left open past midnight: still owed, not quietly reset.
        assertTrue(status.isOwed(at("2026-09-08T00:30")))
    }

    @Test
    fun `a phase with no planning time reads as owed`() {
        assertTrue(phase(owedFrom = null).isOwed(at("2026-09-07T08:00")))
    }

    @Test
    fun `the wake alarm is off until it is switched on`() {
        // The default has to be off: nobody agreed to be woken by answering a
        // questionnaire, and a null is what tells the scheduler to cancel.
        assertNull(nextWake(setup(), at("2026-09-07T09:00")))
    }

    @Test
    fun `the wake alarm is due tomorrow once the morning has passed`() {
        val waking = setup().copy(wakeAlarm = true, wakeTime = LocalTime(7, 0))
        assertEquals(
            at("2026-09-07T07:00"),
            nextWake(waking, at("2026-09-07T02:00")),
        )
        assertEquals(
            at("2026-09-08T07:00"),
            nextWake(waking, at("2026-09-07T09:00")),
        )
    }

    @Test
    fun `ringing at the appointed minute books tomorrow, not the same minute again`() {
        // Strictly after, or the alarm would reschedule itself onto the instant
        // it just fired at and loop.
        val waking = setup().copy(wakeAlarm = true, wakeTime = LocalTime(7, 0))
        assertEquals(
            at("2026-09-08T07:00"),
            nextWake(waking, at("2026-09-07T07:00")),
        )
    }

    @Test
    fun `the daily phase is due tonight when the evening is still ahead`() {
        assertEquals(
            at("2026-09-07T20:00"),
            nextPlanning(setup(), PlanningPhase.DAILY, at("2026-09-07T09:00")),
        )
    }

    @Test
    fun `once tonight is past, the daily phase moves to tomorrow`() {
        assertEquals(
            at("2026-09-08T20:00"),
            nextPlanning(setup(), PlanningPhase.DAILY, at("2026-09-07T21:30")),
        )
    }

    @Test
    fun `firing exactly on time schedules the next day, not the same moment`() {
        // Otherwise the alarm would reschedule itself onto the instant it just
        // fired and never move on.
        assertEquals(
            at("2026-09-08T20:00"),
            nextPlanning(setup(), PlanningPhase.DAILY, at("2026-09-07T20:00")),
        )
    }

    @Test
    fun `the weekly phase lands on the chosen weekday`() {
        // 2026-09-07 is a Monday; the next Sunday is the 13th.
        val next = nextPlanning(setup(), PlanningPhase.WEEKLY, at("2026-09-07T09:00"))
        assertEquals(at("2026-09-13T18:00"), next)
    }

    @Test
    fun `on the planning day itself the weekly phase is still today`() {
        assertEquals(
            at("2026-09-13T18:00"),
            nextPlanning(setup(), PlanningPhase.WEEKLY, at("2026-09-13T09:00")),
        )
    }

    @Test
    fun `after the weekly hour it moves a whole week on`() {
        assertEquals(
            at("2026-09-20T18:00"),
            nextPlanning(setup(), PlanningPhase.WEEKLY, at("2026-09-13T19:00")),
        )
    }

    @Test
    fun `the alarm stays quiet with the app open or the phase done`() {
        assertTrue(shouldRing(appInForeground = false, phaseCompleted = false))
        assertFalse(shouldRing(appInForeground = true, phaseCompleted = false))
        assertFalse(shouldRing(appInForeground = false, phaseCompleted = true))
    }

    @Test
    fun `a snooze buys five minutes`() {
        assertEquals(now + PLANNING_SNOOZE, snoozedFrom(now))
    }

    @Test
    fun `an emergency deferral is clamped so a phase cannot be skipped`() {
        assertEquals(now + 3.hours, deferredFrom(now, 3))
        assertEquals(now + MAX_EMERGENCY_HOURS.hours, deferredFrom(now, 48))
        assertEquals(now + 1.hours, deferredFrom(now, 0))
    }
}
