package com.example.eta.ui

import com.example.eta.ui.theme.AppDesign
import com.example.eta.ui.theme.EtaBrandRed
import com.example.eta.ui.theme.EtaShapes
import com.example.eta.ui.theme.LegacyDarkColors
import com.example.eta.ui.theme.LegacyLightColors
import com.example.eta.ui.theme.colorsOf
import com.example.eta.ui.theme.shapesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDesignTest {

    @Test
    fun `a stored name is read back, and anything else is the default`() {
        AppDesign.entries.forEach { assertEquals(it, AppDesign.fromStored(it.name)) }
        assertEquals(AppDesign.DEFAULT, AppDesign.fromStored(null))
        assertEquals(AppDesign.DEFAULT, AppDesign.fromStored("a design that was removed"))
    }

    @Test
    fun `legacy keeps both of its palettes and follows the device`() {
        assertEquals(LegacyLightColors, colorsOf(AppDesign.LEGACY, systemDark = false))
        assertEquals(LegacyDarkColors, colorsOf(AppDesign.LEGACY, systemDark = true))
    }

    @Test
    fun `the eta design is light and red whatever the device says`() {
        val dark = colorsOf(AppDesign.ETA, systemDark = true)
        assertEquals(colorsOf(AppDesign.ETA, systemDark = false), dark)
        assertEquals(EtaBrandRed, dark.accent)
        assertFalse(dark.isDark)
    }

    @Test
    fun `eta dunkel is dark whatever the device says`() {
        val byDay = colorsOf(AppDesign.ETA_DARK, systemDark = false)
        assertEquals(colorsOf(AppDesign.ETA_DARK, systemDark = true), byDay)
        assertTrue(byDay.isDark)
        assertFalse(AppDesign.ETA_DARK.followsSystemDark)
    }

    /** The square corner is how the planner says a block is not the user's to move. */
    @Test
    fun `no design rounds the fixed block`() {
        AppDesign.entries.forEach {
            assertEquals(EtaShapes().fixedBlock, shapesOf(it).fixedBlock)
        }
    }
}
