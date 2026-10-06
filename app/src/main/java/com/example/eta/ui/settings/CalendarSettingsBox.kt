package com.example.eta.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.model.CalendarSource
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaCheckbox
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.theme.EtaTheme

/**
 * Google Kalender, in the settings tab where the standing configuration lives.
 *
 * Three things, and they are three because the user named three: connecting and
 * disconnecting, refreshing by hand, and choosing **which** calendars count. The
 * last one is why this cannot be a single on/off switch — one account already
 * holds the personal calendar, a work one, the public holidays and whatever
 * anyone has shared in, and only some of those describe the user's own day.
 *
 * The list is folded away behind its own summary line: with five or six calendars
 * it would otherwise push the rest of the tab off the screen, which is the same
 * complaint "Offene Tage" answered in step 12.
 */
@Composable
fun CalendarSettingsBox(viewModel: CalendarSettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    var calendarsOpen by remember { mutableStateOf(false) }
    var ignoredOpen by remember { mutableStateOf(false) }
    var confirmingDisconnect by remember { mutableStateOf(false) }

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

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Google Kalender", style = EtaTheme.typography.heading)
            EtaText(
                text = "Nur lesend. Termine werden in den Planungsphasen angeboten — " +
                    "übernehmen oder ignorieren; geschrieben wird in Google nie.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            state.error?.let {
                EtaText(
                    text = it,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.danger,
                )
            }
            state.status?.let {
                EtaText(
                    text = it,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.success,
                )
            }
            if (state.busy) {
                EtaText(
                    text = "Spreche mit Google …",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                if (state.connected) {
                    EtaButton(
                        text = "Aktualisieren",
                        style = EtaButtonStyle.Secondary,
                        onClick = viewModel::refresh,
                    )
                    EtaButton(
                        text = "Trennen",
                        style = EtaButtonStyle.Secondary,
                        onClick = { confirmingDisconnect = true },
                    )
                } else {
                    EtaButton(text = "Mit Google verbinden", onClick = viewModel::connect)
                }
            }

            if (state.connected) {
                Disclosure(
                    label = "Kalender",
                    summary = "${state.sources.count { it.enabled }} von " +
                        "${state.sources.size} aktiv",
                    open = calendarsOpen,
                    onToggle = { calendarsOpen = !calendarsOpen },
                )
                if (calendarsOpen) {
                    state.sources.forEach { source ->
                        key(source.id) {
                            CalendarRow(
                                source = source,
                                onToggle = { viewModel.setEnabled(source, it) },
                            )
                        }
                    }
                    EtaText(
                        text = "Eine Verbindung deckt ein Google-Konto ab — mit allen " +
                            "Unterkalendern und allem, was dort hineingeteilt wurde.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            }

            if (state.ignored.isNotEmpty()) {
                Disclosure(
                    label = "Ignorierte Events",
                    summary = "${state.ignored.size}",
                    open = ignoredOpen,
                    onToggle = { ignoredOpen = !ignoredOpen },
                )
                if (ignoredOpen) {
                    EtaText(
                        text = "Bewusst nicht übernommen. Zurückholen macht sie wieder zu " +
                            "offenen Terminen, die in der nächsten Planung erscheinen.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                    state.ignored.forEach { event ->
                        key(event.id) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    EtaText(
                                        text = event.title,
                                        style = EtaTheme.typography.body,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    EtaText(
                                        text = event.date.formatLong() +
                                            if (event.allDay) {
                                                " · ganztägig"
                                            } else {
                                                " · " + event.start.formatClock()
                                            },
                                        style = EtaTheme.typography.caption,
                                        color = EtaTheme.colors.textMuted,
                                    )
                                }
                                EtaButton(
                                    text = "Zurückholen",
                                    style = EtaButtonStyle.Secondary,
                                    onClick = { viewModel.unignore(event) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmingDisconnect) {
        ConfirmDialog(
            title = "Verbindung trennen",
            message = "Die gelesenen Kalender und Termine werden gelöscht. Bereits " +
                "übernommene Termine bleiben als Karten bestehen. Die Freigabe selbst " +
                "widerrufst du in deinem Google-Konto unter »Drittanbieter-Apps«.",
            confirm = "Trennen",
            onDismiss = { confirmingDisconnect = false },
            onConfirm = {
                viewModel.disconnect()
                confirmingDisconnect = false
            },
        )
    }
}

@Composable
private fun CalendarRow(source: CalendarSource, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaCheckbox(checked = source.enabled, onCheckedChange = onToggle)
        Spacer(Modifier.size(EtaTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            EtaText(
                text = source.displayName,
                style = EtaTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val note = listOfNotNull(
                source.accountName.takeIf { source.isPrimary },
                "fremder Kalender".takeIf { source.isForeign },
            ).joinToString(" · ")
            if (note.isNotBlank()) {
                EtaText(
                    text = note,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
        }
    }
}

/** A heading that folds what is under it away. */
@Composable
private fun Disclosure(
    label: String,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(vertical = EtaTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaText(
            text = if (open) "▾ $label" else "▸ $label",
            style = EtaTheme.typography.label,
            modifier = Modifier.weight(1f),
        )
        EtaText(
            text = summary,
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}
