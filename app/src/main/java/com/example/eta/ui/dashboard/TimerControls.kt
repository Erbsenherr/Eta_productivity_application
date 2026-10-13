package com.example.eta.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.timerBufferOrZero
import com.example.eta.domain.planning.PomodoroPhaseKind
import com.example.eta.domain.planning.TimerState
import com.example.eta.domain.planning.endsAt
import com.example.eta.domain.planning.formatCountdown
import com.example.eta.domain.planning.fullTimerGoal
import com.example.eta.domain.planning.timerDone
import com.example.eta.domain.planning.timerPhase
import com.example.eta.domain.planning.timerRemaining
import com.example.eta.domain.planning.timerState
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * What a Timer-Task's buttons need: the ticking clock, and the four things they
 * can ask for. One value rather than five parameters on every box between the
 * screen and the row — and the callbacks take a block **id**, which cannot go
 * stale the way a row captured by a gesture can.
 */
class TimerActions(
    val now: Instant,
    val onStart: (blockId: String) -> Unit,
    val onPause: (blockId: String) -> Unit,
    val onResume: (blockId: String) -> Unit,
    val onEnd: (blockId: String) -> Unit,
)

/**
 * The timer of a Timer-Task, where an ordinary task has "Erledigt".
 *
 * "Start" unfolds it. With a pomodoro rhythm the large figure counts down to the
 * next turn and names the phase, and a smaller one says what is left in all;
 * without one the large figure is the whole of it. The button is "Pause" while
 * it runs and "Weiter" while it does not.
 *
 * **"Ende" is behind a long press** until there is a reason for it to be the
 * obvious thing to press: the time is counted down, or the block's frame —
 * buffer included — has run out. Ending early is allowed and deliberate, which
 * is what holding a button down says.
 */
@Composable
fun TimerControls(
    entry: BlockWithItem,
    actions: TimerActions,
    modifier: Modifier = Modifier,
) {
    val block = entry.block
    val id = block.id
    val now = actions.now
    // Asked for by a long press; forgotten when the block changes.
    var endRevealed by remember(id) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        if (block.timerState == TimerState.IDLE) {
            val goal = block.fullTimerGoal(entry.item.timerBufferOrZero)
            EtaButton(
                text = "Start · ${goal.formatShort()}",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { actions.onStart(id) },
            )
            return@Column
        }

        val running = block.timerState == TimerState.RUNNING
        val done = block.timerDone(now)
        val remaining = block.timerRemaining(now)
        val phase = block.timerPhase(now)
        val frameOver = now >= block.endsAt(TimeZone.currentSystemDefault())

        when {
            done -> EtaText(
                text = "Timer-Zeit erreicht",
                style = EtaTheme.typography.title,
                color = EtaTheme.colors.success,
            )

            phase != null -> {
                EtaText(
                    text = (if (phase.kind == PomodoroPhaseKind.PAUSE) "Pause · " else "Arbeit · ") +
                        phase.remaining.formatCountdown(),
                    style = EtaTheme.typography.title,
                    color = if (phase.kind == PomodoroPhaseKind.PAUSE) {
                        EtaTheme.colors.success
                    } else {
                        EtaTheme.colors.accent
                    },
                )
                EtaText(
                    text = "Gesamt noch ${remaining.formatCountdown()}",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            else -> EtaText(
                text = remaining.formatCountdown(),
                style = EtaTheme.typography.title,
                color = EtaTheme.colors.accent,
            )
        }
        if (!running && !done) {
            EtaText(
                text = "Pausiert — die Zeit läuft nicht weiter.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }

        val offerEnd = done || frameOver || endRevealed
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            if (!done) {
                EtaButton(
                    text = if (running) "Pause" else "Weiter",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.weight(1f),
                    onClick = { if (running) actions.onPause(id) else actions.onResume(id) },
                    onLongClick = { endRevealed = true },
                )
            }
            if (offerEnd) {
                EtaButton(
                    text = "Ende",
                    modifier = Modifier.weight(1f),
                    onClick = { actions.onEnd(id) },
                )
            }
        }
        if (!offerEnd) {
            EtaText(
                text = "Gedrückt halten für »Ende«.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        } else if (frameOver && !done) {
            EtaText(
                text = "Der geplante Zeitrahmen ist abgelaufen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }
    }
}

/**
 * A Timer-Task ticked off without its timer.
 *
 * It cannot simply be ticked: what it is about is time put in, and a tick says
 * nothing about how much. So the tick is asked about — and may say how much it
 * was. Without a figure it counts as the whole time.
 */
@Composable
fun TimerTickDialog(
    entry: BlockWithItem,
    onDismiss: () -> Unit,
    /** Null is "the whole time"; otherwise the time that was really put in. */
    onConfirm: (worked: Duration?) -> Unit,
) {
    val goal = entry.block.fullTimerGoal(entry.item.timerBufferOrZero)
    var custom by remember(entry.block.id) { mutableStateOf(false) }
    var worked by remember(entry.block.id) { mutableStateOf(goal) }

    EtaDialog(title = "Wirklich abhaken?", onDismiss = onDismiss) {
        EtaText(
            text = "»${entry.item.name}« ist eine Timer-Task: Sie zählt die Zeit, die du " +
                "investierst — ${goal.formatShort()} sind vorgesehen. Der Timer wurde nie " +
                "gestartet. Soll sie trotzdem als voll gewertet werden?",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        if (custom) {
            EtaField(
                label = "Investierte Zeit",
                hint = if (worked < goal) {
                    "Weniger als vorgesehen: zählt nicht für das nächste Inkrement."
                } else {
                    null
                },
            ) {
                EtaDurationPicker(
                    value = worked,
                    onValueChange = { worked = it.coerceAtMost(goal) },
                    step = 5.minutes,
                    minimum = 1.minutes,
                    precise = true,
                )
            }
        } else {
            EtaButton(
                text = "Eigene Zeit angeben",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { custom = true },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = if (custom) "Abhaken" else "Ja, volle Zeit",
                onClick = { onConfirm(if (custom) worked else null) },
            )
        }
    }
}

/**
 * Started too late: the timer would run past the end of its block.
 *
 * It never runs into time nobody planned, so the block is lengthened — pushing
 * back what stands behind it, where something does — or the timer stops when
 * the block does. Each answer says the hour it would mean, and is offered only
 * where the arithmetic allows it.
 */
@Composable
fun TimerOverflowDialog(
    prompt: TimerOverflowPrompt,
    onShorten: () -> Unit,
    onExtend: () -> Unit,
    onDismiss: () -> Unit,
) {
    EtaDialog(title = "Spät dran", onDismiss = onDismiss) {
        EtaText(
            text = "Mit der vollen Zeit würde »${prompt.name}« bis " +
                "${prompt.projectedEnd.formatClock()} laufen — der Block endet aber um " +
                "${prompt.frameEnd.formatClock()}." +
                (prompt.nextName?.let { " Dahinter steht »$it«." } ?: " Dahinter ist frei."),
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = "Task verlängern",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                enabled = prompt.canExtend,
                onClick = onExtend,
            )
            EtaText(
                text = when {
                    !prompt.canExtend && prompt.nextName != null ->
                        "Geht nicht: Von »${prompt.nextName}« bliebe nichts übrig, oder es " +
                            "ist ein Kalendertermin."

                    !prompt.canExtend -> "Geht nicht: Der Tag ist vorher zu Ende."
                    prompt.nextName != null && prompt.pushedStart != null ->
                        "Der Block läuft dann bis ${prompt.projectedEnd.formatClock()}. " +
                            "»${prompt.nextName}« beginnt erst um " +
                            "${prompt.pushedStart.formatClock()} und endet wie geplant."

                    else -> "Der Block läuft dann bis ${prompt.projectedEnd.formatClock()}, " +
                        "mit der vollen Timer-Zeit."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = "Am Blockende aufhören",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                enabled = prompt.shortenedGoal != null,
                onClick = onShorten,
            )
            EtaText(
                text = prompt.shortenedGoal?.let {
                    "Der Timer läuft dann ${it.formatShort()} statt ${prompt.goal.formatShort()} " +
                        "und stoppt um ${prompt.frameEnd.formatClock()}. Zählt nicht als voll " +
                        "absolviert."
                } ?: "Der Block ist bereits vorbei.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

/** What ending a timer short of its time means — said once, after the fact. */
@Composable
fun TimerResultDialog(result: TimerResult, onDismiss: () -> Unit) {
    EtaDialog(title = "Vorzeitig beendet", onDismiss = onDismiss) {
        EtaText(
            text = buildString {
                append("»${result.name}«: ${result.worked.formatShort()} von ")
                append("${result.goal.formatShort()} gezählt.")
                if (LocalPointsVisible.current) append(" Abgerechnet wird die gezählte Zeit.")
                if (result.missesIncrement) {
                    append(" Für das nächste Inkrement zählt dieser Termin nicht.")
                }
            },
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        if (result.frameOver) {
            EtaText(
                text = "Der Zeitrahmen war abgelaufen, bevor die Timer-Zeit voll war. Wenn das " +
                    "öfter passiert, hilft ein größerer Puffer — zu ändern in der Aufgabe " +
                    "unter Extras → Timer-Task.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.warning,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            Spacer(Modifier.weight(1f))
            EtaButton(text = "OK", onClick = onDismiss)
        }
    }
}
