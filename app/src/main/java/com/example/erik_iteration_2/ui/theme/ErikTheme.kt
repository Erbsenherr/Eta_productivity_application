package com.example.erik_iteration_2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.erik_iteration_2.domain.model.Category
import androidx.compose.ui.graphics.Color

private val LocalErikColors = staticCompositionLocalOf<ErikColors> {
    error("No ErikColors provided — wrap the content in ErikTheme.")
}
private val LocalErikTypography = staticCompositionLocalOf { DefaultErikTypography }
private val LocalErikShapes = staticCompositionLocalOf { ErikShapes() }
private val LocalErikSpacing = staticCompositionLocalOf { ErikSpacing() }

/**
 * The app's design system, built on Compose Foundation only.
 *
 * There is deliberately no Material dependency: the revolver, the drag-and-drop
 * timeline and the card shapes that encode where a block came from all need full
 * control over gesture and paint, which Material components would fight.
 */
@Composable
fun ErikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalErikColors provides if (darkTheme) DarkErikColors else LightErikColors,
        LocalErikTypography provides DefaultErikTypography,
        LocalErikShapes provides ErikShapes(),
        LocalErikSpacing provides ErikSpacing(),
        content = content,
    )
}

/** Access point for the tokens, mirroring how MaterialTheme is normally read. */
object ErikTheme {
    val colors: ErikColors
        @Composable @ReadOnlyComposable get() = LocalErikColors.current

    val typography: ErikTypography
        @Composable @ReadOnlyComposable get() = LocalErikTypography.current

    val shapes: ErikShapes
        @Composable @ReadOnlyComposable get() = LocalErikShapes.current

    val spacing: ErikSpacing
        @Composable @ReadOnlyComposable get() = LocalErikSpacing.current
}

/** The accent a task carries, so category colouring never gets hand-mapped per screen. */
@Composable
@ReadOnlyComposable
fun colorOf(category: Category?): Color = when (category) {
    Category.FOKUS -> ErikTheme.colors.fokus
    Category.NEBENBEI -> ErikTheme.colors.nebenbei
    Category.ACHTSAM -> ErikTheme.colors.achtsam
    null -> ErikTheme.colors.textMuted
}
