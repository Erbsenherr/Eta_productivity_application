package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.QuantitySetting
import com.example.eta.domain.growth.TimerSetting
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.SubtaskTimer
import com.example.eta.domain.planning.TaskEventKind
import com.example.eta.domain.planning.eventsAt
import com.example.eta.domain.planning.stepTimerEntries
import com.example.eta.domain.planning.timerRemaining
import com.example.eta.domain.subtask.StepExtras
import com.example.eta.domain.subtask.advanced
import com.example.eta.domain.subtask.asBlock
import com.example.eta.domain.subtask.familyIn
import com.example.eta.domain.subtask.isGrowingStep
import com.example.eta.domain.subtask.isTimerStep
import com.example.eta.domain.subtask.quantityLabel
import com.example.eta.domain.subtask.stepWorkedInFull
import com.example.eta.domain.subtask.timedLength
import com.example.eta.domain.subtask.withProgressOf
import com.example.eta.domain.subtask.withStepExtras
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 49: a routine's steps as Timer-Tasks, with a "Zielzeit", and with a count. */
class RoutineStepGoalsTest {

    private val now = Instant.parse("2026-10-20T05:00:00Z")
    private val date = LocalDate(2026, 10, 20)

    private val bare = Subtask(itemId = "routine", position = 0, name = "Dehnen", createdAt = now, updatedAt = now)

    private val growing = StepExtras(
        timer = TimerSetting(),
        duration = 5.minutes,
        growth = GrowthSetting(start = 5.minutes, target = 10.minutes, increment = 1.minutes, dynamic = false),
    )

    @Test
    fun `a step set up as a timer task starts at its start and carries no buffer in its time`() {
        val step = bare.withStepExtras(growing.copy(timer = TimerSetting(buffer = 2.minutes)))
        assertTrue(step.isTimerStep)
        assertTrue(step.isGrowingStep)
        assertEquals(5.minutes, step.duration)
        assertEquals(2.minutes, step.timerBuffer)
        assertEquals(StepExtras.of(step).growth?.target, 10.minutes)
    }

    @Test
    fun `without the timer there is no time and no Zielzeit, but a count may stay`() {
        val step = bare.withStepExtras(
            StepExtras(growth = growing.growth, quantity = QuantitySetting.Default, duration = 5.minutes),
        )
        assertFalse(step.isTimerStep)
        assertFalse(step.isGrowingStep)
        assertNull(step.duration)
        assertEquals("Anzahl 1", step.quantityLabel())
    }

    @Test
    fun `a step grows only for a sitting worked in full, and its count either way`() {
        val step = bare.withStepExtras(growing.copy(quantity = QuantitySetting.Default))

        val full = step.advanced(timeCounts = true)
        assertEquals(6.minutes, full.duration)
        assertEquals(2, full.quantity)

        val short = step.advanced(timeCounts = false)
        assertEquals(5.minutes, short.duration)
        assertEquals(2, short.quantity)

        val grown = (1..20).fold(step) { s, _ -> s.advanced(true) }
        assertEquals(10.minutes, grown.duration)
    }

    @Test
    fun `an edit that leaves the start alone keeps where growing has got to`() {
        val step = bare.withStepExtras(growing).advanced(true).advanced(true)
        val edited = step.withStepExtras(StepExtras.of(step))
        assertEquals(7.minutes, edited.duration)
    }

    @Test
    fun `worked in full is the whole goal counted, and a tick without a timer counts as full`() {
        val timer = SubtaskTimer("b", "s", goal = 5.minutes, worked = 4.minutes)
        assertFalse(timer.stepWorkedInFull())
        assertTrue(timer.copy(worked = 5.minutes).stepWorkedInFull())
        assertTrue((null as SubtaskTimer?).stepWorkedInFull())
    }

    @Test
    fun `the weekday rows of one routine are one step, and share what moved`() {
        val parent = routine()
        val monday = bare.withStepExtras(growing)
        val tuesday = monday.copy(id = "other", itemId = "routine-tue")
        assertEquals(monday.familyIn(parent), tuesday.familyIn(parent.copy(id = "routine-tue")))
        assertEquals(6.minutes, tuesday.withProgressOf(monday.advanced(true)).duration)
    }

    @Test
    fun `the timed steps are added up, buffers included, now and grown`() {
        val extras = listOf(
            growing.copy(timer = TimerSetting(buffer = 1.minutes)),
            StepExtras(timer = TimerSetting(), duration = 3.minutes),
            StepExtras(quantity = QuantitySetting.Default),
        )
        assertEquals(9.minutes, extras.timedLength(grown = false))
        assertEquals(14.minutes, extras.timedLength(grown = true))
    }

    private fun routine() = Item.newRecurring(
        id = "routine",
        name = "Morgenroutine",
        category = Category.ACHTSAM,
        recurrenceRule = RecurrenceRule.Daily,
        startTime = LocalTime(7, 0),
        estimatedDuration = 45.minutes,
        now = now,
    ).copy(routineMode = true)

    private fun block() = PlannedBlock(
        id = "block",
        itemId = "routine",
        date = date,
        start = LocalTime(7, 0),
        plannedDuration = 45.minutes,
        origin = BlockOrigin.RECURRING,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a running step timer counts down and rings when its time is up`() {
        val step = bare.withStepExtras(growing).copy(id = "step")
        val started = SubtaskTimer("block", "step", goal = 5.minutes, runningSince = now)
        val entries = stepTimerEntries(
            blocks = listOf(BlockWithItem(block(), routine())),
            timers = listOf(started),
            steps = mapOf("routine" to listOf(step)),
        )
        val entry = entries.single()
        assertEquals("Dehnen", entry.item.name)
        assertEquals(3.minutes, entry.block.timerRemaining(now + 2.minutes))

        val events = eventsAt(emptyList(), now + 5.minutes, TimeZone.UTC, stepTimers = entries)
        assertEquals(TaskEventKind.TIMER_DONE, events.single().kind)

        // Ticked, it rings no more.
        assertTrue(
            stepTimerEntries(
                listOf(BlockWithItem(block(), routine())),
                listOf(started),
                mapOf("routine" to listOf(step)),
                checked = setOf("block" to "step"),
            ).isEmpty(),
        )
    }

    @Test
    fun `an idle step timer is a block whose timer was never used`() {
        val step = bare.withStepExtras(growing)
        val proxy = (null as SubtaskTimer?).asBlock(block(), step)
        assertNull(proxy.timerGoal)
        assertEquals(block().start, proxy.start)
    }
}
