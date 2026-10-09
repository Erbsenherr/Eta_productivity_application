package com.example.eta.ui

import com.example.eta.ui.components.monthGrid
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The calendar's one piece of arithmetic: which day sits in which cell. */
class MonthGridTest {

    @Test
    fun `every week has seven cells and every day of the month appears once`() {
        val weeks = monthGrid(2026, 10)

        assertTrue(weeks.all { it.size == 7 })
        assertEquals((1..31).toList(), weeks.flatten().filterNotNull().map { it.day })
    }

    @Test
    fun `the first of the month sits under its weekday`() {
        // The first of October 2026 is a Thursday: three empty cells before it.
        val first = monthGrid(2026, 10).first()

        assertEquals(listOf(null, null, null), first.take(3))
        assertEquals(LocalDate(2026, 10, 1), first[3])
    }

    @Test
    fun `a month starting on a Sunday puts one day in its first week`() {
        // The first of November 2026 is a Sunday.
        val weeks = monthGrid(2026, 11)

        assertEquals(1, weeks.first().count { it != null })
        assertEquals(LocalDate(2026, 11, 1), weeks.first().last())
        assertEquals(6, weeks.size)
    }

    @Test
    fun `a month starting on a Monday has no empty cell in front`() {
        // The first of June 2026 is a Monday.
        assertEquals(LocalDate(2026, 6, 1), monthGrid(2026, 6).first().first())
    }

    @Test
    fun `February follows the leap year`() {
        assertEquals(29, monthGrid(2028, 2).flatten().count { it != null })
        assertEquals(28, monthGrid(2027, 2).flatten().count { it != null })
    }

    @Test
    fun `the last week is padded out to seven`() {
        val last = monthGrid(2026, 10).last()

        // The thirty-first is a Saturday; Sunday's cell belongs to November.
        assertEquals(LocalDate(2026, 10, 31), last[5])
        assertNull(last[6])
    }
}
