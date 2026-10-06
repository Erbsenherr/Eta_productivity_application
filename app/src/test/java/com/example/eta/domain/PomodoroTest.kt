package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.PomodoroBoundary
import com.example.eta.domain.planning.PomodoroPhase
import com.example.eta.domain.planning.PomodoroPhaseKind.PAUSE
import com.example.eta.domain.planning.PomodoroPhaseKind.WORK
import com.example.eta.domain.planning.TaskEventKind
import com.example.eta.domain.planning.eventsAt
import com.example.eta.domain.planning.nextTaskEvent
import com.example.eta.domain.planning.pomodoroBoundaries
import com.example.eta.domain.planning.pomodoroPhaseAt
import com.example.eta.domain.planning.withPomodoro
import com.example.eta.domain.planning.withoutPomodoro
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)
    private val zone = TimeZone.UTC

    /** A 2-hour task from 10:00 to 12:00. */
    private val block = PlannedBlock(
        id = "b",
        itemId = "i",
        date = date,
        start = LocalTime(10, 0),
        plannedDuration = 2.hours,
        origin = BlockOrigin.DRAGGED,
        createdAt = now,
        updatedAt = now,
    )

    private fun minute(h: Int, m: Int) = h * 60 + m

    @Test
    fun `set up ahead, the rhythm counts from the task's start and stores no anchor`() {
        val planned = block.withPomodoro(30.minutes, 5.minutes, now = LocalTime(8, 0))
        assertNull(planned.pomodoroAnchor)
        assertEquals(
            listOf(
                PomodoroBoundary(PAUSE, minute(10, 30)),
                PomodoroBoundary(WORK, minute(10, 35)),
                PomodoroBoundary(PAUSE, minute(11, 5)),
                PomodoroBoundary(WORK, minute(11, 10)),
                PomodoroBoundary(PAUSE, minute(11, 40)),
                PomodoroBoundary(WORK, minute(11, 45)),
            ),
            planned.pomodoroBoundaries(),
        )
    }

    @Test
    fun `the first work phase is never announced - the start sound already is`() {
        val planned = block.withPomodoro(30.minutes, 5.minutes, now = null)
        assertEquals(PAUSE, planned.pomodoroBoundaries().first().kind)
    }

    @Test
    fun `set up mid-task, it counts from that minute, seconds dropped`() {
        val running = block.withPomodoro(25.minutes, 5.minutes, now = LocalTime(10, 47, 33))
        assertEquals(LocalTime(10, 47), running.pomodoroAnchor)
        assertEquals(PomodoroBoundary(PAUSE, minute(11, 12)), running.pomodoroBoundaries().first())
    }

    @Test
    fun `nothing is announced on or after the task's end`() {
        // Pause 10:55, work 11:00, pause 11:55 — and work at 12:00 would be the end itself.
        val planned = block.withPomodoro(55.minutes, 5.minutes, now = null)
        assertEquals(
            listOf(PomodoroBoundary(PAUSE, minute(10, 55)), PomodoroBoundary(WORK, minute(11, 0))),
            planned.pomodoroBoundaries().take(2),
        )
        assertTrue(planned.pomodoroBoundaries().all { it.minuteOfDay < minute(12, 0) })
        assertTrue(planned.pomodoroBoundaries().none { it.minuteOfDay == minute(12, 0) })
    }

    @Test
    fun `the phase at a minute says which one and until when`() {
        val planned = block.withPomodoro(30.minutes, 5.minutes, now = null)
        assertEquals(PomodoroPhase(WORK, minute(10, 30)), planned.pomodoroPhaseAt(minute(10, 10)))
        assertEquals(PomodoroPhase(PAUSE, minute(10, 35)), planned.pomodoroPhaseAt(minute(10, 31)))
        assertEquals(PomodoroPhase(WORK, minute(12, 0)), planned.pomodoroPhaseAt(minute(11, 50)))
        assertNull(planned.pomodoroPhaseAt(minute(12, 0)))
        assertNull(block.pomodoroPhaseAt(minute(10, 10)))
    }

    @Test
    fun `switching it off leaves no trace`() {
        val off = block.withPomodoro(30.minutes, 5.minutes, now = LocalTime(10, 20)).withoutPomodoro()
        assertEquals(block, off)
    }

    @Test
    fun `the rhythm rides the task alarm alongside the start`() {
        val item = Item.newRecurring(
            name = "Lernen",
            category = Category.FOKUS,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = LocalTime(10, 0),
            estimatedDuration = 2.hours,
            now = now,
        )
        val entry = BlockWithItem(block.withPomodoro(30.minutes, 5.minutes, now = null), item)

        val start = Instant.parse("2026-09-01T10:00:00Z")
        assertEquals(listOf(TaskEventKind.START), eventsAt(listOf(entry), start, zone).map { it.kind })

        val firstPause = Instant.parse("2026-09-01T10:30:00Z")
        assertEquals(firstPause, nextTaskEvent(listOf(entry), start, zone))
        assertEquals(
            listOf(TaskEventKind.POMODORO_PAUSE),
            eventsAt(listOf(entry), firstPause, zone).map { it.kind },
        )
        assertEquals(
            listOf(TaskEventKind.POMODORO_WORK),
            eventsAt(listOf(entry), Instant.parse("2026-09-01T10:35:00Z"), zone).map { it.kind },
        )
    }
}
