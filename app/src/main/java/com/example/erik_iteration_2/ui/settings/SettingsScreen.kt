package com.example.erik_iteration_2.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.data.backup.BACKUP_FILE_NAME
import com.example.erik_iteration_2.domain.setup.UserSetup
import com.example.erik_iteration_2.domain.setup.WEEK
import com.example.erik_iteration_2.domain.streak.CATCH_UP_PHRASE
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikWeekdayPicker
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.setup.FreeTimeStep
import com.example.erik_iteration_2.ui.setup.HousekeepingStep
import com.example.erik_iteration_2.ui.setup.MealsStep
import com.example.erik_iteration_2.ui.setup.MindfulnessStep
import com.example.erik_iteration_2.ui.setup.OnSetupChange
import com.example.erik_iteration_2.ui.setup.PlanningStep
import com.example.erik_iteration_2.ui.setup.SleepStep
import com.example.erik_iteration_2.ui.setup.SportStep
import com.example.erik_iteration_2.ui.setup.WorkStep
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlinx.datetime.LocalDate

/**
 * The Einstellungen tab: the standing configuration, away from planning a day.
 *
 * The questionnaire's own step composables are reused rather than reimplemented —
 * they are already `(draft, onChange)` pairs, so there is exactly one place where
 * each answer is asked for, and it cannot drift between the two screens.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onDebugReset: (() -> Unit)? = null,
    onOpenVacation: () -> Unit = {},
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val openDays by viewModel.openDays.collectAsStateWithLifecycle()
    val phrase by viewModel.phrase.collectAsStateWithLifecycle()
    val phraseAccepted by viewModel.phraseAccepted.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    ErikScreen(modifier = modifier, bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ErikTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
        ) {
            Column {
                ErikText(text = "Einstellungen", style = ErikTheme.typography.display)
                ErikText(
                    text = "Was dauerhaft gilt.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
            }

            message?.let { MessageBox(it, viewModel::dismissMessage) }

            DataBox(
                onExport = { exportLauncher.launch(BACKUP_FILE_NAME) },
                onImport = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
            )

            ErikSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
                    ErikText(text = "Urlaubsmodus", style = ErikTheme.typography.heading)
                    ErikText(
                        text = "Für einen Zeitraum wiederkehrende Aufgaben aussetzen oder " +
                            "verschieben. Danach gilt wieder der gewohnte Plan.",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textMuted,
                    )
                    ErikButton(
                        text = "Urlaub planen",
                        style = ErikButtonStyle.Secondary,
                        onClick = onOpenVacation,
                    )
                }
            }

            CatchUpBox(
                openDays = openDays,
                phrase = phrase,
                unlocked = phraseAccepted,
                onPhraseChange = viewModel::setPhrase,
                onCatchUp = viewModel::catchUp,
            )

            val setup = draft
            if (setup == null) {
                ErikSurface(modifier = Modifier.fillMaxWidth()) {
                    ErikText(
                        text = "Einrichtung wird geladen …",
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textMuted,
                    )
                }
            } else {
                ErikText(text = "Deine Einrichtung", style = ErikTheme.typography.title)
                ErikText(
                    text = "Änderungen legen die wiederkehrenden Aufgaben neu an. Was nicht " +
                        "mehr aus deinen Antworten folgt, wird ausrangiert — Vergangenes " +
                        "bleibt in der Erfolgsliste.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )

                SleepStep(setup, viewModel::update)
                MealsStep(setup, viewModel::update)
                HousekeepingStep(setup, viewModel::update)
                SportStep(setup, viewModel::update)
                FreeTimeStep(setup, viewModel::update)
                MindfulnessStep(setup, viewModel::update)
                WorkStep(setup, viewModel::update)
                PlanningStep(setup, viewModel::update)
                InflationBox(setup, viewModel::update)

                ErikButton(text = "Einrichtung sichern", onClick = viewModel::save)
            }

            if (onDebugReset != null) {
                DebugBox(onReset = onDebugReset)
            }

            Spacer(Modifier.size(ErikTheme.spacing.xl))
        }
    }
}

/**
 * When the savings lose their 30 %.
 *
 * It hangs off a weekday rather than off visiting the weekly planning, so it
 * cannot be dodged by staying away — which also means the day is worth choosing
 * separately: someone who plans on Sunday evening may want the devaluation to
 * land Monday morning, on the fresh week rather than on the one just closed.
 */
@Composable
private fun InflationBox(setup: UserSetup, onChange: OnSetupChange) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = "Wertverfall", style = ErikTheme.typography.heading)
            ErikText(
                text = "Einmal pro Woche verlieren gesparte Punkte 30 %. Der Abzug wird beim " +
                    "ersten Öffnen nach diesem Tag gebucht — auch ohne Wochenplanung.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
            ErikChoice(
                options = listOf(
                    true to "Am Tag der Wochenplanung",
                    false to "An einem eigenen Tag",
                ),
                selected = setup.inflationDay == null,
                onSelect = { follows ->
                    onChange { it.copy(inflationDay = if (follows) null else it.inflationWeekday) }
                },
            )
            if (setup.inflationDay != null) {
                ErikField(label = "Fällig am") {
                    ErikWeekdayPicker(
                        selected = setup.inflationWeekday,
                        onSelect = { day -> onChange { it.copy(inflationDay = day) } },
                        days = WEEK,
                    )
                }
            }
        }
    }
}

/**
 * Where the data lives.
 *
 * The live database cannot sit in a folder of the user's choosing — scoped storage
 * hands out document URIs, and SQLite needs a path it can lock — so what the user
 * picks is where copies go. Saying that plainly is better than a setting that
 * looks like it moves the database and does not.
 */
@Composable
private fun DataBox(onExport: () -> Unit, onImport: () -> Unit) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = "Deine Daten", style = ErikTheme.typography.heading)
            ErikText(
                text = "ERIK speichert alles auf diesem Gerät, nichts geht nach außen. " +
                    "Wohin gesichert wird, bestimmst du — die Datei enthält den " +
                    "kompletten Bestand.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikButton(text = "Sichern", onClick = onExport)
                ErikButton(
                    text = "Wiederherstellen",
                    style = ErikButtonStyle.Secondary,
                    onClick = onImport,
                )
            }
        }
    }
}

/**
 * Making up a day the evening never settled.
 *
 * A skipped day costs the streak and its points, and that consequence *is* the
 * mechanic — so this is not a convenience. It exists for the one case the user
 * named: the app or the phone got in the way. Typing the phrase out in full is
 * friction on purpose; a checkbox would be clicked past without thinking.
 */
@Composable
private fun CatchUpBox(
    openDays: List<LocalDate>,
    phrase: String,
    unlocked: Boolean,
    onPhraseChange: (String) -> Unit,
    onCatchUp: (LocalDate) -> Unit,
) {
    if (openDays.isEmpty()) return

    ErikSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = ErikTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = "Offene Tage", style = ErikTheme.typography.heading)
            ErikText(
                text = "${openDays.size} Tage wurden nie abgerechnet. Das kostet die Serie " +
                    "und die Punkte dieser Tage — so ist es gedacht. Nachtragen ist nur " +
                    "vorgesehen, wenn technische Gründe im Weg standen.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )

            ErikField(
                label = "Zum Freischalten »$CATCH_UP_PHRASE« eingeben",
            ) {
                ErikTextField(
                    value = phrase,
                    onValueChange = onPhraseChange,
                    placeholder = CATCH_UP_PHRASE,
                )
            }

            openDays.forEach { date ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ErikText(
                        text = date.formatLong(),
                        style = ErikTheme.typography.body,
                        modifier = Modifier.weight(1f),
                    )
                    ErikButton(
                        text = "Nachtragen",
                        style = ErikButtonStyle.Secondary,
                        enabled = unlocked,
                        onClick = { onCatchUp(date) },
                    )
                }
            }

            ErikText(
                text = "Nachgetragen wird die Ernte des Tages — Verträge nicht: die werden " +
                    "am Tag selbst bezeugt, nicht eine Woche später.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun MessageBox(message: SettingsMessage, onDismiss: () -> Unit) {
    val danger = message is SettingsMessage.Failed

    ErikSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (danger) ErikTheme.colors.danger else ErikTheme.colors.success,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ErikText(
                    text = when (message) {
                        is SettingsMessage.Saved -> "Einrichtung gesichert."
                        is SettingsMessage.Exported -> "Daten gesichert."
                        is SettingsMessage.Imported -> "Daten eingelesen."
                        is SettingsMessage.CaughtUp -> "Tag nachgetragen."
                        is SettingsMessage.Failed -> message.reason
                    },
                    style = ErikTheme.typography.bodyStrong,
                    color = if (danger) ErikTheme.colors.danger else ErikTheme.colors.textPrimary,
                )
                val detail = when (message) {
                    is SettingsMessage.Saved -> when (message.conflicts) {
                        0 -> "Keine Doppeltbelegungen."
                        1 -> "Achtung: eine Doppeltbelegung."
                        else -> "Achtung: ${message.conflicts} Doppeltbelegungen."
                    }

                    is SettingsMessage.Exported -> "${message.bytes / 1024} KB geschrieben."
                    is SettingsMessage.CaughtUp ->
                        "${message.date.formatLong()} zählt wieder für die Serie."

                    // Room still holds the old files open, so nothing on screen is
                    // from the restored data until the process starts over.
                    is SettingsMessage.Imported -> "Bitte ERIK jetzt beenden und neu starten."
                    is SettingsMessage.Failed -> ""
                }
                if (detail.isNotEmpty()) {
                    ErikText(
                        text = detail,
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textSecondary,
                    )
                }
            }
            ErikButton(text = "OK", style = ErikButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

/** Moved here from the dashboard, which is where it never belonged. */
@Composable
private fun DebugBox(onReset: () -> Unit) {
    var armed by remember { mutableStateOf(false) }

    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikText(
                text = "Debug",
                style = ErikTheme.typography.label,
                color = ErikTheme.colors.textMuted,
            )
            ErikText(
                text = if (armed) {
                    "Wirklich alles löschen? Aufgaben, Blöcke, Punkte, Verträge und die " +
                        "Einrichtung sind danach weg."
                } else {
                    "Setzt die App auf den Zustand vor dem ersten Start zurück und ruft " +
                        "den Fragebogen erneut auf."
                },
                style = ErikTheme.typography.caption,
                color = if (armed) ErikTheme.colors.danger else ErikTheme.colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikButton(
                    text = if (armed) "Ja, alles zurücksetzen" else "Alles zurücksetzen",
                    style = ErikButtonStyle.Secondary,
                    onClick = { if (armed) onReset() else armed = true },
                )
                if (armed) {
                    ErikButton(
                        text = "Abbrechen",
                        style = ErikButtonStyle.Secondary,
                        onClick = { armed = false },
                    )
                }
            }
        }
    }
}
