package com.example.eta.ui.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.ConflictingBlock
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.ui.attributes.AppointmentAttributeFields
import com.example.eta.ui.attributes.AppointmentAttributes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme

/**
 * The calendar step's contents, without a frame of its own.
 *
 * One composable for both hosts — a page of the weekly planning pager, and a
 * screen of its own in the evening — because it is one step asked twice over
 * different stretches. Mounting it twice with two layouts would be two places for
 * the same question to drift.
 */
@Composable
fun CalendarEventsSection(
    viewModel: CalendarEventsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()

    // The account picker and the consent screen are Google's, and only an Activity
    // can put them on screen — so the `PendingIntent` travels up from the sync and
    // is launched here.
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsent(result.data) }

    LaunchedEffect(consent) {
        val pending = consent ?: return@LaunchedEffect
        viewModel.dismissConsent()
        runCatching {
            consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
    ) {
        EtaText(text = "Termine", style = EtaTheme.typography.heading)
        EtaText(
            text = "Was im Google-Kalender steht, bevor der Rest verplant wird — " +
                "so entstehen Terminkonflikte gar nicht erst.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )

        state.error?.let { reason ->
            EtaSurface(
                modifier = Modifier.fillMaxWidth(),
                borderColor = EtaTheme.colors.danger,
                contentPadding = EtaTheme.spacing.md,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaText(
                        text = reason,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.danger,
                    )
                    EtaButton(
                        text = "Nochmal versuchen",
                        style = EtaButtonStyle.Secondary,
                        onClick = viewModel::refresh,
                    )
                }
            }
        }

        when {
            !state.connected && !state.syncing -> EtaText(
                text = "Kein Google-Kalender verbunden. Das geht in den Einstellungen — " +
                    "ohne Verbindung ändert dieser Schritt nichts.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            state.syncing && state.events.isEmpty() -> EtaText(
                text = "Kalender wird gelesen …",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textMuted,
            )

            state.events.isEmpty() -> EtaText(
                text = "Keine neuen Termine. Alles, was der Kalender hergibt, ist " +
                    "entweder übernommen oder bewusst ignoriert.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textMuted,
            )

            else -> state.events.forEach { event ->
                // Keyed, or the open form of one card moves into its neighbour's
                // slot when the card above it is decided — the same trap the
                // concretize cards documented.
                key(event.id) {
                    EventCard(
                        event = event,
                        onIgnore = { viewModel.ignore(event) },
                        onImport = { attributes -> viewModel.import(event, attributes) },
                    )
                }
            }
        }

        if (state.connected) {
            EtaButton(
                text = "Kalender aktualisieren",
                style = EtaButtonStyle.Secondary,
                onClick = viewModel::refresh,
            )
        }
    }

    conflicts?.let { prompt ->
        ConflictDialog(
            prompt = prompt,
            onCancelBlock = viewModel::cancelConflict,
            onCarryIntoWeek = viewModel::carryConflictIntoWeek,
            onShorten = viewModel::shortenConflict,
            onPush = viewModel::pushConflict,
            onKeep = viewModel::keepConflict,
            onDismiss = viewModel::dismissConflicts,
        )
    }
}

/**
 * One undecided event: what the calendar says, and the two things to do about it.
 *
 * "Übernehmen" opens the form rather than importing straight away, because the
 * calendar only supplies three of the answers a card needs — name, time and
 * length. Category and the margins are the user's, and asking for them here is
 * what makes the result an ordinary card rather than a second kind of block.
 */
@Composable
private fun EventCard(
    event: CalendarEvent,
    onIgnore: () -> Unit,
    onImport: (AppointmentAttributes) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var attributes by remember {
        mutableStateOf(
            AppointmentAttributes(start = event.start, duration = event.duration),
        )
    }

    EtaSurface(modifier = Modifier.fillMaxWidth(), contentPadding = EtaTheme.spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = event.title,
                style = EtaTheme.typography.bodyStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = buildString {
                    append(event.date.formatLong())
                    if (event.allDay) {
                        append(" · ganztägig")
                    } else {
                        append(" · ")
                        append(event.start.formatClock())
                        append(" · ")
                        append(event.duration.formatShort())
                    }
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )

            if (open) {
                AppointmentAttributeFields(
                    value = attributes,
                    onChange = { attributes = it },
                    allDay = event.allDay,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Zurück",
                        style = EtaButtonStyle.Secondary,
                        onClick = { open = false },
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(text = "Eintragen", onClick = { onImport(attributes) })
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(
                        text = "Ignorieren",
                        style = EtaButtonStyle.Secondary,
                        onClick = onIgnore,
                    )
                    Spacer(Modifier.weight(1f))
                    EtaButton(text = "Übernehmen", onClick = { open = true })
                }
            }
        }
    }
}

/**
 * The appointment landed on something that was already planned.
 *
 * The appointment itself is not on the table: it comes from a calendar, which
 * means somebody else's expectation as often as not, and the app has no business
 * moving it. What it *can* offer is what to do with the activity now underneath
 * it — and there are four answers, because a collision is usually **partial**.
 *
 * The two whole-activity ones drop it or carry it into the week. The two partial
 * ones keep it: **kürzen** ends it where the appointment begins, **verschieben**
 * starts it again once the appointment is over. Each is shown only where the
 * arithmetic allows it, so a button that appears is a button that works — an
 * appointment that swallows a block whole offers neither, which is the case the
 * user named.
 *
 * Leaving it alone is the fifth answer, and a real one: a walk during a phone
 * meeting is two things at once on purpose.
 */
@Composable
private fun ConflictDialog(
    prompt: ConflictPrompt,
    onCancelBlock: (BlockWithItem) -> Unit,
    onCarryIntoWeek: (BlockWithItem) -> Unit,
    onShorten: (ConflictingBlock) -> Unit,
    onPush: (ConflictingBlock) -> Unit,
    onKeep: (BlockWithItem) -> Unit,
    onDismiss: () -> Unit,
) {
    EtaDialog(title = "Terminkonflikt", onDismiss = onDismiss) {
        EtaText(
            text = "»${prompt.appointment}« liegt auf bereits verplanter Zeit. " +
                "Der Termin bleibt stehen — was soll mit dem Rest geschehen?",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )

        prompt.refusal?.let { reason ->
            EtaText(
                text = reason,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.danger,
            )
        }

        prompt.conflicts.forEach { conflict ->
            key(conflict.entry.block.id) {
                ConflictCard(
                    conflict = conflict,
                    onCancelBlock = { onCancelBlock(conflict.entry) },
                    onCarryIntoWeek = { onCarryIntoWeek(conflict.entry) },
                    onShorten = { onShorten(conflict) },
                    onPush = { onPush(conflict) },
                    onKeep = { onKeep(conflict.entry) },
                )
            }
        }

        Row {
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Fertig", onClick = onDismiss)
        }
        Spacer(Modifier.size(EtaTheme.spacing.xs))
    }
}

/** One collision, with every answer that actually applies to it. */
@Composable
private fun ConflictCard(
    conflict: ConflictingBlock,
    onCancelBlock: () -> Unit,
    onCarryIntoWeek: () -> Unit,
    onShorten: () -> Unit,
    onPush: () -> Unit,
    onKeep: () -> Unit,
) {
    val block = conflict.entry.block

    EtaSurface(modifier = Modifier.fillMaxWidth(), contentPadding = EtaTheme.spacing.md) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = conflict.entry.item.name,
                style = EtaTheme.typography.bodyStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = block.start.formatClock() + " – " +
                    minuteToLocalTime(block.endMinute()).formatClock(),
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )

            // The partial answers first: they keep the activity, and keeping it is
            // what the user most often wants. Each says the time it would produce,
            // so the consequence is read rather than guessed at.
            conflict.shortened?.let { shortened ->
                EtaButton(
                    text = "Kürzen bis " +
                        minuteToLocalTime(shortened.endMinute()).formatClock(),
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onShorten,
                )
            }
            conflict.pushed?.let { pushed ->
                EtaButton(
                    text = "Verschieben auf " + pushed.start.formatClock(),
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPush,
                )
            }
            if (conflict.shortened != null || conflict.pushed != null) {
                EtaText(
                    text = "Die Aktivität selbst ändert sich — Anfahrt, Rückweg und Pause " +
                        "behalten ihre Länge.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = "Fällt aus",
                    style = EtaButtonStyle.Secondary,
                    onClick = onCancelBlock,
                )
                EtaButton(
                    text = "In die Woche",
                    style = EtaButtonStyle.Secondary,
                    onClick = onCarryIntoWeek,
                )
            }
            EtaText(
                text = "»Fällt aus« sagt die Aktivität ab — im Voraus kostet das nichts. " +
                    "»In die Woche« legt sie als Nachholen-Karte in die Wochenliste.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaButton(
                text = "Beides stehen lassen",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onKeep,
            )
        }
    }
}
