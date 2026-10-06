package com.example.eta.domain

import com.example.eta.domain.model.Reminder
import com.example.eta.domain.reminder.defaultReminderDate
import com.example.eta.domain.reminder.nextReminderAlarm
import com.example.eta.domain.reminder.reminderMoment
import com.example.eta.ui.format.formatWeekdays
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderScheduleTest {

    private val evening = LocalDateTime(2026, 9, 15, 22, 0)

    private fun reminder(at: String, fired: Boolean = false) = Reminder(
        text = "x",
        at = Instant.parse(at),
        firedAt = if (fired) Instant.parse(at) else null,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    @Test
    fun `without a date, a time still ahead is today and a time gone by is tomorrow`() {
        assertEquals(LocalDate(2026, 9, 15), defaultReminderDate(LocalTime(23, 30), evening))
        assertEquals(LocalDate(2026, 9, 16), defaultReminderDate(LocalTime(7, 0), evening))
        // The current minute has gone by as far as a reminder is concerned.
        assertEquals(LocalDate(2026, 9, 16), defaultReminderDate(LocalTime(22, 0), evening))
    }

    @Test
    fun `a moment is taken on the whole minute`() {
        assertEquals(
            Instant.parse("2026-09-16T07:00:00Z"),
            reminderMoment(LocalDate(2026, 9, 16), LocalTime(7, 0, 42), TimeZone.UTC),
        )
    }

    @Test
    fun `the alarm aims at the soonest reminder still to ring`() {
        val now = Instant.parse("2026-09-15T20:00:00Z")
        val pending = listOf(
            reminder("2026-09-16T08:00:00Z"),
            reminder("2026-09-15T21:00:00Z"),
            reminder("2026-09-15T20:30:00Z", fired = true),
        )
        assertEquals(Instant.parse("2026-09-15T21:00:00Z"), nextReminderAlarm(pending, now))
        assertNull(nextReminderAlarm(emptyList(), now))
    }

    @Test
    fun `an overdue reminder still rings, a moment from now`() {
        val now = Instant.parse("2026-09-15T20:00:00Z")
        val missed = listOf(reminder("2026-09-15T08:00:00Z"))
        assertEquals(now + 2.seconds, nextReminderAlarm(missed, now))
    }

    @Test
    fun `weekdays read the way a timetable writes them`() {
        assertEquals("täglich", formatWeekdays(DayOfWeek.entries.toSet()))
        assertEquals(
            "Mo–Fr",
            formatWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
        )
        assertEquals("Mo, Mi, Fr", formatWeekdays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        assertEquals("Di, Mi", formatWeekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)))
    }
}
