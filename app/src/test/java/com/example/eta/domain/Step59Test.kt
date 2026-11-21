package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.growthCurrent
import com.example.eta.domain.growth.withQuantitySetTo
import com.example.eta.domain.growth.withTimeSetTo
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.SleepLog
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.sleepPoints
import com.example.eta.domain.reevaluation.DeferredTask
import com.example.eta.domain.reevaluation.heldItsTime
import com.example.eta.domain.reevaluation.settleDay
import com.example.eta.domain.stats.Mood
import com.example.eta.domain.stats.ReviewFigure
import com.example.eta.domain.stats.Share
import com.example.eta.domain.stats.affirmation
import com.example.eta.domain.stats.dayReview
import com.example.eta.domain.stats.moodOf
import com.example.eta.domain.streak.taskDoneOn
import kotlin.time.Duration.Companion.hours
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

/**
 * Step 59: graded sleep points, "Stand setzen", the affirmations, and
 * "Sich der Task verschreiben".
 */
class Step59Test {

    private val now = Instant.parse("2026-11-21T08:00:00Z")
    private val day = LocalDate(2026, 11, 20)

    // --- graded sleep ----------------------------------------------------------------

    @Test
    fun `five minutes pay in full, five pay nothing, then a quarter begun costs half a point`() {
        fun points(late: Int) = sleepPoints(late, tolerance = 5, full = 0.5)
        assertEquals(0.5, points(-30), 0.0)
        assertEquals(0.5, points(5), 0.0)
        assertEquals(0.0, points(6), 0.0)
        assertEquals(0.0, points(10), 0.0)
        assertEquals(-0.5, points(11), 0.0)
        assertEquals(-0.5, points(25), 0.0)
        assertEquals(-1.0, points(26), 0.0)
        assertEquals(-3.5, points(115), 0.0)
        assertEquals(-4.0, points(116), 0.0)
        // Counted up to two hours, and no further.
        assertEquals(-4.0, points(120), 0.0)
        assertEquals(-4.0, points(300), 0.0)
    }

    @Test
    fun `the tolerance is the zone that pays in full`() {
        assertEquals(0.5, sleepPoints(15, tolerance = 15, full = 0.5), 0.0)
        assertEquals(0.0, sleepPoints(20, tolerance = 15, full = 0.5), 0.0)
        assertEquals(-0.5, sleepPoints(21, tolerance = 15, full = 0.5), 0.0)
    }

    @Test
    fun `a bedtime kept after midnight is counted across it`() {
        val log = SleepLog(
            date = day,
            bedAt = LocalTime(0, 15),
            bedDue = LocalTime(23, 30),
            bedTolerance = 5,
            updatedAt = now,
        )
        assertEquals(45, log.bedLateMinutes)
        assertEquals(-1.5, log.bedPoints, 0.0)

        val early = log.copy(bedAt = LocalTime(22, 50))
        assertEquals(-40, early.bedLateMinutes)
        assertEquals(0.5, early.bedPoints, 0.0)
    }

    @Test
    fun `a morning is graded, and an answer without an hour keeps the old rule`() {
        val late = SleepLog(day, wokeAt = LocalTime(7, 40), wakeDue = LocalTime(7, 0), tolerance = 5, updatedAt = now)
        assertEquals(-1.0, late.wakePoints, 0.0)
        assertEquals(0.0, SleepLog(day, updatedAt = now).wakePoints, 0.0)

        assertEquals(0.5, SleepLog(day, bedOnTime = true, updatedAt = now).bedPoints, 0.0)
        assertEquals(0.0, SleepLog(day, bedOnTime = false, updatedAt = now).bedPoints, 0.0)
    }

    @Test
    fun `graded points stand in for the half points, a deduction included`() {
        val settlement = settleDay(
            blocks = emptyList(),
            verdicts = emptyList(),
            sleepMinutes = 24 * 60,
            freeTimeAllowanceMinutes = 0,
            wokeOnTime = true,
            wakePoints = -1.5,
            bedPoints = 0.0,
        )
        assertEquals(-1.5, settlement.wakeBonus, 0.0)
        assertEquals(-1.5, settlement.total, 0.0)
    }

    // --- Stand setzen ----------------------------------------------------------------

    private fun growing() = Item.newQuickTodo("Planke", now).copy(
        recurrenceRule = RecurrenceRule.Daily,
        timerTask = true,
        timerBuffer = 5.minutes,
        estimatedDuration = 15.minutes,
        growthStart = 10.minutes,
        growthTarget = 30.minutes,
        growthIncrement = 1.minutes,
        growthProgress = 2,
        growthEvery = 3,
        timerMalusBase = 12.minutes,
    )

    @Test
    fun `the time is set by hand, within the target, the buffer behind it`() {
        val set = growing().withTimeSetTo(20.minutes)
        assertEquals(20.minutes, set.growthCurrent)
        assertEquals(25.minutes, set.estimatedDuration)
        assertEquals(0, set.growthProgress)
        assertNull(set.timerMalusBase)

        assertEquals(30.minutes, growing().withTimeSetTo(2.hours).growthCurrent)
    }

    @Test
    fun `the count is set by hand, within its target`() {
        val counted = Item.newQuickTodo("Klimmzüge", now).copy(
            recurrenceRule = RecurrenceRule.Daily,
            quantity = 4,
            quantityStart = 1,
            quantityIncrement = 1,
            quantityTarget = 20,
            quantityProgress = 1,
            quantityEvery = 2,
        )
        val set = counted.withQuantitySetTo(12)
        assertEquals(12, set.quantity)
        assertEquals(0, set.quantityProgress)
        assertEquals(20, counted.withQuantitySetTo(50).quantity)
        assertEquals(1, counted.withQuantitySetTo(0).quantity)
    }

    // --- affirmations ----------------------------------------------------------------

    private fun figure(week: Int, before: Int) = ReviewFigure("x", Share(week, 100), Share(before, 100))

    @Test
    fun `worse encourages, as good or better praises, nothing known says nothing`() {
        assertEquals(Mood.WORSE, moodOf(listOf(figure(40, 60), figure(50, 50))))
        assertEquals(Mood.USUAL, moodOf(listOf(figure(50, 52), figure(50, 50))))
        assertEquals(Mood.BETTER, moodOf(listOf(figure(80, 60), figure(50, 50))))
        assertEquals(Mood.USUAL, moodOf(listOf(figure(80, 60), figure(40, 60))))
        assertNull(moodOf(listOf(ReviewFigure("x", Share(0, 0), Share(3, 4)))))
        assertTrue(affirmation(Mood.WORSE, 0).isNotBlank())
        assertEquals(affirmation(Mood.BETTER, 7), affirmation(Mood.BETTER, 7))
    }

    // --- Sich der Task verschreiben -----------------------------------------------------

    private fun item(id: String) = Item(
        id = id,
        type = ItemType.RECURRING,
        name = id,
        stage = Stage.DAY,
        category = Category.FOKUS,
        createdAt = now,
        updatedAt = now,
    )

    private fun block(id: String, committed: Boolean = false, done: Boolean = false) = PlannedBlock(
        id = id,
        itemId = id,
        date = day,
        start = LocalTime(22, 0),
        plannedDuration = 1.hours,
        origin = BlockOrigin.RECURRING,
        completedAt = now.takeIf { done },
        committedAt = now.takeIf { committed },
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a commitment holds its hours and leaves a streak standing`() {
        val committed = BlockWithItem(block("abendroutine", committed = true), item("abendroutine"))
        assertTrue(committed.heldItsTime())
        assertTrue(committed.block.isCommitmentOwed)
        assertNull(taskDoneOn("abendroutine", listOf(committed), TimeZone.UTC))

        val plain = BlockWithItem(block("abendroutine"), item("abendroutine"))
        assertFalse(plain.heldItsTime())
        assertEquals(false, taskDoneOn("abendroutine", listOf(plain), TimeZone.UTC))
    }

    @Test
    fun `tonight's comparison leaves a commitment out`() {
        val today = listOf(
            BlockWithItem(block("a", done = true), item("a")),
            BlockWithItem(block("b", committed = true), item("b")),
        )
        val figures = dayReview(today, earlier = emptyList())
        assertEquals(Share(1, 1), figures.first().week)
    }

    @Test
    fun `delayed tasks are paid with the next settlement`() {
        val settlement = settleDay(
            blocks = emptyList(),
            verdicts = emptyList(),
            sleepMinutes = 24 * 60,
            freeTimeAllowanceMinutes = 0,
            deferred = listOf(DeferredTask("b", "Abendroutine", 0.75), DeferredTask("c", "Lesen", 1.0)),
        )
        assertEquals(1.75, settlement.deferredTotal, 0.0)
        assertEquals(1.75, settlement.total, 0.0)
    }
}
