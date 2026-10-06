package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.blocksStartingAt
import com.example.eta.domain.planning.nextTaskStart
import com.example.eta.domain.planning.startsAt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStartTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    /** UTC, so the expected instants can be read straight off the wall clock. */
    private val zone = TimeZone.UTC

    private fun entry(
        id: String,
        at: LocalTime,
        completed: Boolean = false,
        discarded: Boolean = false,
        on: LocalDate = date,
    ): BlockWithItem {
        val item = Item.newRecurring(
            id = "item-$id",
            name = id,
            category = Category.FOKUS,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = at,
            estimatedDuration = 1.hours,
            now = now,
        )
        return BlockWithItem(
            block = PlannedBlock(
                id = id,
                itemId = item.id,
                date = on,
                start = at,
                plannedDuration = 1.hours,
                origin = BlockOrigin.RECURRING,
                completedAt = if (completed) now else null,
                discardedAt = if (discarded) now else null,
                createdAt = now,
                updatedAt = now,
            ),
            item = item,
        )
    }

    @Test
    fun `a block's start is its wall clock time in the given zone`() {
        assertEquals(
            Instant.parse("2026-09-01T09:00:00Z"),
            entry("a", LocalTime(9, 0)).block.startsAt(zone),
        )
    }

    @Test
    fun `the next start is the earliest one still ahead`() {
        val blocks = listOf(
            entry("frueh", LocalTime(5, 0)),
            entry("gleich", LocalTime(9, 0)),
            entry("spaeter", LocalTime(14, 0)),
        )
        assertEquals(
            Instant.parse("2026-09-01T09:00:00Z"),
            nextTaskStart(blocks, now, zone),
        )
    }

    @Test
    fun `a block already ticked off or dropped does not announce itself`() {
        val blocks = listOf(
            entry("erledigt", LocalTime(9, 0), completed = true),
            entry("abgesagt", LocalTime(10, 0), discarded = true),
            entry("offen", LocalTime(11, 0)),
        )
        assertEquals(
            Instant.parse("2026-09-01T11:00:00Z"),
            nextTaskStart(blocks, now, zone),
        )
    }

    @Test
    fun `a day with nothing left says so, rather than guessing`() {
        // Null is the signal to cancel the alarm; anything else would keep one
        // standing that has nothing to announce.
        assertNull(nextTaskStart(listOf(entry("vorbei", LocalTime(5, 0))), now, zone))
        assertNull(nextTaskStart(emptyList(), now, zone))
    }

    @Test
    fun `tomorrow morning is the next start after a late evening`() {
        val evening = Instant.parse("2026-09-01T22:00:00Z")
        val blocks = listOf(
            entry("heute", LocalTime(20, 0)),
            entry("morgen", LocalTime(6, 30), on = LocalDate(2026, 9, 2)),
        )
        assertEquals(
            Instant.parse("2026-09-02T06:30:00Z"),
            nextTaskStart(blocks, evening, zone),
        )
    }

    @Test
    fun `everything starting on the same minute is announced, not just one`() {
        val blocks = listOf(
            entry("a", LocalTime(9, 0)),
            entry("b", LocalTime(9, 0)),
            entry("c", LocalTime(10, 0)),
        )
        val ringing = blocksStartingAt(blocks, Instant.parse("2026-09-01T09:00:00Z"), zone)
        assertEquals(setOf("a", "b"), ringing.map { it.block.id }.toSet())
    }

    @Test
    fun `an alarm delivered late still announces the minute it was set for`() {
        // The inexact fallback can arrive minutes after the fact. What rings is
        // decided by the minute the alarm was laid down for, not by the clock.
        val blocks = listOf(entry("a", LocalTime(9, 0)), entry("b", LocalTime(9, 10)))
        val ringing = blocksStartingAt(blocks, Instant.parse("2026-09-01T09:00:00Z"), zone)
        assertEquals(listOf("a"), ringing.map { it.block.id })
    }

    @Test
    fun `a block finished before its hour is not announced when the alarm arrives`() {
        val blocks = listOf(entry("a", LocalTime(9, 0), completed = true))
        assertTrue(
            blocksStartingAt(blocks, Instant.parse("2026-09-01T09:00:00Z"), zone).isEmpty(),
        )
    }
}
