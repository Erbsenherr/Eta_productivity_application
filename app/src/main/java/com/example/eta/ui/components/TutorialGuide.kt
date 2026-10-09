package com.example.eta.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Clock

/**
 * The tutorial's line to the screens it is shown on.
 *
 * The tutorial runs the app's own screens over a practice database, so nearly
 * everything it needs to know it reads from there. Two things no table holds: a
 * screen has to be told which of its parts is being talked about ([spot]), and
 * has to say which of its pages is in front ([report]).
 *
 * Null outside the tutorial, which is every ordinary run of the app — a screen
 * that reads it must do nothing at all then.
 */
@Stable
class TutorialGuide(private val onReport: (key: String, value: String) -> Unit) {

    /** The part of the screen the current step points at, by its id. */
    var spot: String? by mutableStateOf(null)

    /**
     * The buttons of a screen the current step lets through, by their gate.
     *
     * A screen's own "Weiter", "Übernehmen" or "Abschließen" pressed before the
     * tutorial has got there leaves the two out of step: the screen a page on,
     * the coach still explaining the last one. So in the tutorial such a button
     * is grey until its step — see [tutorialAllows].
     */
    var allowed: Set<String> by mutableStateOf(emptySet())

    fun report(key: String, value: String) = onReport(key, value)
}

/**
 * Whether a button may be pressed right now. Always, outside the tutorial;
 * inside it, only while the current step names [gate].
 */
@Composable
fun tutorialAllows(gate: String): Boolean {
    val guide = LocalTutorialGuide.current ?: return true
    return gate in guide.allowed
}

val LocalTutorialGuide = staticCompositionLocalOf<TutorialGuide?> { null }

/**
 * The clock a screen reads the time from, where it reads it itself.
 *
 * The system's, except in the tutorial, whose simulated day has a clock of its
 * own — the dashboard's "Gerade" box would otherwise go by the phone's hour
 * while everything beneath it went by the tutorial's.
 */
val LocalEtaClock = staticCompositionLocalOf<Clock> { Clock.System }

/** Says which page of a screen is in front, whenever that changes. */
@Composable
fun ReportToTutorial(key: String, value: String) {
    val guide = LocalTutorialGuide.current ?: return
    LaunchedEffect(guide, key, value) { guide.report(key, value) }
}

private val SPOT_OUTSET = 6.dp
private val SPOT_STROKE = 2.dp
private const val SPOT_PULSE_MILLIS = 900

/**
 * Marks a part of a screen the tutorial can point at.
 *
 * While the current step names [id], the part is framed in the accent colour —
 * drawn **outside** its bounds, so nothing shifts when the frame comes and goes
 * — and scrolled into view. At every other time, and always outside the
 * tutorial, this is the modifier it was given.
 */
@Composable
fun Modifier.tutorialSpot(id: String): Modifier {
    val guide = LocalTutorialGuide.current ?: return this
    if (guide.spot != id) return this

    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(id) {
        // A frame first: the part may have been composed this very frame and
        // has no place on the screen to be scrolled to yet.
        withFrameNanos { }
        requester.bringIntoView()
    }

    val pulse by rememberInfiniteTransition(label = "tutorialSpot").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SPOT_PULSE_MILLIS), RepeatMode.Reverse),
        label = "tutorialSpotPulse",
    )
    val color = EtaTheme.colors.accent

    return this
        .bringIntoViewRequester(requester)
        .drawBehind {
            val outset = SPOT_OUTSET.toPx()
            drawRoundRect(
                color = color.copy(alpha = pulse),
                topLeft = Offset(-outset, -outset),
                size = Size(size.width + 2 * outset, size.height + 2 * outset),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = SPOT_STROKE.toPx()),
            )
        }
}
