package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.domain.reward.isInflationDue
import com.example.erik_iteration_2.domain.reward.lastDueDate
import com.example.erik_iteration_2.domain.reward.weeklyInflationOn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InflationDueTest {

    /** 2026-09-13 is a Sunday; the 14th a Monday, the 16th a Wednesday. */
    private val sunday = LocalDate(2026, 9, 13)
    private val monday = LocalDate(2026, 9, 14)
    private val wednesday = LocalDate(2026, 9, 16)

    private val setupDay = LocalDate(2026, 9, 1)

    @Test
    fun `the due date is the weekday itself when today is it`() {
        assertEquals(sunday, lastDueDate(DayOfWeek.SUNDAY, sunday))
    }

    @Test
    fun `otherwise it is the most recent one behind us`() {
        assertEquals(sunday, lastDueDate(DayOfWeek.SUNDAY, wednesday))
        assertEquals(monday, lastDueDate(DayOfWeek.MONDAY, wednesday))
    }

    @Test
    fun `nothing on record means due once the first weekday has passed`() {
        assertTrue(isInflationDue(null, setupDay, DayOfWeek.SUNDAY, wednesday))
    }

    @Test
    fun `a fresh account is not charged before its first due day`() {
        // Set up on the 1st, and the first Sunday after that is the 6th.
        assertFalse(isInflationDue(null, LocalDate(2026, 9, 7), DayOfWeek.SUNDAY, LocalDate(2026, 9, 9)))
    }

    @Test
    fun `already applied on the due day is not due again`() {
        assertFalse(isInflationDue(sunday, setupDay, DayOfWeek.SUNDAY, wednesday))
    }

    @Test
    fun `applied last week is due again once the day comes round`() {
        val lastWeek = LocalDate(2026, 9, 6)
        assertFalse(isInflationDue(lastWeek, setupDay, DayOfWeek.SUNDAY, LocalDate(2026, 9, 12)))
        assertTrue(isInflationDue(lastWeek, setupDay, DayOfWeek.SUNDAY, sunday))
    }

    @Test
    fun `changing the weekday moves the next due date with it`() {
        // Applied on Sunday the 13th; with Wednesday chosen, the 16th is due.
        assertTrue(isInflationDue(sunday, setupDay, DayOfWeek.WEDNESDAY, wednesday))
    }

    @Test
    fun `savings shrink and debt does not`() {
        assertEquals(3.0, weeklyInflationOn(10.0), 0.0001)
        assertEquals(0.0, weeklyInflationOn(-10.0), 0.0001)
    }
}
