package com.example.eta.ui.subtasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaCheckbox
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaExpander
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme

/**
 * "Task zur Subtask reduzieren": existing tasks folded into the group as steps.
 *
 * The week's goals are listed outright and the undated backlog sits behind a fold,
 * exactly as the note asks — the week is what one is usually reaching for, and the
 * Sammelliste can be long. Tasks already standing on a day are offered nowhere
 * here: those belong to the planner, and the way to fold one of them in is to drag
 * it onto the group.
 *
 * Ticking nothing happens on the spot. The chosen tasks become rows in the builder
 * and are only retired when the card itself is saved, so backing out of either
 * dialog leaves them where they were. The confirmation names every one of them
 * first, because what is being agreed to is that they stop being tasks.
 */
@Composable
fun ReduceTasksDialog(
    candidates: List<FoldCandidate>,
    onDismiss: () -> Unit,
    onConfirm: (List<FoldCandidate>) -> Unit,
) {
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    var confirming by remember { mutableStateOf(false) }

    val week = candidates.filter { it.inWeek }
    val backlog = candidates.filterNot { it.inWeek }
    val selected = candidates.filter { it.itemId in chosen }

    EtaDialog(title = "Task zur Subtask reduzieren", onDismiss = onDismiss) {
        EtaText(
            text = "Angehakte Tasks werden Schritte dieser Gruppe und verschwinden als eigene " +
                "Aufgaben. Ihre Dauer kommt zur Gruppe hinzu; alles andere, was sie tragen, " +
                "geht verloren.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )

        if (candidates.isEmpty()) {
            EtaText(
                text = "Es gibt nichts, was sich einfalten ließe.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textMuted,
            )
        }

        if (week.isNotEmpty()) {
            EtaText(text = "Diese Woche", style = EtaTheme.typography.bodyStrong)
            week.forEach { candidate ->
                CandidateRow(
                    candidate = candidate,
                    checked = candidate.itemId in chosen,
                    onCheckedChange = { on ->
                        chosen = if (on) chosen + candidate.itemId else chosen - candidate.itemId
                    },
                )
            }
        }

        if (backlog.isNotEmpty()) {
            EtaExpander(
                label = "Sammelliste",
                hint = "Noch keiner Woche zugeordnet.",
                summary = if (backlog.size == 1) "1 Karte" else "${backlog.size} Karten",
            ) {
                backlog.forEach { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        checked = candidate.itemId in chosen,
                        onCheckedChange = { on ->
                            chosen = if (on) chosen + candidate.itemId else chosen - candidate.itemId
                        },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Übernehmen",
                enabled = selected.isNotEmpty(),
                onClick = { confirming = true },
            )
        }
    }

    if (confirming) {
        ConfirmDialog(
            title = "Wirklich einfalten?",
            message = "Diese Tasks werden zu Subtasks von dieser Gruppe und verschwinden aus " +
                "ihren Listen:\n\n" +
                selected.joinToString("\n") { "· ${it.name}" } +
                "\n\nDie Gruppe wird dadurch um " + selected.addedDuration().formatShort() +
                " länger. Geschrieben wird erst, wenn du die Aufgabe selbst sicherst.",
            confirm = "Einfalten",
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                onConfirm(selected)
            },
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: FoldCandidate,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaCheckbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(EtaTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            EtaText(text = candidate.name, style = EtaTheme.typography.body)
            candidate.duration?.let {
                EtaText(
                    text = it.formatShort(),
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }
        }
    }
}
