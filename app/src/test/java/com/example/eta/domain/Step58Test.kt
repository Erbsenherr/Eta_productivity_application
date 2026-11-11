package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.reward.BalanceKey
import com.example.eta.domain.reward.Balancing
import com.example.eta.domain.reward.decodeBalancing
import com.example.eta.domain.reward.encodeBalancing
import com.example.eta.domain.reward.yieldOf
import com.example.eta.domain.stats.Trend
import com.example.eta.domain.stats.accuracy
import com.example.eta.domain.stats.weekReview
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 58: the Wochenrückblick, and the Punkte-Balancing. */
class Step58Test {

    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private val today = LocalDate(2026, 11, 15)

    @After
    fun resetBalancing() {
        Balancing.values = emptyMap()
    }

    private fun task(date: LocalDate, done: Boolean): BlockWithItem {
        val item = Item.newQuickTodo("Lernen", now).copy(category = Category.FOKUS)
        return BlockWithItem(
            PlannedBlock(
                itemId = item.id,
                date = date,
                start = LocalTime(9, 0),
                plannedDuration = 1.hours,
                origin = BlockOrigin.DRAGGED,
                completedAt = if (done) now else null,
                createdAt = now,
                updatedAt = now,
            ),
            item,
        )
    }

    // --- Wochenrückblick --------------------------------------------------------------

    @Test
    fun `last week is set against every day before it`() {
        val weekFrom = LocalDate(2026, 11, 8)
        val weekTo = LocalDate(2026, 11, 14)
        val blocks = listOf(
            // Before: one of four done, 25 %.
            task(LocalDate(2026, 11, 1), done = true),
            task(LocalDate(2026, 11, 2), done = false),
            task(LocalDate(2026, 11, 3), done = false),
            task(LocalDate(2026, 11, 4), done = false),
            // Last week: two of two, 100 %.
            task(LocalDate(2026, 11, 9), done = true),
            task(LocalDate(2026, 11, 10), done = true),
        )
        val review = weekReview(blocks, emptyList(), weekFrom, weekTo, today) { 8 * 60 }

        assertEquals(2, review.tasksDone)
        assertTrue(review.hasHistory)
        val completed = review.figures.first { it.label == "Aufgaben abgeschlossen" }
        assertEquals(100, completed.week.percent)
        assertEquals(25, completed.before.percent)
        assertEquals(Trend.BETTER, completed.trend)
        // Nothing answered about sleep on either side.
        assertEquals(Trend.UNKNOWN, review.figures.first { it.label == "Pünktlich aufgestanden" }.trend)
    }

    @Test
    fun `a first week has nothing to compare with`() {
        val review = weekReview(
            listOf(task(LocalDate(2026, 11, 9), done = false)),
            emptyList(),
            LocalDate(2026, 11, 8),
            LocalDate(2026, 11, 14),
            today,
        ) { 0 }
        assertFalse(review.hasHistory)
        assertFalse(review.isEmpty)
    }

    @Test
    fun `accuracy counts what happened as laid down`() {
        val share = accuracy(listOf(task(today, done = true), task(today, done = false)))
        assertEquals(50, share.percent)
    }

    // --- Punkte-Balancing --------------------------------------------------------------

    @Test
    fun `only what differs from its default is written, and read back`() {
        val values = mapOf(BalanceKey.FOCUS to 1.5, BalanceKey.SIDELINE to 0.5)
        val text = encodeBalancing(values)
        assertEquals("FOCUS=1.5", text)
        assertEquals(mapOf(BalanceKey.FOCUS to 1.5), decodeBalancing(text))
        assertEquals(emptyMap<BalanceKey, Double>(), decodeBalancing("NONSENSE=1,FOCUS=x,"))
    }

    @Test
    fun `a tuned multiplier changes what an hour pays`() {
        assertEquals(1.0, yieldOf(Category.FOKUS, 1.hours), 1e-9)
        Balancing.values = mapOf(BalanceKey.FOCUS to 2.0)
        assertEquals(2.0, yieldOf(Category.FOKUS, 1.hours), 1e-9)
    }
}
