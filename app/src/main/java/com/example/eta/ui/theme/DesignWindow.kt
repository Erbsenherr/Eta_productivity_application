package com.example.eta.ui.theme

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb

/**
 * Makes the window agree with the design: edge to edge, and the two things
 * Compose does not paint.
 *
 * - **The system bar icons.** Plain `enableEdgeToEdge()` picks them from the
 *   device's dark mode, which is right for a design that follows it and wrong for
 *   one that is light or dark by choice — white icons over a white screen.
 * - **The window background**, which shows until the first frame. The theme in the
 *   manifest can only know the device's mode, not the user's choice.
 *
 * Call it in `onCreate`, and again whenever the design changes.
 */
fun ComponentActivity.applyDesignToWindow(design: AppDesign) {
    val systemDark = resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    val colors = colorsOf(design, systemDark)

    when {
        design.followsSystemDark -> enableEdgeToEdge()
        colors.isDark -> enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(DARK_SCRIM),
        )
        else -> enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(LIGHT_SCRIM, DARK_SCRIM),
        )
    }
    window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
}

// The scrims `enableEdgeToEdge()` uses by default, which it does not expose.
private val LIGHT_SCRIM = AndroidColor.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = AndroidColor.argb(0x80, 0x1b, 0x1b, 0x1b)
