package com.example.eta.domain

import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.QuantitySetting
import com.example.eta.domain.growth.advancedByCompletion
import com.example.eta.domain.growth.countCompletion
import com.example.eta.domain.growth.growthSetting
import com.example.eta.domain.growth.hasQuantity
import com.example.eta.domain.growth.isQuantityReached
import com.example.eta.domain.growth.quantityFamily
import com.example.eta.domain.growth.quantityLabel
import com.example.eta.domain.growth.withGrowth
import com.example.eta.domain.growth.withQuantity
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemExtras
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.withExtras
import com.example.eta.ui.format.formatShort
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update condition, and the Mengen-Inkrement (step 24). */
class QuantityTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")

    private fun definition(weekday: DayOfWeek = DayOfWeek.MONDAY) = Item(
        type = ItemType.RECURRING,
        name = "Klimmzüge",
        stage = Stage.DAY,
        category = Category.FOKUS,
        recurrenceRule = RecurrenceRule.Weekly(weekday),
        startTime = LocalTime(18, 0),
        estimatedDuration = 15.minutes,
        createdAt = now,
        updatedAt = now,
    )

    // ---- the update condition ---------------------------------------------

    @Test
    fun `every completion fires when the condition is one`() {
        assertEquals(0 to true, countCompletion(progress = 0, every = 1))
    }

    @Test
    fun `with two the first completion only counts and the second fires`() {
        assertEquals(1 to false, countCompletion(progress = 0, every = 2))
        assertEquals(0 to true, countCompletion(progress = 1, every = 2))
    }

    @Test
    fun `a growth task with a condition of two grows every second completion`() {
        val item = definition().withGrowth(GrowthSetting(15.minutes, 1.hours, 5.minutes, false, every = 2))
        val once = item.advancedByCompletion()
        assertEquals(15.minutes, once.estimatedDuration)
        assertEquals(1, once.growthProgress)
        val twice = once.advancedByCompletion()
        assertEquals(20.minutes, twice.estimatedDuration)
        assertEquals(0, twice.growthProgress)
    }

    @Test
    fun `growth may be set to the second`() {
        val item = definition().withGrowth(GrowthSetting(1.minutes, 3.minutes, 30.seconds, false))
        assertEquals(1.minutes + 30.seconds, item.advancedByCompletion().estimatedDuration)
    }

    @Test
    fun `the growth condition survives the round trip through the item`() {
        val setting = GrowthSetting(15.minutes, 1.hours, 5.minutes, true, every = 3)
        assertEquals(setting, definition().withGrowth(setting).growthSetting)
    }

    @Test
    fun `moving the growth start resets the counted completions`() {
        val setting = GrowthSetting(15.minutes, 1.hours, 5.minutes, false, every = 3)
        val counted = definition().withGrowth(setting).advancedByCompletion()
        assertEquals(1, counted.growthProgress)
        assertEquals(1, counted.withGrowth(setting.copy(increment = 10.minutes)).growthProgress)
        assertEquals(0, counted.withGrowth(setting.copy(start = 20.minutes)).growthProgress)
    }

    // ---- the Mengen-Inkrement ---------------------------------------------

    @Test
    fun `switching the count on starts at the start`() {
        val item = definition().withQuantity(QuantitySetting(start = 1, increment = 1))
        assertTrue(item.hasQuantity)
        assertEquals(1, item.quantity)
        assertEquals("Anzahl 1", item.quantityLabel())
    }

    @Test
    fun `every confirmed completion adds the increment`() {
        val item = definition().withQuantity(QuantitySetting(start = 1, increment = 1))
        val afterThree = item.advancedByCompletion().advancedByCompletion().advancedByCompletion()
        assertEquals(4, afterThree.quantity)
    }

    @Test
    fun `the count honours its own update condition`() {
        val item = definition().withQuantity(QuantitySetting(start = 5, increment = 2, every = 2))
        val once = item.advancedByCompletion()
        assertEquals(5, once.quantity)
        assertEquals(7, once.advancedByCompletion().quantity)
    }

    @Test
    fun `the count stops at its target`() {
        val item = definition().withQuantity(QuantitySetting(start = 8, increment = 3, target = 10))
        val grown = item.advancedByCompletion()
        assertEquals(10, grown.quantity)
        assertTrue(grown.isQuantityReached)
        assertEquals(10, grown.advancedByCompletion().quantity)
        assertEquals("Anzahl 10 / 10", grown.quantityLabel())
    }

    @Test
    fun `the count leaves the length alone`() {
        val item = definition().withQuantity(QuantitySetting(start = 1, increment = 1))
        assertEquals(15.minutes, item.advancedByCompletion().estimatedDuration)
    }

    @Test
    fun `an edit that leaves the start alone keeps the count reached`() {
        val setting = QuantitySetting(start = 1, increment = 1)
        val reached = definition().withQuantity(setting).copy(quantity = 7)
        assertEquals(7, reached.withQuantity(setting.copy(increment = 2)).quantity)
        assertEquals(3, reached.withQuantity(setting.copy(start = 3)).quantity)
        // A target lowered below where it stands pulls the count down to it.
        assertEquals(5, reached.withQuantity(setting.copy(target = 5)).quantity)
    }

    @Test
    fun `the count travels through the extras and comes off with them`() {
        val setting = QuantitySetting(start = 2, increment = 1, every = 2, target = 20)
        val item = definition().withExtras(ItemExtras(quantity = setting))
        assertEquals(setting, ItemExtras.of(item).quantity)
        val off = item.withExtras(ItemExtras())
        assertFalse(off.hasQuantity)
        assertNull(off.quantityLabel())
    }

    @Test
    fun `the weekdays of one counted task are one family`() {
        val setting = QuantitySetting(start = 1, increment = 1)
        val monday = definition(DayOfWeek.MONDAY).withQuantity(setting)
        val wednesday = definition(DayOfWeek.WEDNESDAY).withQuantity(setting)
        assertEquals(monday.quantityFamily, wednesday.quantityFamily)
    }

    // ---- durations to the second ------------------------------------------

    @Test
    fun `seconds are printed only where a duration has them`() {
        assertEquals("45 min", 45.minutes.formatShort())
        assertEquals("1:30 h", 90.minutes.formatShort())
        assertEquals("12 min 30 s", (12.minutes + 30.seconds).formatShort())
        assertEquals("45 s", 45.seconds.formatShort())
        assertEquals("1:30:20 h", (90.minutes + 20.seconds).formatShort())
    }
}
