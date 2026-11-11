package com.example.eta.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.StopwatchTime
import com.example.eta.domain.planning.formatCountdown
import com.example.eta.domain.stats.elapsed
import com.example.eta.domain.stats.isRunning
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * What the stopwatch of the "Gerade" box needs (step 56): the ticking clock,
 * the ones being taken, and their buttons — by id, for the reason
 * [TimerActions] takes one.
 */
class StopwatchActions(
    val now: Instant,
    val running: List<StopwatchTime>,
    /** "⏱" on the task in the box: the screen explains (once) and asks from when. */
    val onRequest: (BlockWithItem) -> Unit,
    val onPause: (id: String) -> Unit,
    val onResume: (id: String) -> Unit,
    val onEnd: (id: String) -> Unit,
)

/**
 * Every stopwatch still being taken, under the box's pages — whatever task is
 * running now, so one started on a task that has since ended is not lost from
 * sight while it still counts.
 */
@Composable
fun StopwatchLines(actions: StopwatchActions) {
    if (actions.running.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        actions.running.forEach { time ->
            key(time.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        EtaText(
                            text = "⏱ " + time.elapsed(actions.now).formatCountdown(),
                            style = EtaTheme.typography.label,
                            color = if (time.isRunning) EtaTheme.colors.accent else EtaTheme.colors.warning,
                        )
                        EtaText(
                            text = time.name + if (time.isRunning) "" else " · pausiert",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                    }
                    EtaButton(
                        text = if (time.isRunning) "Pause" else "Weiter",
                        style = EtaButtonStyle.Secondary,
                        onClick = {
                            if (time.isRunning) actions.onPause(time.id) else actions.onResume(time.id)
                        },
                    )
                    Spacer(Modifier.width(EtaTheme.spacing.xs))
                    EtaButton(text = "Beenden", onClick = { actions.onEnd(time.id) })
                }
            }
        }
    }
}

/** The first "⏱": what the stopwatch is for. Shown once. */
@Composable
fun StopwatchExplainDialog(onDismiss: () -> Unit, onUnderstood: () -> Unit) {
    EtaDialog(title = "Stoppuhr", onDismiss = onDismiss) {
        EtaText(
            text = "Mit der Stoppuhr misst du, wie lange eine Aufgabe wirklich dauert. " +
                "Du kannst sie ab jetzt laufen lassen oder ab dem geplanten Beginn, " +
                "sie pausieren und beenden. Beim Beenden wird die Zeit gespeichert.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaText(
            text = "Alle gestoppten Zeiten findest du unter Statistiken → »Gestoppte Zeiten«, " +
                "mit Durchschnitt, kürzester und längster Zeit. Punkte und Plan bleiben " +
                "davon unberührt.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        Row {
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Verstanden", onClick = onUnderstood)
        }
    }
}

/** From when it counts: now, or the task's start by the plan. */
@Composable
fun StopwatchStartDialog(
    entry: BlockWithItem,
    now: Instant,
    onDismiss: () -> Unit,
    onStart: (fromPlan: Boolean) -> Unit,
) {
    val planStart = LocalDateTime(entry.block.date, entry.block.start)
        .toInstant(TimeZone.currentSystemDefault())
    val planPassed = planStart < now
    EtaDialog(title = "Stoppuhr starten", onDismiss = onDismiss) {
        EtaText(
            text = "»${entry.item.name}« — ab wann soll gemessen werden?",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = "Ab jetzt",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onStart(false) },
            )
            EtaButton(
                text = "Ab Beginn laut Plan · ${entry.block.start.formatClock()}",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                enabled = planPassed,
                onClick = { onStart(true) },
            )
            if (!planPassed) {
                EtaText(
                    text = "Die Aufgabe beginnt laut Plan erst noch.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
        }
        EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
    }
}
