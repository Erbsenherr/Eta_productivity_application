package com.example.eta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.eta.domain.model.Category
import androidx.compose.ui.graphics.Color

private val LocalEtaColors = staticCompositionLocalOf<EtaColors> {
    error("No EtaColors provided — wrap the content in EtaTheme.")
}
private val LocalEtaTypography = staticCompositionLocalOf { DefaultEtaTypography }
private val LocalEtaShapes = staticCompositionLocalOf { EtaShapes() }
private val LocalEtaSpacing = staticCompositionLocalOf { EtaSpacing() }

/**
 * The app's design system, built on Compose Foundation only.
 *
 * There is deliberately no Material dependency: the revolver, the drag-and-drop
 * timeline and the card shapes that encode where a block came from all need full
 * control over gesture and paint, which Material components would fight.
 *
 * [design] picks the tokens; every activity passes the one in `DesignStore`.
 */
@Composable
fun EtaTheme(
    design: AppDesign,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalEtaColors provides colorsOf(design, darkTheme),
        LocalEtaTypography provides DefaultEtaTypography,
        LocalEtaShapes provides shapesOf(design),
        LocalEtaSpacing provides EtaSpacing(),
        content = content,
    )
}

/** Access point for the tokens, mirroring how MaterialTheme is normally read. */
object EtaTheme {
    val colors: EtaColors
        @Composable @ReadOnlyComposable get() = LocalEtaColors.current

    val typography: EtaTypography
        @Composable @ReadOnlyComposable get() = LocalEtaTypography.current

    val shapes: EtaShapes
        @Composable @ReadOnlyComposable get() = LocalEtaShapes.current

    val spacing: EtaSpacing
        @Composable @ReadOnlyComposable get() = LocalEtaSpacing.current
}

/** The accent a task carries, so category colouring never gets hand-mapped per screen. */
@Composable
@ReadOnlyComposable
fun colorOf(category: Category?): Color = when (category) {
    Category.FOKUS -> EtaTheme.colors.fokus
    Category.NEBENBEI -> EtaTheme.colors.nebenbei
    Category.ACHTSAM -> EtaTheme.colors.achtsam
    null -> EtaTheme.colors.textMuted
}
