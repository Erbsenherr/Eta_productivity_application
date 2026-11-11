package com.example.eta.domain

import com.example.eta.domain.model.StopwatchTime
import com.example.eta.domain.stats.elapsed
import com.example.eta.domain.stats.ended
import com.example.eta.domain.stats.isRunning
import com.example.eta.domain.stats.paused
import com.example.eta.domain.stats.resumed
import com.example.eta.domain.stats.stopwatchSummaries
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 56: the stopwatch, and its times in the Statistiken tab. */
class Step56Test {

    private val t0 = Instant.parse("2026-11-11T08:00:00Z")
    private val date = LocalDate(2026, 11, 11)

    private fun running(from: Instant = t0) = StopwatchTime(
        itemId = "i",
        blockId = "b",
        name = "Kochen",
        date = date,
        runningSince = from,
        createdAt = t0,
        updatedAt = t0,
    )

    private fun ended(name: String, minutes: Int, day: Int = 11, id: String = name + minutes) = StopwatchTime(
        id = id,
        itemId = "i",
        blockId = "b",
        name = name,
        date = LocalDate(2026, 11, day),
        worked = minutes.minutes,
        endedAt = t0,
        createdAt = t0,
        updatedAt = t0,
    )

    @Test
    fun `a running stopwatch counts from when it was set going`() {
        assertEquals(10.minutes, running().elapsed(t0 + 10.minutes))
    }

    @Test
    fun `a pause is left out of the time`() {
        val paused = running().paused(t0 + 10.minutes)
        assertFalse(paused.isRunning)
        // Twenty minutes later it still says ten.
        assertEquals(10.minutes, paused.elapsed(t0 + 30.minutes))
        val resumed = paused.resumed(t0 + 30.minutes)
        assertEquals(15.minutes, resumed.elapsed(t0 + 35.minutes))
    }

    @Test
    fun `ending keeps what was counted, to the second`() {
        val done = running().ended(t0 + 12.minutes + 30.seconds + 400.milliseconds())
        assertNotNull(done.endedAt)
        assertEquals(12.minutes + 30.seconds, done.worked)
        assertFalse(done.isRunning)
    }

    @Test
    fun `the summaries are alphabetical, by name, finished times only`() {
        val summaries = stopwatchSummaries(
            listOf(
                ended("Zähneputzen", 3),
                ended("kochen", 40, day = 10),
                ended("Kochen", 50, day = 11),
                ended("Abwasch", 20),
                running(),
            ),
        )
        assertEquals(listOf("Abwasch", "Kochen", "Zähneputzen"), summaries.map { it.name })

        val kochen = summaries[1]
        assertEquals(2, kochen.count)
        assertEquals(45.minutes, kochen.average)
        assertEquals(40.minutes, kochen.shortest)
        assertEquals(50.minutes, kochen.longest)
        // Newest first.
        assertTrue(kochen.times.first().date > kochen.times.last().date)
    }

    private fun Int.milliseconds() = kotlin.time.Duration.parse("${this}ms")
}
