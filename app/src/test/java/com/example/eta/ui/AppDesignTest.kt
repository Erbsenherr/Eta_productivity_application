package com.example.eta.ui

import androidx.compose.ui.graphics.Color
import com.example.eta.ui.theme.ACCENT_PRESETS
import com.example.eta.ui.theme.AppDesign
import com.example.eta.ui.theme.Brightness
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.ui.theme.EtaBrandRed
import com.example.eta.ui.theme.EtaRedColors
import com.example.eta.ui.theme.EtaRedDarkColors
import com.example.eta.ui.theme.EtaShapes
import com.example.eta.ui.theme.LegacyDarkColors
import com.example.eta.ui.theme.LegacyLightColors
import com.example.eta.ui.theme.TabStyle
import com.example.eta.ui.theme.accentOfHue
import com.example.eta.ui.theme.shapesOf
import com.example.eta.ui.theme.withAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDesignTest {

    @Test
    fun `stored names are read back, and anything else is the default`() {
        AppDesign.entries.forEach { design ->
            Brightness.entries.forEach { brightness ->
                assertEquals(
                    DesignChoice(design, brightness),
                    DesignChoice.fromStored(design.name, brightness.name),
                )
            }
        }
        assertEquals(DesignChoice(), DesignChoice.fromStored(null, null))
        assertEquals(DesignChoice(), DesignChoice.fromStored("a design that was removed", "?"))
        assertEquals(DesignChoice(AppDesign.ETA, Brightness.SYSTEM), DesignChoice())
    }

    /** `ETA_DARK` was a design of its own for one build. */
    @Test
    fun `the old dark design becomes eta, dark`() {
        assertEquals(
            DesignChoice(AppDesign.ETA, Brightness.DARK),
            DesignChoice.fromStored("ETA_DARK", null),
        )
    }

    @Test
    fun `both designs follow the phone unless told otherwise`() {
        val eta = DesignChoice(AppDesign.ETA)
        assertEquals(EtaRedColors, eta.colors(systemDark = false))
        assertEquals(EtaRedDarkColors, eta.colors(systemDark = true))

        val legacy = DesignChoice(AppDesign.LEGACY)
        assertEquals(LegacyLightColors, legacy.colors(systemDark = false))
        assertEquals(LegacyDarkColors, legacy.colors(systemDark = true))
    }

    @Test
    fun `a fixed brightness holds whatever the phone says`() {
        val light = DesignChoice(AppDesign.ETA, Brightness.LIGHT)
        assertEquals(EtaRedColors, light.colors(systemDark = true))
        assertEquals(EtaBrandRed, light.colors(systemDark = true).accent)

        val dark = DesignChoice(AppDesign.LEGACY, Brightness.DARK)
        assertEquals(LegacyDarkColors, dark.colors(systemDark = false))
    }

    @Test
    fun `one button steps through all three and comes back round`() {
        assertEquals(Brightness.LIGHT, Brightness.SYSTEM.next())
        assertEquals(Brightness.DARK, Brightness.LIGHT.next())
        assertEquals(Brightness.SYSTEM, Brightness.DARK.next())
    }

    @Test
    fun `an accent of one's own goes over either design, and can be read on`() {
        val teal = 0xFF1F9E8F.toInt()
        AppDesign.entries.forEach { design ->
            listOf(false, true).forEach { dark ->
                val colors = DesignChoice(design, accent = teal).colors(systemDark = dark)
                assertEquals(Color(teal), colors.accent)
                // Dark enough for white to stand on it.
                assertEquals(Color.White, colors.onAccent)
                // Everything that is not the accent's stays the design's.
                assertEquals(DesignChoice(design).colors(dark).danger, colors.danger)
                assertEquals(DesignChoice(design).colors(dark).fokus, colors.fokus)
            }
        }
        // A pale accent is written on in the dark instead.
        val pale = EtaRedColors.withAccent(Color(0xFFF2E36B))
        assertEquals(Color(0xFF14142B), pale.onAccent)
        // And none is the design's own.
        assertEquals(EtaBrandRed, DesignChoice(AppDesign.ETA, accent = null).colors(false).accent)
    }

    @Test
    fun `the accent and the tab style are read back beside the design`() {
        val stored = DesignChoice.fromStored("LEGACY", "DARK", accent = ACCENT_PRESETS.first(), tabStyle = "BOTH")
        assertEquals(DesignChoice(AppDesign.LEGACY, Brightness.DARK, ACCENT_PRESETS.first(), TabStyle.BOTH), stored)

        // Nothing stored, or a style that was removed: names, and the design's own colour.
        assertEquals(DesignChoice(), DesignChoice.fromStored(null, null, accent = null, tabStyle = "?"))
        assertEquals(TabStyle.NAMES, TabStyle.DEFAULT)
        assertTrue(TabStyle.ICONS.showsIcon && !TabStyle.ICONS.showsName)
        assertTrue(TabStyle.BOTH.showsIcon && TabStyle.BOTH.showsName)
        assertTrue(!TabStyle.NAMES.showsIcon && TabStyle.NAMES.showsName)
    }

    @Test
    fun `a hue off the bar is an opaque colour, whatever was touched`() {
        listOf(-20f, 0f, 180f, 359f, 400f).forEach { hue ->
            assertEquals(0xFF, accentOfHue(hue) ushr 24)
        }
    }

    /** The square corner is how the planner says a block is not the user's to move. */
    @Test
    fun `no design rounds the fixed block`() {
        AppDesign.entries.forEach {
            assertEquals(EtaShapes().fixedBlock, shapesOf(it).fixedBlock)
        }
    }
}
