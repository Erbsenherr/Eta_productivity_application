package com.example.eta.ui.reminders

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.model.Reminder
import com.example.eta.domain.reminder.defaultReminderDate
import com.example.eta.ui.attributes.CheckRow
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaStepper
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The Erinnerungen tab.
 *
 * On top, a reminder is set: what to say and when. The time is all that has to be
 * given — it lands on today, or tomorrow once today's hour has passed — and the
 * checkbox opens a date for anything further out. Below, what is still to come,
 * opened with a tap like every other list in the app, with Bearbeiten and Löschen.
 */
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel,
    modifier: Modifier = Modifier,
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<Reminder?>(null) }
    var editing by remember { mutableStateOf<Reminder?>(null) }

    EtaScreen(modifier = modifier, bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Erinnerungen", style = EtaTheme.typography.display)
                EtaText(
                    text = "Ein Ton und eine Nachricht, zur Uhrzeit deiner Wahl.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            EtaSurface(modifier = Modifier.fillMaxWidth()) {
                // A fresh form after every entry: keyed by a counter, so the text
                // clears and the time starts over without resetting by hand.
                var formKey by remember { mutableIntStateOf(0) }
                key(formKey) {
                    ReminderForm(
                        initialText = "",
                        initialTime = null,
                        initialDate = null,
                        now = viewModel.now(),
                        confirm = "Erinnerung stellen",
                        onConfirm = { text, date, time ->
                            viewModel.add(text, date, time)
                            formKey++
                        },
                    )
                }
            }

            EtaSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EtaText(
                            text = "Anstehend",
                            style = EtaTheme.typography.heading,
                            modifier = Modifier.weight(1f),
                        )
                        EtaText(
                            text = pending.size.toString(),
                            style = EtaTheme.typography.title,
                            color = if (pending.isEmpty()) {
                                EtaTheme.colors.textMuted
                            } else {
                                EtaTheme.colors.accent
                            },
                        )
                    }
                    if (pending.isEmpty()) {
                        EtaText(
                            text = "Nichts gestellt.",
                            style = EtaTheme.typography.body,
                            color = EtaTheme.colors.textMuted,
                        )
                    }
                    pending.forEach { reminder ->
                        ReminderLine(
                            reminder = reminder,
                            at = reminder.at.toLocalDateTime(viewModel.timeZone),
                            today = viewModel.now().date,
                            onOpen = { opened = reminder },
                        )
                    }
                }
            }

            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }

    opened?.let { reminder ->
        ReminderDetailDialog(
            reminder = reminder,
            at = reminder.at.toLocalDateTime(viewModel.timeZone),
            onDismiss = { opened = null },
            onEdit = {
                editing = reminder
                opened = null
            },
            onDelete = {
                viewModel.delete(reminder.id)
                opened = null
            },
        )
    }

    editing?.let { reminder ->
        val at = reminder.at.toLocalDateTime(viewModel.timeZone)
        EtaDialog(title = "Erinnerung bearbeiten", onDismiss = { editing = null }) {
            ReminderForm(
                initialText = reminder.text,
                initialTime = at.time,
                initialDate = at.date,
                now = viewModel.now(),
                confirm = "Sichern",
                onCancel = { editing = null },
                onConfirm = { text, date, time ->
                    viewModel.update(reminder.id, text, date, time)
                    editing = null
                },
            )
        }
    }
}

/**
 * What a reminder is asked: the text, the time, and — only when ticked — a date.
 *
 * Without the tick the date follows the time: today while the hour is still ahead,
 * tomorrow once it has passed. An existing reminder on another day opens with the
 * box ticked, so editing its text does not quietly move it to today.
 */
@Composable
private fun ReminderForm(
    initialText: String,
    initialTime: LocalTime?,
    initialDate: LocalDate?,
    now: LocalDateTime,
    confirm: String,
    onConfirm: (text: String, date: LocalDate, time: LocalTime) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initialText) }
    var time by remember {
        mutableStateOf(initialTime ?: LocalTime((now.hour + 1).coerceAtMost(23), 0))
    }
    var ownDate by remember {
        mutableStateOf(initialDate != null && initialDate != defaultReminderDate(initialTime!!, now))
    }
    var inDays by remember {
        mutableIntStateOf(initialDate?.let { now.date.daysUntil(it).coerceAtLeast(0) } ?: 1)
    }

    val date = if (ownDate) now.date.plus(DatePeriod(days = inDays)) else defaultReminderDate(time, now)
    val inThePast = LocalDateTime(date, time) <= now

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
        EtaField(label = "Woran erinnern?") {
            EtaTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "z. B. Paket abholen",
                singleLine = false,
            )
        }
        EtaField(
            label = "Uhrzeit",
            hint = if (ownDate) null else "Am ${date.formatLong()}.",
        ) {
            EtaTimePicker(value = time, onValueChange = { time = it })
        }
        CheckRow(
            checked = ownDate,
            onCheckedChange = { ownDate = it },
            label = "Anderes Datum",
            hint = "Sonst heute — oder morgen, wenn die Uhrzeit heute schon vorbei ist.",
        )
        if (ownDate) {
            EtaField(label = "Datum", hint = date.formatLong()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaStepper(
                        value = when (inDays) {
                            0 -> "heute"
                            1 -> "morgen"
                            else -> "in $inDays Tagen"
                        },
                        valueWidth = 104.dp,
                        onDecrement = { inDays = (inDays - 1).coerceAtLeast(0) },
                        onIncrement = { inDays += 1 },
                    )
                    // A week at a time, since a reminder a month out would
                    // otherwise be thirty taps away.
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaButton(
                            text = "− 1 Woche",
                            style = EtaButtonStyle.Secondary,
                            onClick = { inDays = (inDays - 7).coerceAtLeast(0) },
                        )
                        EtaButton(
                            text = "+ 1 Woche",
                            style = EtaButtonStyle.Secondary,
                            onClick = { inDays += 7 },
                        )
                    }
                }
            }
        }
        if (inThePast) {
            EtaText(
                text = "Dieser Zeitpunkt ist schon vorbei.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            if (onCancel != null) {
                EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onCancel)
            }
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = confirm,
                enabled = text.isNotBlank() && !inThePast,
                onClick = { onConfirm(text, date, time) },
            )
        }
    }
}

@Composable
private fun ReminderLine(
    reminder: Reminder,
    at: LocalDateTime,
    today: LocalDate,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().pointerInput(reminder.id) {
            detectTapGestures(onTap = { onOpen() }, onLongPress = { onOpen() })
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            EtaText(
                text = reminder.text,
                style = EtaTheme.typography.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (reminder.isAutomatic) {
                // Said here rather than only in the dialog: a list in which some
                // rows cannot be edited has to show which ones before they are
                // tapped.
                EtaText(
                    text = "Extra einer Aufgabe",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.accent,
                )
            }
        }
        Spacer(Modifier.size(EtaTheme.spacing.sm))
        EtaText(
            text = when (today.daysUntil(at.date)) {
                0 -> "heute · ${at.time.formatClock()}"
                1 -> "morgen · ${at.time.formatClock()}"
                else -> "${at.date.formatLong()} · ${at.time.formatClock()}"
            },
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}

/** A tapped reminder: all of its text, and the two things to do with it. */
@Composable
private fun ReminderDetailDialog(
    reminder: Reminder,
    at: LocalDateTime,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember(reminder.id) { mutableStateOf(false) }

    EtaDialog(title = "Erinnerung", onDismiss = onDismiss) {
        EtaText(
            text = "${at.date.formatLong()} · ${at.time.formatClock()}",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )
        EtaText(text = reminder.text, style = EtaTheme.typography.body)

        // A reminder derived from a task is not edited here, and saying so is the
        // honest answer: the app keeps it aimed at the task next occurrence, so
        // anything changed here would be overwritten on the next sync. The switch
        // that owns it is the one that created it.
        if (reminder.isAutomatic) {
            EtaText(
                text = "Kommt aus dem Extra ‚Erinnerung‘ einer Aufgabe und zeigt " +
                    "immer auf deren nächsten Termin. Vorlauf, Botschaft und das " +
                    "Abschalten stehen bei der Aufgabe selbst — unter Extras.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            return@EtaDialog
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = if (confirmingDelete) "Wirklich?" else "Löschen",
                style = EtaButtonStyle.Secondary,
                onClick = { if (confirmingDelete) onDelete() else confirmingDelete = true },
            )
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Bearbeiten", onClick = onEdit)
        }
    }
}
