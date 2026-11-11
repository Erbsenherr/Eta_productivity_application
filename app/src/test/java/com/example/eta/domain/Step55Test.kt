package com.example.eta.domain

import com.example.eta.domain.growth.advancedBySitting
import com.example.eta.domain.growth.hasTimerMalus
import com.example.eta.domain.growth.recoveredFromMalus
import com.example.eta.domain.growth.timerDuration
import com.example.eta.domain.growth.withEarlyEndMalus
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.RecurrenceRule
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 55: "Vorzeitig beenden" — 10 % off, 2 % of the old time back per full sitting. */
class Step55Test {

    private val now = Instant.parse("2026-11-11T08:00:00Z")

    private fun timerTask(minutes: Int = 60, buffer: Int? = null) =
        Item.newQuickTodo("Lernen", now).copy(
            recurrenceRule = RecurrenceRule.Daily,
            timerTask = true,
            timerBuffer = buffer?.minutes,
            estimatedDuration = (minutes + (buffer ?: 0)).minutes,
        )

    @Test
    fun `an early end takes a tenth of the time as it stands`() {
        val once = timerTask().withEarlyEndMalus()
        assertEquals(54.minutes, once.timerDuration)
        assertEquals(60.minutes, once.timerMalusBase)

        val twice = once.withEarlyEndMalus()
        assertEquals(48.minutes + 36.seconds, twice.timerDuration)
        // Still the time before the first of them.
        assertEquals(60.minutes, twice.timerMalusBase)
    }

    @Test
    fun `the buffer stays behind the shortened time`() {
        val item = timerTask(minutes = 60, buffer = 10).withEarlyEndMalus()
        assertEquals(54.minutes, item.timerDuration)
        assertEquals(64.minutes, item.estimatedDuration)
    }

    @Test
    fun `full sittings bring back two percent of the old time, up to it`() {
        var item = timerTask().withEarlyEndMalus()
        item = item.recoveredFromMalus()
        assertEquals(55.minutes + 12.seconds, item.timerDuration)
        assertTrue(item.hasTimerMalus)

        repeat(4) { item = item.recoveredFromMalus() }
        assertEquals(60.minutes, item.timerDuration)
        assertFalse(item.hasTimerMalus)
        assertNull(item.timerMalusBase)
    }

    @Test
    fun `a time already back at its old length ends the malus without moving`() {
        val item = timerTask(minutes = 70).copy(timerMalusBase = 60.minutes)
        val recovered = item.recoveredFromMalus()
        assertEquals(70.minutes, recovered.timerDuration)
        assertNull(recovered.timerMalusBase)
    }

    @Test
    fun `growing waits while a malus stands`() {
        val growing = timerTask().copy(
            growthStart = 30.minutes,
            growthTarget = 90.minutes,
            growthIncrement = 5.minutes,
        )
        val shortened = growing.advancedBySitting(endedEarly = true, workedInFull = false, lenient = false)
        assertEquals(54.minutes, shortened.timerDuration)
        assertEquals(0, shortened.growthProgress)

        // A full sitting under the malus recovers, and does not grow.
        val next = shortened.advancedBySitting(endedEarly = false, workedInFull = true, lenient = false)
        assertEquals(55.minutes + 12.seconds, next.timerDuration)
        assertEquals(0, next.growthProgress)

        // Without a malus, a full sitting grows as ever.
        val plain = growing.advancedBySitting(endedEarly = false, workedInFull = true, lenient = false)
        assertEquals(65.minutes, plain.timerDuration)
    }

    @Test
    fun `a count still goes up on a sitting ended early`() {
        val counted = timerTask().copy(quantity = 10, quantityStart = 10, quantityIncrement = 2)
        val next = counted.advancedBySitting(endedEarly = true, workedInFull = false, lenient = false)
        assertEquals(12, next.quantity)
        assertEquals(54.minutes, next.timerDuration)
    }

    @Test
    fun `a short sitting without the button costs nothing`() {
        val item = timerTask().advancedBySitting(endedEarly = false, workedInFull = false, lenient = false)
        assertEquals(60.minutes, item.timerDuration)
        assertFalse(item.hasTimerMalus)
    }
}
