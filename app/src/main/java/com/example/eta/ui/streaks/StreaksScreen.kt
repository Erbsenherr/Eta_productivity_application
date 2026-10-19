package com.example.eta.ui.streaks

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.model.StreakKind
import com.example.eta.domain.streak.STREAK_BREAK_PENALTY
import com.example.eta.domain.streak.STREAK_MAX_PER_STREAK
import com.example.eta.domain.streak.STREAK_MAX_TOTAL
import com.example.eta.domain.streak.STREAK_POINTS_PER_DAY
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.format.formatFinePoints
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.lists.RecurringCreateDialog
import com.example.eta.ui.lists.recurringSummary
import com.example.eta.ui.theme.EtaTheme

/** The two windows of "Streak beginnen" take turns, like the Belohn-o-mat's. */
private enum class BeginStage { MENU, NEW_TASK }

/**
 * The Streaks tab (step 48): streaks bound to standing tasks, to getting up and
 * going to bed on time, and the Tagesabschluss-Serie, which always runs.
 *
 * A streak is begun here and moved only by the evening. Holding a streak ends
 * it — which breaks it, unless it is still waiting for its first completion.
 */
@Composable
fun StreaksScreen(viewModel: StreaksViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pointsVisible = LocalPointsVisible.current
    var begin by remember { mutableStateOf<BeginStage?>(null) }
    var ending by remember { mutableStateOf<String?>(null) }

    EtaScreen(bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Streaks", style = EtaTheme.typography.title)
                EtaText(
                    text = "Wie oft in Folge du eine wiederkehrende Aufgabe erledigt hast. " +
                        "Ein Tag, an dem sie nicht ansteht, unterbricht nichts — ein Tag, an " +
                        "dem sie ansteht und liegen bleibt, löscht die Flamme. Gezählt wird " +
                        "im Tagesabschluss.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
            ) {
                state.rows.forEach { row ->
                    key(row.streak?.id ?: row.kind.name) {
                        StreakCard(
                            row = row,
                            onLongPress = row.streak
                                ?.takeIf { row.kind != StreakKind.SERIES }
                                ?.let { streak -> { ending = streak.id } },
                        )
                    }
                }
            }

            if (pointsVisible) {
                EtaText(
                    text = "Jeder Streak bringt am Abend ${formatFinePoints(STREAK_POINTS_PER_DAY)} Punkte je " +
                        "Tag seiner Länge — höchstens ${formatPoints(STREAK_MAX_PER_STREAK)} pro " +
                        "Streak und ${formatPoints(STREAK_MAX_TOTAL)} für alle zusammen. Bricht " +
                        "einer ab, kostet das ${formatPoints(STREAK_BREAK_PENALTY)} Punkte.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            EtaButton(
                text = "Streak beginnen",
                onClick = { begin = BeginStage.MENU },
            )

            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }

    when (begin) {
        BeginStage.MENU -> BeginDialog(
            state = state,
            onSleep = {
                viewModel.beginSleep()
                begin = null
            },
            onTask = { group ->
                viewModel.beginTask(group)
                begin = null
            },
            onNewTask = { begin = BeginStage.NEW_TASK },
            onDismiss = { begin = null },
        )

        BeginStage.NEW_TASK -> RecurringCreateDialog(
            definitions = state.definitions,
            onDismiss = { begin = BeginStage.MENU },
            onCreate = { name, note, attributes ->
                viewModel.createTaskWithStreak(name, note, attributes)
                begin = null
            },
        )

        null -> Unit
    }

    ending?.let { id ->
        val row = state.rows.firstOrNull { it.streak?.id == id }
        if (row == null) {
            ending = null
        } else {
            ConfirmDialog(
                title = "Streak beenden?",
                message = if (row.streak?.isWaiting == true) {
                    "»${row.label}« wird entfernt. Er hatte noch nicht begonnen."
                } else {
                    "»${row.label}« endet damit. Das zählt als Abbruch" +
                        if (pointsVisible) {
                            " und kostet ${formatPoints(STREAK_BREAK_PENALTY)} Punkte."
                        } else {
                            "."
                        }
                },
                confirm = "Beenden",
                onDismiss = { ending = null },
                onConfirm = {
                    viewModel.end(id)
                    ending = null
                },
            )
        }
    }
}

/** One streak: its flame, its name and where it stands today. */
@Composable
private fun StreakCard(row: StreakRow, onLongPress: (() -> Unit)?) {
    val press = rememberUpdatedState(onLongPress)
    val waiting = row.kind != StreakKind.SERIES && row.shown == 0

    EtaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(row.streak?.id) {
                detectTapGestures(onLongPress = { press.value?.invoke() })
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakFlame(count = row.shown, lit = row.shown > 0)
            Spacer(Modifier.size(EtaTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                EtaText(
                    text = row.label,
                    style = EtaTheme.typography.bodyStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                EtaText(
                    text = when {
                        row.kind == StreakKind.SERIES ->
                            if (row.doneToday) "Heute abgeschlossen." else "Abende in Folge abgeschlossen."
                        waiting -> "Schließe die Task ab, um den Streak zu starten."
                        row.doneToday -> "Heute erledigt — der Abend zählt es."
                        else -> "Mal in Folge erledigt."
                    },
                    style = EtaTheme.typography.caption,
                    color = if (row.doneToday) EtaTheme.colors.success else EtaTheme.colors.textMuted,
                )
            }
        }
    }
}

/** "Streak beginnen": the sleep streak, the standing schedule, or a new task. */
@Composable
private fun BeginDialog(
    state: StreaksUiState,
    onSleep: () -> Unit,
    onTask: (com.example.eta.domain.recurrence.RecurringGroup) -> Unit,
    onNewTask: () -> Unit,
    onDismiss: () -> Unit,
) {
    EtaDialog(title = "Streak beginnen", onDismiss = onDismiss) {
        EtaText(
            text = "Wähle, woran der Streak hängt. Er beginnt, sobald du die Aufgabe das " +
                "erste Mal abhakst.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        BeginLine(
            id = "sleep",
            title = "Pünktlich schlafen & aufstehen",
            subtitle = if (state.sleepTaken) {
                "Läuft schon."
            } else {
                "Zählt nur, wenn beides pünktlich war — fällt jeden Tag an."
            },
            enabled = !state.sleepTaken,
            onClick = onSleep,
        )
        if (state.groups.isEmpty()) {
            EtaText(
                text = "Noch keine wiederkehrenden Aufgaben.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textMuted,
            )
        }
        state.groups.forEach { group ->
            key(group.representative.id) {
                val taken = group.representative.normalizedName in state.taken
                BeginLine(
                    id = group.representative.id,
                    title = group.representative.name,
                    subtitle = if (taken) "Läuft schon." else recurringSummary(group),
                    enabled = !taken,
                    onClick = { onTask(group) },
                )
            }
        }
        EtaButton(
            text = "Neue wiederkehrende Aufgabe",
            style = EtaButtonStyle.Secondary,
            onClick = onNewTask,
        )
        Row {
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

/** A line of the menu; the whole line is the button. */
@Composable
private fun BeginLine(
    id: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val click = rememberUpdatedState(onClick)
    EtaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(id, enabled) {
                detectTapGestures(onTap = { if (enabled) click.value() })
            },
        contentPadding = EtaTheme.spacing.md,
    ) {
        Column {
            EtaText(
                text = title,
                style = EtaTheme.typography.body,
                color = if (enabled) EtaTheme.colors.textPrimary else EtaTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = subtitle,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
