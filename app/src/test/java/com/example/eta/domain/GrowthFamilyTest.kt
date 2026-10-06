package com.example.eta.domain

import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.growthFamily
import com.example.eta.domain.growth.withGrowth
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.recurrence.groupRecurring
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What holds the two halves of a two-weekday growth task together.
 *
 * The rule being guarded: the length is a property of the *task*, so both rows
 * carry the same one — which is also what keeps the standing schedule showing
 * one row for it rather than two.
 */
class GrowthFamilyTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val setting = GrowthSetting(15.minutes, 1.hours, 5.minutes, dynamic = false)

    private fun row(id: String, weekday: DayOfWeek, name: String = "Sport") = Item(
        id = id,
        type = ItemType.RECURRING,
        name = name,
        stage = Stage.DAY,
        category = Category.FOKUS,
        recurrenceRule = RecurrenceRule.Weekly(weekday),
        startTime = LocalTime(18, 0),
        estimatedDuration = 15.minutes,
        createdAt = now,
        updatedAt = now,
    ).withGrowth(setting)

    @Test
    fun `the weekdays of one task are one family`() {
        val tuesday = row("t", DayOfWeek.TUESDAY)
        val thursday = row("h", DayOfWeek.THURSDAY)
        assertEquals(tuesday.growthFamily, thursday.growthFamily)
    }

    @Test
    fun `two different tasks are not`() {
        assertNotEquals(
            row("t", DayOfWeek.TUESDAY).growthFamily,
            row("l", DayOfWeek.TUESDAY, name = "Lesen").growthFamily,
        )
    }

    @Test
    fun `equal lengths keep the standing schedule showing one row`() {
        val rows = listOf(row("t", DayOfWeek.TUESDAY), row("h", DayOfWeek.THURSDAY))
        assertEquals(1, groupRecurring(rows).size)

        // And the failure this guards against: one weekday grown alone.
        val drifted = listOf(rows[0].copy(estimatedDuration = 20.minutes), rows[1])
        assertEquals(2, groupRecurring(drifted).size)
    }
}
