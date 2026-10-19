package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.QuantitySetting
import com.example.eta.domain.growth.TimerSetting
import com.example.eta.domain.growth.withQuantity
import com.example.eta.domain.growth.withTimer
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.SleepLog
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.sleepPrompts
import com.example.eta.domain.reevaluation.settleDay
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.stats.Share
import com.example.eta.domain.stats.StatRange
import com.example.eta.domain.stats.StatSection
import com.example.eta.domain.stats.monthsFitBetter
import com.example.eta.domain.stats.overview
import com.example.eta.domain.stats.planChanged
import com.example.eta.domain.stats.quantityPensum
import com.example.eta.domain.stats.showsStat
import com.example.eta.domain.stats.taskStats
import com.example.eta.domain.stats.timerPensum
import com.example.eta.domain.stats.withStat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 43: what the dashboard asks about sleep and when, the half points that
 * come of it, and what the statistics count.
 */
class SleepAndStatisticsTest {

    private val now = Instant.parse("2026-10-13T06:00:00Z")
    private val monday = LocalDate(2026, 10, 12)
    private val tuesday = LocalDate(2026, 10, 13)

    /** Winding down from 22:30, asleep at 23:00, up at 07:00 — every night. */
    private val setup = UserSetup.draft(now).copy(
        bedPrepTime = LocalTime(22, 30),
        sleepTime = LocalTime(23, 0),
        wakeTime = LocalTime(7, 0),
        weekendNight = null,
        nightsByDay = null,
    )

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime(tuesday, LocalTime(hour, minute))

    private fun prompts(
        time: LocalDateTime,
        logs: List<SleepLog> = emptyList(),
        with: UserSetup = setup,
    ) = sleepPrompts(with, emptyMap(), logs.associateBy { it.date }, time)

    // --- the morning ---------------------------------------------------------------

    @Test
    fun `the morning box stands an hour either side of getting up`() {
        assertFalse(prompts(at(5, 59)).morning)
        assertTrue(prompts(at(6, 0)).morning)
        assertTrue(prompts(at(8, 0)).morning)
        assertFalse(prompts(at(8, 1)).morning)
        assertEquals(LocalTime(7, 0), prompts(at(6, 30)).wakeDue)
    }

    @Test
    fun `a morning left unanswered is asked for until the day ends`() {
        assertFalse(prompts(at(7, 30)).morningOwed)
        assertTrue(prompts(at(8, 1)).morningOwed)
        assertTrue(prompts(at(21, 0)).morningOwed)

        val answered = SleepLog(tuesday, wokeAt = LocalTime(7, 2), wakeDue = LocalTime(7, 0), updatedAt = now)
        assertFalse(prompts(at(21, 0), listOf(answered)).morningOwed)
        assertEquals(answered, prompts(at(7, 30), listOf(answered)).morningAnswer)
    }

    @Test
    fun `on time is at most five minutes after the hour, and earlier counts too`() {
        fun woke(hour: Int, minute: Int) =
            SleepLog(tuesday, wokeAt = LocalTime(hour, minute), wakeDue = LocalTime(7, 0), updatedAt = now)

        assertTrue(woke(6, 40).wokeOnTime == true)
        assertTrue(woke(7, 5).wokeOnTime == true)
        assertTrue(woke(7, 6).wokeOnTime == false)
        assertNull(SleepLog(tuesday, updatedAt = now).wokeOnTime)
    }

    // --- the evening ---------------------------------------------------------------

    @Test
    fun `the night box stands while winding down, and is filed under that evening's day`() {
        assertNull(prompts(at(22, 29)).eveningOf)
        assertEquals(tuesday, prompts(at(22, 30)).eveningOf)
        assertEquals(tuesday, prompts(at(22, 59)).eveningOf)
        // Step 48: on offer for the five minutes of tolerance past the bedtime.
        assertEquals(tuesday, prompts(at(23, 4)).eveningOf)
        assertNull(prompts(at(23, 5)).eveningOf)

        val pressed = SleepLog(tuesday, bedOnTime = true, updatedAt = now)
        assertTrue(prompts(at(22, 45), listOf(pressed)).eveningAnswered)
    }

    @Test
    fun `a bedtime after midnight still belongs to the day before`() {
        // Winding down from 00:00, asleep at 00:30, up at 08:00.
        val late = setup.copy(
            bedPrepTime = LocalTime(0, 0),
            sleepTime = LocalTime(0, 30),
            wakeTime = LocalTime(8, 0),
        )

        // Ten past midnight on Tuesday is how Monday ends.
        assertEquals(monday, prompts(at(0, 10), with = late).eveningOf)
        // And Monday is not asked about as "yesterday" before its bedtime has passed.
        assertNull(prompts(at(0, 10), with = late).bedOwedFor)
        // Not before the tolerance is over either (step 48).
        assertNull(prompts(at(0, 34), with = late).bedOwedFor)
        assertEquals(monday, prompts(at(0, 35), with = late).bedOwedFor)
    }

    @Test
    fun `yesterday's bedtime is asked about until it is answered`() {
        assertEquals(monday, prompts(at(9, 0)).bedOwedFor)

        val late = SleepLog(monday, bedOnTime = false, updatedAt = now)
        assertNull(prompts(at(9, 0), listOf(late)).bedOwedFor)
    }

    // --- the half points -------------------------------------------------------------

    @Test
    fun `each end of the night kept is worth half a point in the settlement`() {
        fun total(woke: Boolean, bed: Boolean) = settleDay(
            blocks = emptyList(),
            verdicts = emptyList(),
            sleepMinutes = 24 * 60,
            freeTimeAllowanceMinutes = 0,
            wokeOnTime = woke,
            bedOnTime = bed,
        ).total

        assertEquals(0.0, total(woke = false, bed = false), 0.0)
        assertEquals(0.5, total(woke = true, bed = false), 0.0)
        assertEquals(1.0, total(woke = true, bed = true), 0.0)
    }

    // --- statistics --------------------------------------------------------------------

    private fun item(
        id: String,
        category: Category? = Category.FOKUS,
        role: ItemRole? = null,
    ) = Item(
        id = id,
        type = ItemType.RECURRING,
        name = id,
        stage = Stage.DAY,
        category = category,
        role = role,
        createdAt = now,
        updatedAt = now,
    )

    private fun block(
        id: String,
        date: LocalDate = monday,
        from: LocalTime = LocalTime(10, 0),
        duration: Duration = 1.hours,
        done: Boolean = false,
        discarded: Boolean = false,
        excused: Boolean = false,
        original: Duration? = duration,
        originalStart: LocalTime? = from,
    ) = PlannedBlock(
        id = id,
        itemId = id,
        date = date,
        start = from,
        plannedDuration = duration,
        origin = BlockOrigin.RECURRING,
        completedAt = now.takeIf { done },
        discardedAt = now.takeIf { discarded },
        forceMajeure = "Sturm".takeIf { excused },
        originalStart = originalStart,
        originalDuration = original,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `the overview counts what was taken on, and leaves the frame and force majeure out`() {
        val blocks = listOf(
            BlockWithItem(block("a", done = true), item("a")),
            BlockWithItem(block("b", from = LocalTime(12, 0)), item("b", Category.NEBENBEI)),
            // The frame of the day is not a task that was taken on.
            BlockWithItem(block("p", from = LocalTime(14, 0), done = true), item("p", category = null, role = ItemRole.BREAK)),
            // Nobody's doing: in neither half of the rate.
            BlockWithItem(block("x", from = LocalTime(16, 0), discarded = true, excused = true), item("x")),
        )

        val figures = overview(blocks, logs = emptyList(), sleepMinutesOn = { 8 * 60 })

        assertEquals(Share(1, 2), figures.completed)
        assertEquals(50, figures.completed.percent)
        assertEquals(mapOf(Category.FOKUS to 60), figures.categoryMinutes)
        // Three hours stood on a day of sixteen waking ones; the cancellation gave its hour back.
        assertEquals(Share(180, 16 * 60), figures.planned)
    }

    @Test
    fun `mornings and evenings are counted only where they were answered`() {
        val logs = listOf(
            SleepLog(monday, wokeAt = LocalTime(7, 0), wakeDue = LocalTime(7, 0), bedOnTime = false, updatedAt = now),
            SleepLog(tuesday, wokeAt = LocalTime(7, 30), wakeDue = LocalTime(7, 0), updatedAt = now),
        )

        val figures = overview(emptyList(), logs, sleepMinutesOn = { 0 })

        assertEquals(Share(1, 2), figures.wake)
        assertEquals(Share(0, 1), figures.bed)
        assertNull(figures.planned.percent)
    }

    @Test
    fun `a changed plan is one that differs from how the schedule laid it down`() {
        assertEquals(false, block("a").planChanged())
        assertEquals(true, block("a", duration = 45.minutes, original = 1.hours).planChanged())
        assertEquals(true, block("a", from = LocalTime(11, 0), originalStart = LocalTime(10, 0)).planChanged())
        // A dynamically placed task's hour is the app's doing.
        assertEquals(
            false,
            block("a", from = LocalTime(11, 0), originalStart = LocalTime(10, 0)).planChanged(ignoreStart = true),
        )
        // Older than the stamp: not known, and not counted either way.
        assertNull(block("a", original = null).planChanged())
    }

    @Test
    fun `a task's rates are over its occurrences, as planned being the strict one`() {
        val blocks = listOf(
            block("1", done = true),
            block("2", done = true, duration = 45.minutes, original = 1.hours),
            block("3", discarded = true),
            block("4", done = true, original = null),
            block("5", discarded = true, excused = true),
        )

        val stats = taskStats(blocks)

        assertEquals(165.minutes, stats.time)
        assertEquals(Share(1, 4), stats.skipped)
        // Of the three whose original plan is known, one was changed.
        assertEquals(Share(1, 3), stats.changed)
        // Finished and not changed: the first, and the one whose plan is not known.
        assertEquals(Share(2, 4), stats.asPlanned)
    }

    @Test
    fun `the workload of a timer task is its timer, without buffer or late extension`() {
        val task = item("lernen").withTimer(TimerSetting(10.minutes))
        val blocks = listOf(
            block("1", date = monday, duration = 40.minutes),
            block("2", date = tuesday, duration = 55.minutes).copy(timerExtension = 10.minutes),
        )

        assertEquals(listOf(30.0, 35.0), timerPensum(blocks, task).map { it.value })
    }

    @Test
    fun `the count of a task is what it was done with, and where it stands today`() {
        val task = item("klimmzuege").withQuantity(QuantitySetting(start = 5, increment = 1))
        val blocks = listOf(
            block("1", date = monday, done = true).copy(quantityDone = 4),
            block("2", date = monday, done = true),
        )

        val points = quantityPensum(blocks, task, today = tuesday)

        assertEquals(listOf(monday to 4.0, tuesday to 5.0), points.map { it.date to it.value })
    }

    @Test
    fun `sections are shown until they are hidden, and a range counts back from today`() {
        val fresh = UserSetup.draft(now)
        assertTrue(StatSection.entries.all(fresh::showsStat))

        val hidden = fresh.withStat(StatSection.BED, shown = false)
        assertFalse(hidden.showsStat(StatSection.BED))
        assertTrue(hidden.showsStat(StatSection.WAKE))
        assertTrue(hidden.withStat(StatSection.BED, shown = true).showsStat(StatSection.BED))

        assertEquals(LocalDate(2026, 10, 6), StatRange.WEEK.from(tuesday))
        assertNull(StatRange.ALL.from(tuesday))
        assertFalse(monthsFitBetter(LocalDate(2026, 9, 13), tuesday))
        assertTrue(monthsFitBetter(LocalDate(2026, 1, 1), tuesday))
    }
}
