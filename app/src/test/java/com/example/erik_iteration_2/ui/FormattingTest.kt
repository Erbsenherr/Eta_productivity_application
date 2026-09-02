package com.example.erik_iteration_2.ui

import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatCountdown
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.format.formatShort
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    private val now = Instant.parse("2026-08-22T12:00:00Z")

    @Test
    fun `clock times are zero padded`() {
        assertEquals("18:00", LocalTime(18, 0).formatClock())
        assertEquals("07:05", LocalTime(7, 5).formatClock())
    }

    @Test
    fun `durations under an hour read in minutes`() {
        assertEquals("45 min", 45.minutes.formatShort())
    }

    @Test
    fun `durations of an hour or more read as hours and minutes`() {
        assertEquals("1:30 h", 90.minutes.formatShort())
        assertEquals("2:00 h", 2.hours.formatShort())
    }

    @Test
    fun `countdowns coarsen as the deadline recedes`() {
        assertEquals("2T 4h", formatCountdown(now + 52.hours, now))
        assertEquals("1h 30m", formatCountdown(now + 90.minutes, now))
        assertEquals("5m 30s", formatCountdown(now + 5.minutes + 30.seconds, now))
    }

    @Test
    fun `a passed deadline reads as overdue instead of negative`() {
        assertEquals("überfällig", formatCountdown(now - 1.hours, now))
    }

    @Test
    fun `points drop a pointless decimal but keep halves`() {
        assertEquals("3", formatPoints(3.0))
        assertEquals("2.5", formatPoints(2.5))
        assertEquals("-1.5", formatPoints(-1.5))
    }
}
