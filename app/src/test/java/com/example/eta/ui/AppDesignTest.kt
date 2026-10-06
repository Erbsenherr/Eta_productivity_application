package com.example.eta.ui

import com.example.eta.ui.theme.AppDesign
import com.example.eta.ui.theme.Brightness
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.ui.theme.EtaBrandRed
import com.example.eta.ui.theme.EtaRedColors
import com.example.eta.ui.theme.EtaRedDarkColors
import com.example.eta.ui.theme.EtaShapes
import com.example.eta.ui.theme.LegacyDarkColors
import com.example.eta.ui.theme.LegacyLightColors
import com.example.eta.ui.theme.shapesOf
import org.junit.Assert.assertEquals
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

    /** The square corner is how the planner says a block is not the user's to move. */
    @Test
    fun `no design rounds the fixed block`() {
        AppDesign.entries.forEach {
            assertEquals(EtaShapes().fixedBlock, shapesOf(it).fixedBlock)
        }
    }
}
