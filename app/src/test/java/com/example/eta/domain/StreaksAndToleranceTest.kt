package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.HabitStreak
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Reward
import com.example.eta.domain.model.SleepLog
import com.example.eta.domain.model.StreakKind
import com.example.eta.domain.planning.sleepPrompts
import com.example.eta.domain.reevaluation.CONTRACT_BREACH_PENALTY
import com.example.eta.domain.reevaluation.ContractVerdict
import com.example.eta.domain.reevaluation.settleDay
import com.example.eta.domain.reward.RewardTarget
import com.example.eta.domain.reward.SleepBonus
import com.example.eta.domain.reward.pourInto
import com.example.eta.domain.reward.sleepEarnings
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.streak.STREAK_BREAK_PENALTY
import com.example.eta.domain.streak.STREAK_MAX_PER_STREAK
import com.example.eta.domain.streak.STREAK_MAX_TOTAL
import com.example.eta.domain.streak.StreakVerdict
import com.example.eta.domain.streak.seriesOutcome
import com.example.eta.domain.streak.shownLength
import com.example.eta.domain.streak.streakOutcomes
import com.example.eta.ui.format.formatFinePoints
import com.example.eta.ui.theme.orderTabs
import com.example.eta.ui.theme.reorderVisible
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 48: streaks and the series, the breach charge, the tolerance for "on
 * time", the morning that ends with its routine, the sleep bonuses in the
 * Belohn-o-mat, and the tab order.
 */
class StreaksAndToleranceTest {

    private val utc = TimeZone.UTC
    private val now = Instant.parse("2026-10-13T20:00:00Z")
    private val tuesday = LocalDate(2026, 10, 13)
    private val monday = LocalDate(2026, 10, 12)

    private fun entry(
        name: String,
        completed: Boolean = false,
        discardedAt: Instant? = null,
        excuse: String? = null,
        date: LocalDate = tuesday,
    ): BlockWithItem {
        val item = Item.newRecurring(
            id = "item-$name",
            name = name,
            category = Category.FOKUS,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = LocalTime(9, 0),
            estimatedDuration = 1.hours,
            now = now,
        )
        return BlockWithItem(
            block = PlannedBlock(
                id = "block-$name",
                itemId = item.id,
                date = date,
                start = LocalTime(9, 0),
                plannedDuration = 1.hours,
                origin = BlockOrigin.RECURRING,
                completedAt = if (completed) now else null,
                discardedAt = discardedAt,
                forceMajeure = excuse,
                createdAt = now,
                updatedAt = now,
            ),
            item = item,
        )
    }

    private fun streak(
        name: String?,
        length: Int,
        kind: StreakKind = if (name == null) StreakKind.SLEEP else StreakKind.TASK,
        lastCountedOn: LocalDate? = monday,
    ) = HabitStreak(
        id = "streak-${name ?: kind.name}",
        kind = kind,
        taskName = name?.lowercase(),
        label = name ?: kind.name,
        length = length,
        lastCountedOn = lastCountedOn,
        startedAt = now,
        updatedAt = now,
    )

    private fun outcomes(
        streaks: List<HabitStreak>,
        blocks: List<BlockWithItem> = emptyList(),
        woke: Boolean = false,
        bed: Boolean = false,
    ) = streakOutcomes(streaks, tuesday, blocks, woke, bed, timeZone = utc)

    // --- task streaks ----------------------------------------------------------------

    @Test
    fun `a task done today counts one further, and one not due today stands still`() {
        val result = outcomes(
            listOf(streak("Sport", 4), streak("Arbeiten", 9)),
            blocks = listOf(entry("Sport", completed = true)),
        )
        assertEquals(StreakVerdict.EXTENDED, result[0].verdict)
        assertEquals(5, result[0].after)
        // No block for Arbeiten today — the weekend, say. Nothing breaks.
        assertEquals(StreakVerdict.NOT_DUE, result[1].verdict)
        assertEquals(9, result[1].after)
        assertEquals(0.0, result[1].payout, 1e-9)
    }

    @Test
    fun `a task due and not done breaks, and costs ten points`() {
        val result = outcomes(listOf(streak("Sport", 4)), blocks = listOf(entry("Sport")))
        assertEquals(StreakVerdict.BROKEN, result.single().verdict)
        assertEquals(0, result.single().after)
        assertEquals(STREAK_BREAK_PENALTY, result.single().penalty, 1e-9)
    }

    @Test
    fun `called off on the day it breaks, called off ahead or excused it does not`() {
        val onTheDay = entry("Sport", discardedAt = Instant.parse("2026-10-13T08:00:00Z"))
        val ahead = entry("Sport", discardedAt = Instant.parse("2026-10-12T19:00:00Z"))
        val excused = entry("Sport", discardedAt = Instant.parse("2026-10-13T08:00:00Z"), excuse = "krank")

        assertEquals(StreakVerdict.BROKEN, outcomes(listOf(streak("Sport", 3)), listOf(onTheDay)).single().verdict)
        assertEquals(StreakVerdict.NOT_DUE, outcomes(listOf(streak("Sport", 3)), listOf(ahead)).single().verdict)
        assertEquals(StreakVerdict.NOT_DUE, outcomes(listOf(streak("Sport", 3)), listOf(excused)).single().verdict)
    }

    @Test
    fun `a streak still waiting for its first completion cannot break`() {
        val missed = outcomes(listOf(streak("Sport", 0)), blocks = listOf(entry("Sport"))).single()
        assertEquals(StreakVerdict.WAITING, missed.verdict)
        assertEquals(0.0, missed.penalty, 1e-9)

        val begun = outcomes(listOf(streak("Sport", 0)), blocks = listOf(entry("Sport", completed = true))).single()
        assertEquals(StreakVerdict.EXTENDED, begun.verdict)
        assertEquals(1, begun.after)
    }

    @Test
    fun `an evening already counted is not counted again`() {
        val counted = streak("Sport", 4, lastCountedOn = tuesday)
        assertTrue(outcomes(listOf(counted), listOf(entry("Sport", completed = true))).isEmpty())
    }

    @Test
    fun `the tab shows today's completion before the evening has counted it`() {
        assertEquals(5, streak("Sport", 4).shownLength(doneToday = true, today = tuesday))
        assertEquals(4, streak("Sport", 4).shownLength(doneToday = false, today = tuesday))
        assertEquals(4, streak("Sport", 4, lastCountedOn = tuesday).shownLength(true, tuesday))
    }

    // --- the sleep streak ---------------------------------------------------------------

    @Test
    fun `the sleep streak holds only when both ends were on time`() {
        val sleep = streak(null, 3)
        assertEquals(StreakVerdict.EXTENDED, outcomes(listOf(sleep), woke = true, bed = true).single().verdict)
        assertEquals(StreakVerdict.BROKEN, outcomes(listOf(sleep), woke = true, bed = false).single().verdict)
        // An unanswered end is not an on-time one.
        assertEquals(StreakVerdict.BROKEN, outcomes(listOf(sleep)).single().verdict)
    }

    // --- what they pay ---------------------------------------------------------------

    @Test
    fun `a streak pays a thousandth per day, at most one, and all together at most three`() {
        val short = outcomes(listOf(streak("Sport", 29)), listOf(entry("Sport", completed = true))).single()
        assertEquals(0.03, short.payout, 1e-9)

        val long = outcomes(listOf(streak("Sport", 2000)), listOf(entry("Sport", completed = true))).single()
        assertEquals(STREAK_MAX_PER_STREAK, long.payout, 1e-9)

        val names = (1..5).map { "Task$it" }
        val many = outcomes(
            names.map { streak(it, 2000) },
            names.map { entry(it, completed = true) },
        )
        assertEquals(STREAK_MAX_TOTAL, many.sumOf { it.payout }, 1e-9)
    }

    // --- the series ---------------------------------------------------------------

    private fun days(vararg day: Int) = day.map { LocalDate(2026, 10, it) }.toSet()

    private val series = streak(null, 5, kind = StreakKind.SERIES)

    @Test
    fun `the series runs on while every evening was settled`() {
        val result = seriesOutcome(series, days(8, 9, 10, 11, 12), tuesday)!!
        assertEquals(StreakVerdict.EXTENDED, result.verdict)
        assertEquals(6, result.after)
    }

    @Test
    fun `a missed evening breaks the series, which begins again at once`() {
        // Monday was never settled.
        val result = seriesOutcome(series, days(8, 9, 10, 11), tuesday)!!
        assertEquals(StreakVerdict.BROKEN, result.verdict)
        assertEquals(1, result.after)
    }

    @Test
    fun `a missed evening made up before this one keeps the series`() {
        // Sunday's evening was counted last; Monday was made up since.
        val result = seriesOutcome(series.copy(lastCountedOn = LocalDate(2026, 10, 11)), days(9, 10, 11, 12), tuesday)!!
        assertEquals(StreakVerdict.EXTENDED, result.verdict)
    }

    @Test
    fun `a series never counted takes the run as it stands, unbroken`() {
        val fresh = series.copy(length = 0, lastCountedOn = null)
        val result = seriesOutcome(fresh, days(10, 11, 12), tuesday)!!
        assertEquals(StreakVerdict.EXTENDED, result.verdict)
        assertEquals(4, result.after)
        assertNull(seriesOutcome(series.copy(lastCountedOn = tuesday), days(12), tuesday))
    }

    // --- the settlement ---------------------------------------------------------------

    private fun contract(state: ContractState) = Contract(
        slot = if (state == ContractState.LEGACY) null else 0,
        title = "t",
        conditions = "c",
        breachDefinition = "b",
        effort = ContractEffort.MITTEL,
        signature = "P.",
        signedOn = tuesday,
        endsOn = tuesday,
        state = state,
        createdAt = now,
        updatedAt = now,
    )

    private fun settle(verdicts: List<ContractVerdict> = emptyList(), streaks: List<StreakVerdict> = emptyList()) =
        settleDay(
            blocks = emptyList(),
            verdicts = verdicts,
            sleepMinutes = 24 * 60,
            freeTimeAllowanceMinutes = 0,
            streaks = outcomes(
                streaks.mapIndexed { index, _ -> streak("S$index", 10) },
                streaks.mapIndexedNotNull { index, verdict ->
                    when (verdict) {
                        StreakVerdict.EXTENDED -> entry("S$index", completed = true)
                        StreakVerdict.BROKEN -> entry("S$index")
                        else -> null
                    }
                },
            ),
        )

    @Test
    fun `a slot contract broken costs ten points once, a legacy one and a probation nothing`() {
        val broken = settle(listOf(ContractVerdict(contract(ContractState.ACTIVE), kept = false, keepServing = true)))
        assertEquals(1, broken.contractsBroken)
        assertEquals(-CONTRACT_BREACH_PENALTY, broken.total, 1e-9)

        assertEquals(0.0, settle(listOf(ContractVerdict(contract(ContractState.LEGACY), kept = false))).total, 1e-9)
        assertEquals(0.0, settle(listOf(ContractVerdict(contract(ContractState.PROBATION), kept = false))).total, 1e-9)
    }

    @Test
    fun `the settlement books what the streaks pay and what breaking them costs`() {
        val result = settle(streaks = listOf(StreakVerdict.EXTENDED, StreakVerdict.BROKEN))
        assertEquals(0.011, result.streakPayout, 1e-9)
        assertEquals(1, result.streaksBroken)
        assertEquals(0.011 - STREAK_BREAK_PENALTY, result.total, 1e-9)
    }

    // --- on time ---------------------------------------------------------------

    private val setup = UserSetup.draft(now).copy(
        bedPrepTime = LocalTime(22, 30),
        sleepTime = LocalTime(23, 0),
        wakeTime = LocalTime(7, 0),
        weekendNight = null,
        nightsByDay = null,
    )

    @Test
    fun `a morning keeps the tolerance it was answered under`() {
        fun woke(minute: Int, tolerance: Int?) = SleepLog(
            tuesday,
            wokeAt = LocalTime(7, minute),
            wakeDue = LocalTime(7, 0),
            tolerance = tolerance,
            updatedAt = now,
        ).wokeOnTime

        assertEquals(true, woke(5, null))
        assertEquals(false, woke(6, null))
        assertEquals(true, woke(20, 20))
        assertEquals(false, woke(1, 0))
    }

    @Test
    fun `the tolerance keeps the bed button on offer past the bedtime`() {
        val wide = setup.copy(punctualTolerance = 15)
        fun at(hour: Int, minute: Int) = LocalDateTime(tuesday, LocalTime(hour, minute))
        assertEquals(tuesday, sleepPrompts(wide, emptyMap(), emptyMap(), at(23, 14)).eveningOf)
        assertNull(sleepPrompts(wide, emptyMap(), emptyMap(), at(23, 15)).eveningOf)
        assertEquals(15, sleepPrompts(wide, emptyMap(), emptyMap(), at(9, 0)).tolerance)
    }

    @Test
    fun `a finished morning routine ends Guten Morgen, and an open question moves to its box`() {
        val time = LocalDateTime(tuesday, LocalTime(7, 10))
        val open = sleepPrompts(setup, emptyMap(), emptyMap(), time)
        assertTrue(open.morning)
        assertFalse(open.morningOwed)

        val done = sleepPrompts(setup, emptyMap(), emptyMap(), time, morningDone = true)
        assertFalse(done.morning)
        assertTrue(done.morningOwed)

        val answered = SleepLog(tuesday, wokeAt = LocalTime(7, 0), wakeDue = LocalTime(7, 0), updatedAt = now)
        assertFalse(
            sleepPrompts(setup, emptyMap(), mapOf(tuesday to answered), time, morningDone = true).morningOwed,
        )
    }

    // --- the Belohn-o-mat ---------------------------------------------------------------

    private fun reward(id: String, position: Int) = Reward(
        id = id,
        name = id,
        cost = 10.0,
        position = position,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `the sleep bonuses fill a reward bound to them, and flow on when unbound`() {
        val earnings = sleepEarnings(wakeBonus = 0.5, bedBonus = 0.5)
        val bound = RewardTarget(reward("bound", 2), setOf(SleepBonus.WAKE.taskName))
        val open = RewardTarget(reward("open", 1))

        val gains = pourInto(listOf(open, bound), earnings).associate { it.reward.id to it.added }
        assertEquals(0.5, gains["bound"]!!, 1e-9)
        assertEquals(0.5, gains["open"]!!, 1e-9)
        assertTrue(sleepEarnings(0.0, 0.0).isEmpty())
    }

    // --- small things ---------------------------------------------------------------

    @Test
    fun `fine points show the thousandth`() {
        assertEquals("0.001", formatFinePoints(0.001))
        assertEquals("0.03", formatFinePoints(0.03))
        assertEquals("1", formatFinePoints(1.0))
    }

    @Test
    fun `the tab order keeps the user's order and slots a new tab in after its predecessor`() {
        val defaults = listOf("Today", "Lists", "Notes", "Streaks", "Settings")
        assertEquals(defaults, orderTabs(defaults, emptyList()))
        // Stored before Streaks existed, with Settings moved to the front.
        assertEquals(
            listOf("Settings", "Lists", "Today", "Notes", "Streaks"),
            orderTabs(defaults, listOf("Settings", "Lists", "Today", "Notes", "Gone")),
        )
        // Only the visible ones are reordered; a hidden tab keeps its slot.
        assertEquals(
            listOf("Notes", "Lists", "Today", "Streaks", "Settings"),
            reorderVisible(defaults, listOf("Notes", "Lists", "Today", "Settings")),
        )
    }
}
