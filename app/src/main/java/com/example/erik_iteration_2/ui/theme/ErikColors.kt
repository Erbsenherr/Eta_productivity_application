package com.example.erik_iteration_2.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette. Warm neutrals with an indigo accent — calm enough to sit behind a
 * dense planner without competing with the cards on it.
 *
 * The three category colours are part of the palette rather than local constants
 * because Fokus/Nebenbei/Achtsam are shown on nearly every surface in the app.
 */
@Immutable
data class ErikColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val fokus: Color,
    val nebenbei: Color,
    val achtsam: Color,
    /** Sleep hours in the day planner, which are shaded rather than drawn as blocks. */
    val sleep: Color,
    val isDark: Boolean,
)

val LightErikColors = ErikColors(
    background = Color(0xFFFAFAF8),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFE4E2DD),
    textPrimary = Color(0xFF1A1A17),
    textSecondary = Color(0xFF5C5A54),
    textMuted = Color(0xFF8E8B83),
    accent = Color(0xFF4F46E5),
    accentSoft = Color(0xFFEEF0FF),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    danger = Color(0xFFB91C1C),
    fokus = Color(0xFF4F46E5),
    nebenbei = Color(0xFF0891B2),
    achtsam = Color(0xFF059669),
    sleep = Color(0xFFEDEDF3),
    isDark = false,
)

val DarkErikColors = ErikColors(
    background = Color(0xFF111113),
    surface = Color(0xFF1B1B1F),
    surfaceRaised = Color(0xFF232329),
    border = Color(0xFF2E2E36),
    textPrimary = Color(0xFFF2F2F0),
    textSecondary = Color(0xFFA8A6A0),
    textMuted = Color(0xFF77756F),
    accent = Color(0xFF818CF8),
    accentSoft = Color(0xFF262A45),
    onAccent = Color(0xFF14142B),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    fokus = Color(0xFF818CF8),
    nebenbei = Color(0xFF22D3EE),
    achtsam = Color(0xFF34D399),
    sleep = Color(0xFF191922),
    isDark = true,
)
