package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.QuantitySetting
import com.example.eta.domain.growth.TimerSetting
import com.example.eta.domain.growth.advancedByCompletion
import com.example.eta.domain.growth.growthCurrent
import com.example.eta.domain.growth.isFullyGrown
import com.example.eta.domain.growth.timerDuration
import com.example.eta.domain.growth.withBufferAdded
import com.example.eta.domain.growth.withGrowth
import com.example.eta.domain.growth.withQuantity
import com.example.eta.domain.growth.withTimer
import com.example.eta.domain.growth.workedInFull
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.PomodoroPhaseKind
import com.example.eta.domain.planning.TimerSignalKind
import com.example.eta.domain.planning.TimerState
import com.example.eta.domain.planning.formatCountdown
import com.example.eta.domain.planning.fullTimerGoal
import com.example.eta.domain.planning.timerBilled
import com.example.eta.domain.planning.timerDone
import com.example.eta.domain.planning.timerElapsed
import com.example.eta.domain.planning.timerOverflow
import com.example.eta.domain.planning.timerPaused
import com.example.eta.domain.planning.timerPhase
import com.example.eta.domain.planning.timerRemaining
import com.example.eta.domain.planning.timerResumed
import com.example.eta.domain.planning.timerSignals
import com.example.eta.domain.planning.timerStarted
import com.example.eta.domain.planning.timerState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timer of a Timer-Task: what it counts, what it bills, when it rings, and
 * what a late start runs into.
 */
class TaskTimerTest {

    private val t0 = Instant.parse("2026-10-13T08:00:00Z")
    private val date = LocalDate(2026, 10, 13)

    private fun item(id: String = "lernen", buffer: Duration? = null) = Item(
        id = id,
        type = ItemType.RECURRING,
        name = id,
        stage = Stage.DAY,
        category = Category.FOKUS,
        createdAt = t0,
        updatedAt = t0,
    ).withTimer(TimerSetting(buffer))

    private fun block(
        id: String = "b",
        from: LocalTime = LocalTime(10, 0),
        duration: Duration = 130.minutes,
        origin: BlockOrigin = BlockOrigin.RECURRING,
    ) = PlannedBlock(
        id = id,
        itemId = "item-$id",
        date = date,
        start = from,
        plannedDuration = duration,
        origin = origin,
        createdAt = t0,
        updatedAt = t0,
    )

    // --- counting --------------------------------------------------------------

    @Test
    fun `a timer counts from its start, and not while it is paused`() {
        val idle = block()
        assertEquals(TimerState.IDLE, idle.timerState)
        assertEquals(Duration.ZERO, idle.timerElapsed(t0))

        val running = idle.timerStarted(2.hours, t0)
        assertEquals(TimerState.RUNNING, running.timerState)
        assertEquals(20.minutes, running.timerElapsed(t0 + 20.minutes))

        // Twenty minutes worked, a quarter of an hour of break by hand, ten more.
        val paused = running.timerPaused(t0 + 20.minutes)
        assertEquals(TimerState.PAUSED, paused.timerState)
        assertEquals(20.minutes, paused.timerElapsed(t0 + 35.minutes))

        val again = paused.timerResumed(t0 + 35.minutes)
        assertEquals(30.minutes, again.timerElapsed(t0 + 45.minutes))
        assertEquals(90.minutes, again.timerRemaining(t0 + 45.minutes))
    }

    @Test
    fun `a timer left running stops counting at its goal`() {
        val running = block().timerStarted(2.hours, t0)

        assertFalse(running.timerDone(t0 + 119.minutes))
        assertTrue(running.timerDone(t0 + 2.hours))
        assertEquals(2.hours, running.timerElapsed(t0 + 9.hours))
    }

    // --- the rhythm follows the timer ---------------------------------------------

    @Test
    fun `the pomodoro phase is counted on the timer, pauses of the rhythm included`() {
        val running = block()
            .copy(pomodoroWork = 25.minutes, pomodoroPause = 5.minutes)
            .timerStarted(1.hours, t0)

        val work = running.timerPhase(t0 + 10.minutes)
        assertEquals(PomodoroPhaseKind.WORK, work?.kind)
        assertEquals(15.minutes, work?.remaining)

        val pause = running.timerPhase(t0 + 27.minutes)
        assertEquals(PomodoroPhaseKind.PAUSE, pause?.kind)
        assertEquals(3.minutes, pause?.remaining)

        // The last phase would be a pause of five minutes; the goal cuts it off.
        assertEquals(2.minutes, running.timerPhase(t0 + 58.minutes)?.remaining)
        assertNull(running.timerPhase(t0 + 1.hours))
    }

    @Test
    fun `a break by hand stops the rhythm with everything else`() {
        val paused = block()
            .copy(pomodoroWork = 25.minutes, pomodoroPause = 5.minutes)
            .timerStarted(1.hours, t0)
            .timerPaused(t0 + 10.minutes)

        // An hour later the phase is where it was left.
        assertEquals(15.minutes, paused.timerPhase(t0 + 70.minutes)?.remaining)
    }

    @Test
    fun `a running timer announces every turn of its rhythm and its goal`() {
        val running = block()
            .copy(pomodoroWork = 25.minutes, pomodoroPause = 5.minutes)
            .timerStarted(1.hours, t0)

        assertEquals(
            listOf(
                TimerSignalKind.PAUSE to t0 + 25.minutes,
                TimerSignalKind.WORK to t0 + 30.minutes,
                TimerSignalKind.PAUSE to t0 + 55.minutes,
                TimerSignalKind.DONE to t0 + 60.minutes,
            ),
            running.timerSignals().map { it.kind to it.at },
        )
    }

    @Test
    fun `after a break by hand the announcements are counted from where it went on`() {
        val resumed = block()
            .copy(pomodoroWork = 25.minutes, pomodoroPause = 5.minutes)
            .timerStarted(1.hours, t0)
            .timerPaused(t0 + 27.minutes)
            .timerResumed(t0 + 2.hours)

        // Three minutes of the rhythm's pause were left, then work again.
        val signals = resumed.timerSignals()
        assertEquals(TimerSignalKind.WORK to t0 + 2.hours + 3.minutes, signals.first().let { it.kind to it.at })
        assertEquals(t0 + 2.hours + 33.minutes, signals.last().at)
    }

    @Test
    fun `a timer that is not running announces nothing`() {
        val started = block().timerStarted(1.hours, t0)

        assertTrue(block().timerSignals().isEmpty())
        assertTrue(started.timerPaused(t0 + 5.minutes).timerSignals().isEmpty())
        assertEquals(listOf(TimerSignalKind.DONE), started.timerSignals().map { it.kind })
    }

    // --- what a sitting is worth -----------------------------------------------------

    @Test
    fun `the goal is the block without its buffer`() {
        assertEquals(2.hours, block(duration = 130.minutes).fullTimerGoal(10.minutes))
        assertEquals(130.minutes, block(duration = 130.minutes).fullTimerGoal(Duration.ZERO))
    }

    @Test
    fun `a sitting worked in full bills the plan, one ended early the count and the buffer`() {
        val running = block(duration = 130.minutes).timerStarted(2.hours, t0)

        assertEquals(130.minutes, running.timerBilled(t0 + 2.hours, 10.minutes))
        assertEquals(55.minutes, running.timerBilled(t0 + 45.minutes, 10.minutes))
    }

    @Test
    fun `only a sitting billed at the plan counts as worked in full`() {
        val planned = block(duration = 130.minutes)

        // Ticked off without a figure: taken at its word.
        assertTrue(planned.workedInFull)
        assertTrue(planned.copy(actualDuration = 130.minutes).workedInFull)
        assertFalse(planned.copy(actualDuration = 55.minutes).workedInFull)
    }

    // --- growing, with a buffer behind it ------------------------------------------------

    @Test
    fun `growing moves the time on the timer and leaves the buffer as it was`() {
        val setting = GrowthSetting(15.minutes, 20.minutes, 5.minutes, dynamic = false)
        val task = item(buffer = 10.minutes)
            .copy(estimatedDuration = 15.minutes)
            .withGrowth(setting)
            .withBufferAdded()

        assertEquals(25.minutes, task.estimatedDuration)
        assertEquals(15.minutes, task.timerDuration)
        assertEquals(15.minutes, task.growthCurrent)

        val grown = task.advancedByCompletion()
        assertEquals(30.minutes, grown.estimatedDuration)
        assertEquals(20.minutes, grown.timerDuration)
        assertTrue(grown.isFullyGrown)
        // And there it stays.
        assertEquals(30.minutes, grown.advancedByCompletion().estimatedDuration)
    }

    @Test
    fun `a sitting that was not worked in full moves the count and not the time`() {
        val task = item()
            .copy(estimatedDuration = 15.minutes)
            .withGrowth(GrowthSetting(15.minutes, 1.hours, 5.minutes, dynamic = false))
            .withQuantity(QuantitySetting(start = 3, increment = 1))

        val short = task.advancedByCompletion(timeCounts = false)

        assertEquals(15.minutes, short.estimatedDuration)
        assertEquals(4, short.quantity)
    }

    // --- started late ---------------------------------------------------------------------

    private fun entry(block: PlannedBlock, buffer: Duration? = null) =
        BlockWithItem(block, item(block.id, buffer).copy(id = block.itemId))

    @Test
    fun `a start the buffer absorbs asks nothing`() {
        // 10:00 to 12:10 — two hours and ten minutes of buffer.
        val lernen = entry(block("a"), buffer = 10.minutes)
        val next = entry(block("n", LocalTime(12, 10), 1.hours))

        assertNull(timerOverflow(listOf(lernen, next), lernen, nowMinute = 10 * 60 + 10, goal = 2.hours))
    }

    @Test
    fun `a late start with nothing behind it asks nothing either`() {
        val lernen = entry(block("a"), buffer = 10.minutes)

        assertNull(timerOverflow(listOf(lernen), lernen, nowMinute = 10 * 60 + 30, goal = 2.hours))
    }

    @Test
    fun `a late start that runs into the next task offers both ways out`() {
        val lernen = entry(block("a"), buffer = 10.minutes)
        val next = entry(block("n", LocalTime(12, 10), 1.hours))

        val overflow = timerOverflow(listOf(lernen, next), lernen, nowMinute = 10 * 60 + 30, goal = 2.hours)

        assertEquals("n", overflow?.next?.block?.id)
        // The timer stops where the next task begins …
        assertEquals(100.minutes, overflow?.shortenedGoal)
        // … or the next task begins when the timer is through, and ends as planned.
        assertEquals(LocalTime(12, 30), overflow?.pushedNext?.start)
        assertEquals(40.minutes, overflow?.pushedNext?.plannedDuration)
    }

    @Test
    fun `an appointment out of the calendar is never the one to give way`() {
        val lernen = entry(block("a"), buffer = 10.minutes)
        val next = entry(block("n", LocalTime(12, 10), 1.hours, origin = BlockOrigin.CALENDAR_IMPORT))

        val overflow = timerOverflow(listOf(lernen, next), lernen, nowMinute = 10 * 60 + 30, goal = 2.hours)

        assertEquals(100.minutes, overflow?.shortenedGoal)
        assertNull(overflow?.pushedNext)
    }

    @Test
    fun `a task the timer would swallow whole cannot simply begin later`() {
        val lernen = entry(block("a"), buffer = 10.minutes)
        val next = entry(block("n", LocalTime(12, 10), 15.minutes))

        val overflow = timerOverflow(listOf(lernen, next), lernen, nowMinute = 10 * 60 + 30, goal = 2.hours)

        assertNull(overflow?.pushedNext)
        assertEquals(100.minutes, overflow?.shortenedGoal)
    }

    // --- the figure on the screen ---------------------------------------------------------

    @Test
    fun `a countdown reads to the second and rounds up`() {
        assertEquals("1:05:09", (1.hours + 5.minutes + 9.seconds).formatCountdown())
        assertEquals("24:59", (24.minutes + 59.seconds).formatCountdown())
        assertEquals("00:01", 400.seconds.div(1000).formatCountdown())
        assertEquals("00:00", Duration.ZERO.formatCountdown())
    }
}
