package com.example.eta.ui.reevaluation

import com.example.eta.domain.sound.SoundEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.example.eta.alarm.EtaSound
import com.example.eta.domain.model.StreakKind
import com.example.eta.domain.streak.StreakOutcome
import com.example.eta.domain.streak.StreakVerdict
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.format.formatFinePoints
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.streaks.StreakFlame
import com.example.eta.ui.theme.EtaTheme
import kotlinx.coroutines.delay

/** How long one streak's turn lasts before the next one's begins. */
private const val STREAK_TURN_MILLIS = 900

/**
 * The streaks' sounds — chosen on the "Audio" page since step 51, their
 * defaults still the placeholders they were, like the Belohn-o-mat's.
 */
private object StreakSounds {
    /** A flame growing by one. */
    val UP = SoundEvent.STREAK_UP

    /** A flame going out. */
    val OUT = SoundEvent.STREAK_OUT
}

/**
 * The evening's streak page (step 48), before the contracts.
 *
 * Each streak has its turn, one after the other and without anything to press:
 * one done today counts up by one, with a sound; one not due today stays as it
 * is; one due and not done goes out — and then, and only then, asks whether to
 * start it again. Unanswered, it is let go. The series never asks: it always
 * runs, and starts again by itself.
 *
 * A preview like every page before "Abschließen": nothing is written here.
 */
@Composable
internal fun StreaksStep(
    outcomes: List<StreakOutcome>,
    restarts: Map<String, Boolean>,
    onRestart: (id: String, restart: Boolean) -> Unit,
    /** The page is the one in front — the turns run only then. */
    active: Boolean,
) {
    val pointsVisible = LocalPointsVisible.current

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            Column {
                EtaText(text = "Streaks", style = EtaTheme.typography.heading)
                EtaText(
                    text = "Was heute anstand und erledigt wurde, zählt eins weiter. Was " +
                        "nicht anstand, bleibt stehen. Was anstand und liegen blieb, erlischt.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            outcomes.forEachIndexed { index, outcome ->
                key(outcome.streak.id) {
                    StreakTurn(
                        outcome = outcome,
                        restart = restarts[outcome.streak.id] ?: false,
                        onRestart = { onRestart(outcome.streak.id, it) },
                        active = active,
                        delayMillis = index * STREAK_TURN_MILLIS,
                        pointsVisible = pointsVisible,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakTurn(
    outcome: StreakOutcome,
    restart: Boolean,
    onRestart: (Boolean) -> Unit,
    active: Boolean,
    delayMillis: Int,
    pointsVisible: Boolean,
) {
    val context = LocalContext.current
    val series = outcome.streak.kind == StreakKind.SERIES
    var shown by remember(outcome.streak.id) { mutableStateOf(outcome.before) }
    var lit by remember(outcome.streak.id) { mutableStateOf(outcome.before > 0) }
    var done by remember(outcome.streak.id) { mutableStateOf(false) }
    val pulse = remember(outcome.streak.id) { Animatable(1f) }

    LaunchedEffect(active, outcome.verdict, outcome.before, outcome.after) {
        shown = outcome.before
        lit = outcome.before > 0
        done = false
        pulse.snapTo(1f)
        if (!active) return@LaunchedEffect
        delay(delayMillis.toLong())
        when (outcome.verdict) {
            StreakVerdict.EXTENDED -> {
                EtaSound.play(context, StreakSounds.UP)
                shown = outcome.after
                lit = true
                pulse.animateTo(1.35f, tween(180))
                pulse.animateTo(1f, tween(260))
            }
            StreakVerdict.BROKEN -> {
                EtaSound.play(context, StreakSounds.OUT)
                lit = false
                shown = 0
                pulse.animateTo(0.8f, tween(180))
                pulse.animateTo(1f, tween(260))
                if (series) {
                    // It always runs: tonight is settled, so it is burning again.
                    delay(STREAK_TURN_MILLIS / 2L)
                    shown = outcome.after
                    lit = outcome.after > 0
                }
            }
            StreakVerdict.NOT_DUE, StreakVerdict.WAITING -> Unit
        }
        done = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakFlame(
                count = shown,
                lit = lit,
                modifier = Modifier.scale(pulse.value),
            )
            Spacer(Modifier.size(EtaTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                EtaText(
                    text = outcome.streak.label,
                    style = EtaTheme.typography.bodyStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                EtaText(
                    text = when (outcome.verdict) {
                        StreakVerdict.EXTENDED -> if (outcome.before == 0) "Begonnen!" else "Eins weiter."
                        StreakVerdict.NOT_DUE -> "Stand heute nicht an — bleibt stehen."
                        StreakVerdict.WAITING -> "Wartet auf die erste Erledigung."
                        StreakVerdict.BROKEN ->
                            if (series) "Ein Abend fehlte — sie beginnt neu." else "Erloschen."
                    },
                    style = EtaTheme.typography.caption,
                    color = when (outcome.verdict) {
                        StreakVerdict.EXTENDED -> EtaTheme.colors.success
                        StreakVerdict.BROKEN -> EtaTheme.colors.danger
                        else -> EtaTheme.colors.textMuted
                    },
                )
            }
            if (pointsVisible && done) {
                val text = when {
                    outcome.penalty > 0.0 -> "-${formatPoints(outcome.penalty)}"
                    outcome.payout > 0.0 -> "+${formatFinePoints(outcome.payout)}"
                    else -> null
                }
                text?.let {
                    EtaText(
                        text = it,
                        style = EtaTheme.typography.bodyStrong,
                        color = if (outcome.penalty > 0.0) EtaTheme.colors.danger else EtaTheme.colors.success,
                    )
                }
            }
        }
        // The question comes once the flame has gone out, not before.
        if (outcome.verdict == StreakVerdict.BROKEN && !series && done) {
            EtaText(
                text = "Gleich neu beginnen? Dann zählt er ab der nächsten Erledigung wieder.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            EtaChoice(
                options = listOf(true to "Neu beginnen", false to "Verfallen lassen"),
                selected = restart,
                onSelect = onRestart,
            )
        }
    }
}
