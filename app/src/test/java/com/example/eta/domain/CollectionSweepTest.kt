package com.example.eta.domain

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
import com.example.eta.domain.staging.collectionTimeoutThreshold
import com.example.eta.domain.staging.isStaleInCollection
import com.example.eta.domain.staging.lockedForSperrliste
import kotlin.time.Instant
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionSweepTest {

    private val tz = TimeZone.of("Europe/Berlin")
    private val entered = Instant.parse("2026-01-10T09:00:00Z")

    private fun collectionTodo() = Item.newQuickTodo("Keller aufräumen", entered)

    @Test
    fun `a fresh todo is not stale`() {
        val item = collectionTodo()
        val threeWeeksLater = entered.plus(DateTimePeriod(days = 21), tz)
        assertFalse(item.isStaleInCollection(threeWeeksLater, tz))
    }

    @Test
    fun `a todo becomes stale exactly one month after entering the Sammelliste`() {
        val item = collectionTodo()
        val oneMonthLater = entered.plus(DateTimePeriod(months = 1), tz)
        assertTrue(item.isStaleInCollection(oneMonthLater, tz))
    }

    @Test
    fun `items outside the Sammelliste never go stale`() {
        val planned = collectionTodo().copy(stage = Stage.WEEK)
        val muchLater = entered.plus(DateTimePeriod(months = 6), tz)
        assertFalse(planned.isStaleInCollection(muchLater, tz))
    }

    @Test
    fun `locking moves the item to the Sperrliste for half a year`() {
        val now = entered.plus(DateTimePeriod(months = 1), tz)
        val locked = collectionTodo().lockedForSperrliste(now, tz)

        assertEquals(Stage.LOCKED, locked.stage)
        assertEquals(now.plus(DateTimePeriod(months = 6), tz), locked.lockedUntil)
    }

    @Test
    fun `the query threshold sits one month before now`() {
        val now = Instant.parse("2026-03-15T12:00:00Z")
        assertEquals(
            Instant.parse("2026-02-15T12:00:00Z"),
            collectionTimeoutThreshold(now, tz),
        )
    }
}
