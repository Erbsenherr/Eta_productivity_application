package com.example.eta.domain

import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.overlaps
import com.example.eta.domain.planning.shortenedBefore
import com.example.eta.domain.planning.startMinute
import com.example.eta.domain.planning.startedAfter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Making room for a fixed appointment without giving the activity up.
 *
 * The example in the user's own words: A runs 10:00–13:00, the calendar puts B at
 * 12:00–14:00, and shortening A leaves it running 10:00–12:00.
 */
class ConflictReliefTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun block(
        id: String,
        from: LocalTime,
        duration: Duration,
        travelBefore: Duration? = null,
        returnAfter: Duration? = null,
        breakAfter: Duration? = null,
    ) = PlannedBlock(
        id = id,
        itemId = "item-$id",
        date = date,
        start = from,
        plannedDuration = duration,
        origin = BlockOrigin.RECURRING,
        travelBefore = travelBefore,
        returnAfter = returnAfter,
        breakAfter = breakAfter,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `the example - an activity is cut back to where the appointment begins`() {
        val a = block("a", LocalTime(10, 0), 3.hours)
        val b = block("b", LocalTime(12, 0), 2.hours)

        val shortened = a.shortenedBefore(b)
        assertNotNull(shortened)
        assertEquals(LocalTime(10, 0), shortened!!.start)
        assertEquals(2.hours, shortened.plannedDuration)
        assertEquals(12 * 60, shortened.endMinute())
        // And it really is out of the way afterwards.
        assertFalse(shortened.overlaps(b))
    }

    @Test
    fun `an activity the appointment only clips at the front waits instead`() {
        // The other direction the user named: the conflict bites at the start, so
        // the whole activity moves behind the appointment rather than shrinking.
        val c = block("c", LocalTime(13, 0), 2.hours)
        val b = block("b", LocalTime(12, 0), 2.hours)

        assertNull(c.shortenedBefore(b))

        val pushed = c.startedAfter(b)
        assertNotNull(pushed)
        assertEquals(LocalTime(14, 0), pushed!!.start)
        // It kept its length: nothing about it got smaller, it happens later.
        assertEquals(2.hours, pushed.plannedDuration)
        assertFalse(pushed.overlaps(b))
    }

    @Test
    fun `an activity swallowed whole offers neither`() {
        // The case the user carved out: the appointment covers it GÄNZLICH, so
        // there is nothing to keep in front and nothing left behind.
        val a = block("a", LocalTime(12, 30), 1.hours)
        val b = block("b", LocalTime(12, 0), 2.hours)

        assertNull(a.shortenedBefore(b))
        assertNull(a.startedAfter(b))
    }

    @Test
    fun `an activity spanning the appointment can go either way`() {
        val a = block("a", LocalTime(10, 0), 6.hours)
        val b = block("b", LocalTime(12, 0), 2.hours)

        assertEquals(2.hours, a.shortenedBefore(b)?.plannedDuration)
        assertEquals(LocalTime(14, 0), a.startedAfter(b)?.start)
    }

    @Test
    fun `the margins keep their lengths and the container still clears`() {
        // Only the activity itself changes. A earns a 25-minute break, B brings a
        // quarter of an hour of travel — so what has to clear is container against
        // container, and A ends 25 minutes before B's journey starts.
        val a = block("a", LocalTime(10, 0), 3.hours, breakAfter = 25.minutes)
        val b = block("b", LocalTime(12, 0), 2.hours, travelBefore = 15.minutes)

        val shortened = a.shortenedBefore(b)
        assertNotNull(shortened)
        assertEquals(25.minutes, shortened!!.breakAfter)
        assertEquals(11 * 60 + 20, shortened.endMinute())
        assertFalse(shortened.overlaps(b))
    }

    @Test
    fun `a pushed activity clears the appointment's own tail, and keeps its journey`() {
        val b = block("b", LocalTime(12, 0), 2.hours, breakAfter = 30.minutes)
        val c = block("c", LocalTime(13, 0), 3.hours, travelBefore = 15.minutes)

        val pushed = c.startedAfter(b)
        assertNotNull(pushed)
        // B is over at 14:00 and rests until 14:30; C's journey starts then, so C
        // itself starts a quarter of an hour later.
        assertEquals(LocalTime(14, 45), pushed!!.start)
        assertEquals(15.minutes, pushed.travelBefore)
        assertFalse(pushed.overlaps(b))
    }

    @Test
    fun `a remainder too small to be a block is not offered`() {
        // Ten minutes is not a shortened activity, it is a destroyed one — and
        // destroying it is what the two other answers are for.
        val a = block("a", LocalTime(11, 50), 70.minutes)
        val b = block("b", LocalTime(12, 0), 2.hours)

        assertNull(a.shortenedBefore(b))
    }

    @Test
    fun `a quarter of an hour is still a block`() {
        val a = block("a", LocalTime(11, 45), 75.minutes)
        val b = block("b", LocalTime(12, 0), 2.hours)

        assertEquals(15.minutes, a.shortenedBefore(b)?.plannedDuration)
    }

    @Test
    fun `nothing is pushed past midnight`() {
        val b = block("b", LocalTime(22, 0), 105.minutes)
        val c = block("c", LocalTime(23, 0), 60.minutes)

        assertNull(c.startedAfter(b))
    }

    @Test
    fun `shortening drops a recorded correction`() {
        // The plan changed, so a duration recorded against the old one no longer
        // describes anything.
        val a = block("a", LocalTime(10, 0), 3.hours).copy(actualDuration = 150.minutes)
        val b = block("b", LocalTime(12, 0), 2.hours)

        val shortened = a.shortenedBefore(b)
        assertNull(shortened?.actualDuration)
        assertEquals(10 * 60, shortened?.startMinute())
    }
}
