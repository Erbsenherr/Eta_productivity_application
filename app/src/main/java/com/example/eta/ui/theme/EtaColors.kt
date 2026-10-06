package com.example.eta.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette. Which values fill it is the design's business — see [AppDesign];
 * the legacy one is warm neutrals with an indigo accent, calm enough to sit behind
 * a dense planner without competing with the cards on it.
 *
 * The three category colours are part of the palette rather than local constants
 * because Fokus/Nebenbei/Achtsam are shown on nearly every surface in the app.
 */
@Immutable
data class EtaColors(
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
    /**
     * A whole surface that is bad news, rather than a word that is.
     *
     * [danger] is for text and borders and stays legible on the ordinary
     * background; this is the wash behind a box that is itself the problem — a
     * contract slot locked by a breach. Kept as a token rather than a `danger`
     * with an alpha, so the dark theme can pick its own instead of getting a
     * washed-out red over a near-black ground.
     */
    val dangerSoft: Color,
    /**
     * The mirror of [dangerSoft]: a whole surface that is good news.
     *
     * The wash behind a stretch of the day that has been dealt with — a task
     * ticked off releases the time it was given, and the planner shades it rather
     * than leaving a finished block in the way. A token rather than [success] with
     * an alpha, for the same reason: the dark theme has to pick its own green
     * instead of getting a washed-out one over a near-black ground.
     */
    val successSoft: Color,
    val fokus: Color,
    val nebenbei: Color,
    val achtsam: Color,
    /** Sleep hours in the day planner, which are shaded rather than drawn as blocks. */
    val sleep: Color,
    /**
     * Imported calendar appointments.
     *
     * A colour of its own rather than a category colour, because what it says is
     * not "this is Fokus" but "this came from outside and is not yours to move".
     * Ochre because it has to sit next to Fokus, Nebenbei and Achtsam without
     * being mistaken for a fourth category — warm where all three are cool.
     */
    val calendar: Color,
    val isDark: Boolean,
)

/** The palette of the [AppDesign.LEGACY] design by day. */
val LegacyLightColors = EtaColors(
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
    dangerSoft = Color(0xFFFDECEC),
    successSoft = Color(0xFFE8F6EC),
    fokus = Color(0xFF4F46E5),
    nebenbei = Color(0xFF0891B2),
    achtsam = Color(0xFF059669),
    sleep = Color(0xFFEDEDF3),
    calendar = Color(0xFFB07D1A),
    isDark = false,
)

/** The palette of the [AppDesign.LEGACY] design by night. */
val LegacyDarkColors = EtaColors(
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
    dangerSoft = Color(0xFF2A1718),
    successSoft = Color(0xFF17251C),
    fokus = Color(0xFF818CF8),
    nebenbei = Color(0xFF22D3EE),
    achtsam = Color(0xFF34D399),
    sleep = Color(0xFF191922),
    calendar = Color(0xFFE3B341),
    isDark = true,
)

/** The red of the app icon's glyph, sampled from it. */
val EtaBrandRed = Color(0xFFE5322D)

/**
 * The palette of the [AppDesign.ETA] design: the app icon turned into a screen —
 * white, with its red wherever the app answers a touch.
 *
 * Two things had to move out of the red's way. **[danger] is a dark wine**, not a
 * second red: with a red accent, a red warning would look like one more button.
 * And **Fokus keeps its blue**, where the legacy palette lets it share the accent:
 * a day full of Fokus blocks in the accent colour would be a day painted red, next
 * to the red mark that says two blocks overlap.
 */
val EtaRedColors = EtaColors(
    background = Color(0xFFF8F6F5),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFECE6E4),
    textPrimary = Color(0xFF1B1717),
    textSecondary = Color(0xFF5F5654),
    textMuted = Color(0xFF978D8A),
    accent = EtaBrandRed,
    accentSoft = Color(0xFFFDEBE8),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    danger = Color(0xFF8B1538),
    dangerSoft = Color(0xFFF6DDE4),
    successSoft = Color(0xFFE6F5EA),
    fokus = Color(0xFF3F51B5),
    nebenbei = Color(0xFF0891B2),
    achtsam = Color(0xFF059669),
    sleep = Color(0xFFF0ECEB),
    calendar = Color(0xFFB07D1A),
    isDark = false,
)

/**
 * The palette of the [AppDesign.ETA_DARK] design: [EtaRedColors] with the lights
 * out. The ground is a near-black leaning warm, so the red sits on it rather than
 * vibrating against a blue-black, and the red itself is a shade lighter — the
 * icon's own is too dark to read as text on this ground.
 *
 * [danger] cannot be the wine it is by day, which would vanish here; it is a
 * rose, still clearly not the accent.
 */
val EtaRedDarkColors = EtaColors(
    background = Color(0xFF141112),
    surface = Color(0xFF1E1A1B),
    surfaceRaised = Color(0xFF282223),
    border = Color(0xFF372E2F),
    textPrimary = Color(0xFFF5F0EF),
    textSecondary = Color(0xFFB5AAA8),
    textMuted = Color(0xFF857A78),
    accent = Color(0xFFEB4640),
    accentSoft = Color(0xFF3A1D1C),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFFB7AA5),
    dangerSoft = Color(0xFF3A1A27),
    successSoft = Color(0xFF17251C),
    fokus = Color(0xFF8C9EFF),
    nebenbei = Color(0xFF22D3EE),
    achtsam = Color(0xFF34D399),
    sleep = Color(0xFF1C1819),
    calendar = Color(0xFFE3B341),
    isDark = true,
)
