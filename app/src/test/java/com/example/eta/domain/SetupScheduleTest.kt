package com.example.eta.domain

import com.example.eta.domain.setup.DailySlot
import com.example.eta.domain.setup.MealPlan
import com.example.eta.domain.setup.SetupLabels
import com.example.eta.domain.setup.TimeSpan
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WeeklySlot
import com.example.eta.domain.setup.WorkBlock
import com.example.eta.domain.setup.WorkSchedule
import com.example.eta.domain.setup.WorkSegmentKind
import com.example.eta.domain.setup.bedPrepDuration
import com.example.eta.domain.setup.conflicts
import com.example.eta.domain.setup.freeMinutesPerWeek
import com.example.eta.domain.setup.sleepDuration
import com.example.eta.domain.setup.weeklySpans
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupScheduleTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")

    /** A deliberately empty week, so each test only adds what it is about. */
    private fun bareSetup() = UserSetup.draft(now).copy(
        bedPrepTime = LocalTime(22, 0),
        sleepTime = LocalTime(23, 0),
        wakeTime = LocalTime(7, 0),
        morningDuration = Duration.ZERO,
        meals = MealPlan.DailyCooking(emptyList()),
        housekeeping = null,
        sport = null,
        freeTime = DailySlot(LocalTime(20, 0), Duration.ZERO),
        socialTimePerWeek = Duration.ZERO,
        mindfulness = null,
        work = WorkSchedule.None,
    )

    private fun workday(from: LocalTime, to: LocalTime, pause: TimeSpan? = null) =
        WorkSchedule.EveryWorkday(WorkBlock(TimeSpan(from, to), pause))

    @Test
    fun `the night wraps past midnight instead of being cut off`() {
        val setup = bareSetup()
        assertEquals(8.hours, setup.sleepDuration())
        assertEquals(1.hours, setup.bedPrepDuration())

        val monday = setup.weeklySpans().filter {
            it.weekday == DayOfWeek.MONDAY && it.label == SetupLabels.SLEEP
        }
        // Monday holds the tail of Sunday night and the start of its own.
        assertEquals(2, monday.size)
        assertEquals(8 * 60, monday.sumOf { it.minutes })
    }

    @Test
    fun `a single sleep answer is not a conflict with itself`() {
        assertTrue(bareSetup().conflicts().isEmpty())
    }

    @Test
    fun `a break inside the working day is not a conflict`() {
        val setup = bareSetup().copy(
            work = workday(
                from = LocalTime(9, 0),
                to = LocalTime(17, 0),
                pause = TimeSpan(LocalTime(12, 30), LocalTime(13, 15)),
            ),
        )
        assertTrue(setup.conflicts().isEmpty())
    }

    @Test
    fun `a break splits the working day into three pieces`() {
        val block = WorkBlock(
            span = TimeSpan(LocalTime(9, 0), LocalTime(17, 0)),
            pause = TimeSpan(LocalTime(12, 30), LocalTime(13, 15)),
        )
        val segments = block.segments()

        assertEquals(
            listOf(WorkSegmentKind.WORK, WorkSegmentKind.PAUSE, WorkSegmentKind.WORK),
            segments.map { it.kind },
        )
        assertEquals(TimeSpan(LocalTime(9, 0), LocalTime(12, 30)), segments[0].span)
        assertEquals(TimeSpan(LocalTime(13, 15), LocalTime(17, 0)), segments[2].span)
        // The break costs the day nothing extra — it comes out of the work hours.
        assertEquals(8 * 60, segments.sumOf { it.span.duration.inWholeMinutes }.toInt())
    }

    @Test
    fun `a break outside the working day is ignored rather than moved`() {
        val block = WorkBlock(
            span = TimeSpan(LocalTime(9, 0), LocalTime(17, 0)),
            pause = TimeSpan(LocalTime(18, 0), LocalTime(18, 30)),
        )
        assertEquals(listOf(WorkSegmentKind.WORK), block.segments().map { it.kind })
        assertTrue(block.hasUnusablePause())
    }

    @Test
    fun `a break at the very edge does not leave an empty stretch behind`() {
        val block = WorkBlock(
            span = TimeSpan(LocalTime(9, 0), LocalTime(17, 0)),
            pause = TimeSpan(LocalTime(9, 0), LocalTime(9, 30)),
        )
        assertEquals(
            listOf(WorkSegmentKind.PAUSE, WorkSegmentKind.WORK),
            block.segments().map { it.kind },
        )
    }

    @Test
    fun `work running into the night collides with the evening and the night`() {
        val setup = bareSetup().copy(work = workday(LocalTime(14, 0), LocalTime(23, 30)))
        val conflicts = setup.conflicts()

        fun between(a: String, b: String) = conflicts.filter {
            setOf(it.first.label, it.second.label) == setOf(a, b)
        }

        // Work until 23:30 eats the whole hour of winding down and half an hour
        // of the night — on each of the five workdays.
        val withBedPrep = between(SetupLabels.WORK, SetupLabels.BED_PREP)
        val withSleep = between(SetupLabels.WORK, SetupLabels.SLEEP)
        assertEquals(5, withBedPrep.size)
        assertEquals(5, withSleep.size)
        assertEquals(conflicts.size, withBedPrep.size + withSleep.size)
        assertTrue(withBedPrep.all { it.toMinute - it.fromMinute == 60 })
        assertTrue(withSleep.all { it.toMinute - it.fromMinute == 30 })
        assertTrue(withSleep.none { it.weekday == DayOfWeek.SATURDAY })
    }

    @Test
    fun `touching appointments do not count as a conflict`() {
        val setup = bareSetup().copy(
            sport = WeeklySlot(DayOfWeek.MONDAY, LocalTime(17, 0), 1.hours),
            work = workday(LocalTime(9, 0), LocalTime(17, 0)),
        )
        assertTrue(setup.conflicts().isEmpty())
    }

    @Test
    fun `a weekly answer only collides on its own weekday`() {
        val setup = bareSetup().copy(
            sport = WeeklySlot(DayOfWeek.TUESDAY, LocalTime(9, 0), 1.hours),
            work = workday(LocalTime(9, 0), LocalTime(17, 0)),
        )
        val conflicts = setup.conflicts()
        assertEquals(1, conflicts.size)
        assertEquals(DayOfWeek.TUESDAY, conflicts.single().weekday)
    }

    @Test
    fun `free hours subtract sleep, appointments and the social budget`() {
        val setup = bareSetup().copy(socialTimePerWeek = 3.hours)
        // 7 × 24 h minus 7 × 9 h of night and winding down, minus 3 h social.
        assertEquals((7 * 24 - 7 * 9 - 3) * 60, setup.freeMinutesPerWeek())
    }

    @Test
    fun `overlapping appointments are only subtracted once`() {
        val overlapping = bareSetup().copy(
            sport = WeeklySlot(DayOfWeek.MONDAY, LocalTime(16, 30), 1.hours),
            work = workday(LocalTime(9, 0), LocalTime(17, 0)),
        )
        // Monday is busy 09:00–17:30, not 9 h: the shared half hour counts once.
        val expected = (7 * 24 - 7 * 9) * 60 - 4 * 8 * 60 - 510
        assertEquals(expected, overlapping.freeMinutesPerWeek())
    }

    @Test
    fun `a duration of zero produces no appointment at all`() {
        val setup = bareSetup().copy(morningDuration = 0.minutes)
        assertTrue(setup.weeklySpans().none { it.label == SetupLabels.MORNING })
    }
}
