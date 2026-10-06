package com.example.eta.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.planning.DEFAULT_POMODORO_PAUSE
import com.example.eta.domain.planning.DEFAULT_POMODORO_WORK
import com.example.eta.domain.planning.MIN_POMODORO_PHASE
import com.example.eta.domain.planning.hasPomodoro
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * What a long press on the "now" box opens.
 *
 * The same press switches the rhythm on and off, as the user asked: a block that
 * already has one is offered "Ausschalten", one without is offered the setup with
 * the suggested 30 and 5 minutes.
 *
 * [running] is the "Gerade" page. It changes one sentence, and it is the one that
 * matters: a rhythm set up now counts from now, while one set up ahead counts from
 * the task's start — and then the task's own start sound opens it, with
 * `pom_work_start` first heard after the first pause.
 */
@Composable
fun PomodoroDialog(
    entry: BlockWithItem,
    running: Boolean,
    onDismiss: () -> Unit,
    onSetUp: (work: Duration, pause: Duration) -> Unit,
    onSwitchOff: () -> Unit,
) {
    val block = entry.block

    if (block.hasPomodoro) {
        ConfirmDialog(
            title = "Pomodoro ausschalten?",
            message = "${entry.item.name} läuft gerade im Rhythmus " +
                "${block.pomodoroWork?.formatShort()} Arbeit / " +
                "${block.pomodoroPause?.formatShort()} Pause. Ausgeschaltet meldet " +
                "sich nur noch das Ende der Aufgabe.",
            confirm = "Ausschalten",
            onDismiss = onDismiss,
            onConfirm = onSwitchOff,
        )
        return
    }

    var work by remember(block.id) { mutableStateOf(DEFAULT_POMODORO_WORK) }
    var pause by remember(block.id) { mutableStateOf(DEFAULT_POMODORO_PAUSE) }

    EtaDialog(title = "Pomodoro einrichten?", onDismiss = onDismiss) {
        EtaText(
            text = if (running) {
                "${entry.item.name} — der Rhythmus beginnt jetzt mit einer Arbeitsphase."
            } else {
                "${entry.item.name} — beginnt um ${block.start.formatClock()} wie gewohnt " +
                    "mit dem Startton. Der Arbeitston kommt erst nach der ersten Pause."
            },
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        // Stepped in fives, typed to the minute: tapping the value opens the entry
        // dialog. Seconds are dropped — the rhythm rings on whole minutes.
        EtaField(label = "Arbeit", hint = "Wert antippen, um ihn frei einzugeben.") {
            EtaDurationPicker(
                value = work,
                onValueChange = { work = it.inWholeMinutes.minutes },
                step = 5.minutes,
                minimum = MIN_POMODORO_PHASE,
                precise = true,
            )
        }
        EtaField(label = "Pause") {
            EtaDurationPicker(
                value = pause,
                onValueChange = { pause = it.inWholeMinutes.minutes },
                step = 5.minutes,
                minimum = MIN_POMODORO_PHASE,
                precise = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Einrichten", onClick = { onSetUp(work, pause) })
        }
    }
}
