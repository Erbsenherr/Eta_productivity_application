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
import com.example.eta.domain.growth.MALUS_RECOVERY
import com.example.eta.domain.growth.hasQuantity
import com.example.eta.domain.growth.isGrowthTask
import com.example.eta.domain.growth.timerBufferOrZero
import com.example.eta.domain.growth.timerDuration
import com.example.eta.domain.growth.withEarlyEndMalus
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
    /**
     * "Vorzeitig beenden" (step 55): the screen asks first ([EarlyEndDialog]).
     * Null keeps the older shape — "Ende" behind a long press — which a
     * routine's step still has: a step carries no malus.
     */
    val onEndEarly: ((blockId: String) -> Unit)? = null,
)

/**
 * The four buttons of a routine step's timer (step 49), by block and step id —
 * ids for the reason [TimerActions] takes one.
 */
class StepTimerActions(
    val onStart: (blockId: String, subtaskId: String) -> Unit,
    val onPause: (blockId: String, subtaskId: String) -> Unit,
    val onResume: (blockId: String, subtaskId: String) -> Unit,
    val onEnd: (blockId: String, subtaskId: String) -> Unit,
)

/**
 * The timer of a Timer-Task, where an ordinary task has "Erledigt".
 *
 * "Start" unfolds it. With a pomodoro rhythm the large figure counts down to the
 * next turn and names the phase, and a smaller one says what is left in all;
 * without one the large figure is the whole of it. The button is "Pause" while
 * it runs and "Weiter" while it does not.
 *
 * Before the time is counted down the second button is **"Vorzeitig beenden"**
 * (step 55), which the screen asks about — it costs the task 10 % of its time.
 * Once the time is up it is "Ende". Without [TimerActions.onEndEarly] — a
 * routine's step — "Ende" is behind a long press as it always was.
 */
@Composable
fun TimerControls(
    entry: BlockWithItem,
    actions: TimerActions,
    modifier: Modifier = Modifier,
    /** What "Start" counts down from, where it is not the block's own time — a step's (step 49). */
    idleGoal: Duration? = null,
) {
    val block = entry.block
    val id = block.id
    val now = actions.now
    // Asked for by a long press; forgotten when the block changes.
    var endRevealed by remember(id) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        if (block.timerState == TimerState.IDLE) {
            val goal = idleGoal ?: block.fullTimerGoal(entry.item.timerBufferOrZero)
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

        val endEarly = actions.onEndEarly
        if (endEarly != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                if (!done) {
                    EtaButton(
                        text = if (running) "Pause" else "Weiter",
                        style = EtaButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { if (running) actions.onPause(id) else actions.onResume(id) },
                    )
                    EtaButton(
                        text = "Vorzeitig beenden",
                        style = EtaButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { endEarly(id) },
                    )
                } else {
                    EtaButton(
                        text = "Ende",
                        modifier = Modifier.weight(1f),
                        onClick = { actions.onEnd(id) },
                    )
                }
            }
            if (frameOver && !done) {
                EtaText(
                    text = "Der geplante Zeitrahmen ist abgelaufen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
                )
            }
            return@Column
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
 * "Vorzeitig beenden" asked about (step 55): what it costs, said before.
 *
 * The figures are the task's — 10 % of the time as it stands, and 2 % of the
 * time before the first malus back with every full sitting — so the user sees
 * the minutes, not the rule.
 */
@Composable
fun EarlyEndDialog(
    entry: BlockWithItem,
    /** What the timer has counted so far. */
    counted: Duration,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val goal = entry.block.fullTimerGoal(entry.item.timerBufferOrZero)
    EtaDialog(title = "Vorzeitig beenden?", onDismiss = onDismiss) {
        EtaText(
            text = "»${entry.item.name}«: ${counted.formatShort()} von ${goal.formatShort()} gezählt.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        MalusNote(entry)
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Weitermachen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Vorzeitig beenden", onClick = onConfirm)
        }
    }
}

/** What an early end does to the task, in its own minutes. */
@Composable
private fun MalusNote(entry: BlockWithItem) {
    val item = entry.item
    val current = item.timerDuration ?: return
    val reduced = item.withEarlyEndMalus().timerDuration ?: return
    val base = item.timerMalusBase ?: current
    val growthNote = if (item.isGrowthTask) " Bis dahin wächst die Zielzeit nicht." else ""
    EtaText(
        text = "Beim Abendabschluss sinkt die Zeit der Task um 10 %: von " +
            "${current.formatShort()} auf ${reduced.formatShort()}. Jede danach vollständig " +
            "absolvierte Sitzung holt ${(base * MALUS_RECOVERY).formatShort()} zurück, bis wieder " +
            "${base.formatShort()} erreicht sind." + growthNote,
        style = EtaTheme.typography.caption,
        color = EtaTheme.colors.warning,
    )
}

/**
 * A Timer-Task ticked off without its timer having counted it all.
 *
 * Three answers (step 55): the whole time was put in — it counts in full,
 * towards the next increment as well, which is how a workout tracked
 * elsewhere still grows — or it was ended early, with the time that was put
 * in and the malus that brings, or nothing.
 */
@Composable
fun TimerTickDialog(
    entry: BlockWithItem,
    onDismiss: () -> Unit,
    /** [worked] is null for the whole time; [early] is "Vorzeitig beendet". */
    onConfirm: (worked: Duration?, early: Boolean) -> Unit,
    /** What the timer counted, where it was started and never ended. */
    counted: Duration? = null,
) {
    val goal = entry.block.fullTimerGoal(entry.item.timerBufferOrZero)
    var early by remember(entry.block.id) { mutableStateOf(false) }
    var worked by remember(entry.block.id) {
        mutableStateOf((counted ?: goal).coerceIn(1.minutes, maxOf(goal, 1.minutes)))
    }
    val grows = entry.item.isGrowthTask || entry.item.hasQuantity
    val timerLine = if (counted != null) {
        "Der Timer hat ${counted.formatShort()} gezählt und wurde nicht beendet."
    } else {
        "Der Timer wurde nie gestartet."
    }

    EtaDialog(title = "Timer-Task abhaken", onDismiss = onDismiss) {
        EtaText(
            text = "»${entry.item.name}« ist eine Timer-Task — ${goal.formatShort()} sind " +
                "vorgesehen. " + timerLine,
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        if (!early) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = "Volle Zeit gemacht",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onConfirm(null, false) },
                )
                EtaText(
                    text = if (grows) {
                        "Zählt als vollständig absolviert — auch für das nächste Inkrement."
                    } else {
                        "Zählt als vollständig absolviert."
                    },
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
                EtaButton(
                    text = "Vorzeitig beendet",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { early = true },
                )
            }
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        } else {
            EtaField(label = "Investierte Zeit") {
                EtaDurationPicker(
                    value = worked,
                    onValueChange = { worked = it.coerceAtMost(goal) },
                    step = 5.minutes,
                    minimum = 1.minutes,
                    precise = true,
                )
            }
            MalusNote(entry)
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(text = "Zurück", style = EtaButtonStyle.Secondary, onClick = { early = false })
                Spacer(Modifier.weight(1f))
                EtaButton(text = "Abhaken", onClick = { onConfirm(worked, true) })
            }
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
