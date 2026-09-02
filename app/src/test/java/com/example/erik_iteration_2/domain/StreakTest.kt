package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.domain.streak.streakOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTest {

    private val today = LocalDate(2026, 9, 10)

    private fun days(vararg day: Int): Set<LocalDate> =
        day.map { LocalDate(2026, 9, it) }.toSet()

    @Test
    fun `nothing settled is a broken streak`() {
        val streak = streakOf(emptySet(), today)
        assertEquals(0, streak.length)
        assertTrue(streak.isBroken)
        assertTrue(streak.todayOpen)
    }

    @Test
    fun `a run of settled days counts them all`() {
        assertEquals(4, streakOf(days(7, 8, 9, 10), today).length)
    }

    @Test
    fun `today still open does not break a run that reaches yesterday`() {
        // The day is still being lived; a streak that read zero every morning
        // would say nothing at all.
        val streak = streakOf(days(7, 8, 9), today)
        assertEquals(3, streak.length)
        assertTrue(streak.todayOpen)
        assertFalse(streak.isBroken)
    }

    @Test
    fun `settling today extends the run and closes it`() {
        val streak = streakOf(days(7, 8, 9, 10), today)
        assertEquals(4, streak.length)
        assertFalse(streak.todayOpen)
    }

    @Test
    fun `a missed yesterday ends the run whatever came before`() {
        assertEquals(0, streakOf(days(1, 2, 3, 4, 5, 6, 7, 8), today).length)
    }

    @Test
    fun `a gap is not bridged`() {
        // The 8th is missing, so only the 9th counts.
        assertEquals(1, streakOf(days(5, 6, 7, 9), today).length)
    }

    @Test
    fun `settling today after a broken run starts a new one at one`() {
        assertEquals(1, streakOf(days(1, 2, 10), today).length)
    }

    @Test
    fun `future days are irrelevant`() {
        assertEquals(1, streakOf(days(9, 11, 12), today).length)
    }
}
