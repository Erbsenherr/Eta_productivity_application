package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Stage
import com.example.erik_iteration_2.domain.staging.banDate
import com.example.erik_iteration_2.domain.staging.criticalThreshold
import com.example.erik_iteration_2.domain.staging.daysUntilBan
import com.example.erik_iteration_2.domain.staging.isCriticalInCollection
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalTodoTest {

    private val tz = TimeZone.of("Europe/Berlin")
    private val entered = Instant.parse("2026-01-10T09:00:00Z")
    private val item = Item.newQuickTodo("Keller aufräumen", entered)

    @Test
    fun `the ban date sits one month after entering the Sammelliste`() {
        assertEquals(LocalDate(2026, 2, 10), item.banDate(tz))
    }

    @Test
    fun `days until ban counts down`() {
        val now = Instant.parse("2026-02-05T12:00:00Z")
        assertEquals(5, item.daysUntilBan(now, tz))
    }

    @Test
    fun `within a week of the ban the todo counts as critical`() {
        val now = Instant.parse("2026-02-05T12:00:00Z")
        assertTrue(item.isCriticalInCollection(now, tz))
    }

    @Test
    fun `more than a week out it is not yet critical`() {
        val now = Instant.parse("2026-02-01T12:00:00Z")
        assertEquals(9, item.daysUntilBan(now, tz))
        assertFalse(item.isCriticalInCollection(now, tz))
    }

    @Test
    fun `an overdue todo stays critical rather than dropping off the list`() {
        val now = Instant.parse("2026-02-14T12:00:00Z")
        assertEquals(-4, item.daysUntilBan(now, tz))
        assertTrue(item.isCriticalInCollection(now, tz))
    }

    @Test
    fun `items outside the Sammelliste have no ban date at all`() {
        val planned = item.copy(stage = Stage.WEEK)
        assertNull(planned.banDate(tz))
        assertNull(planned.daysUntilBan(Instant.parse("2026-02-05T12:00:00Z"), tz))
        assertFalse(planned.isCriticalInCollection(Instant.parse("2026-02-05T12:00:00Z"), tz))
    }

    @Test
    fun `the query threshold admits exactly the items inside the warning window`() {
        val now = Instant.parse("2026-02-05T12:00:00Z")
        // now - 1 month + 7 days
        assertEquals(Instant.parse("2026-01-12T12:00:00Z"), criticalThreshold(now, tz))
        assertTrue(entered <= criticalThreshold(now, tz))
    }
}
