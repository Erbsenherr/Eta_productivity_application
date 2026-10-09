package com.example.eta.ui.settings

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.alarm.AlarmSettingsIntents
import com.example.eta.alarm.EtaSound
import com.example.eta.alarm.EtaSpeech
import com.example.eta.domain.planning.TaskAnnouncement
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.eta.alarm.alarmReadiness
import com.example.eta.data.backup.BACKUP_FILE_NAME
import com.example.eta.domain.planning.STILL_ACTIVE_MAX_PER_DAY
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.streak.CATCH_UP_PHRASE
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaWeekdayPicker
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaStepper
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.attributes.CheckRow
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.setup.OnSetupChange
import com.example.eta.ui.setup.PlanningStep
import com.example.eta.ui.setup.SleepStep
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.theme.AppDesign
import com.example.eta.ui.theme.Brightness
import com.example.eta.ui.theme.DesignChoice
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.ui.subtasks.MorningRoutineSteps
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The Einstellungen tab: the standing configuration, away from planning a day.
 *
 * The questionnaire's own step composables are reused rather than reimplemented —
 * they are already `(draft, onChange)` pairs, so there is exactly one place where
 * each answer is asked for, and it cannot drift between the two screens.
 *
 * Only the answers that are **configuration** are here. Essen, Hausputz, Sport,
 * Freizeit, Achtsamkeit and Arbeit became standing tasks the moment the
 * questionnaire laid them down, and they are edited as tasks on the Listen tab;
 * asking them again here would overwrite whatever was changed there. Sleep and
 * the morning stay, because they hang off the night — see
 * `isOwnedBySettings`.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    /**
     * The calendar half, which shares nothing with the questionnaire: its own
     * connection, its own list, and a save button that would otherwise have two
     * meanings. Passed in the way the dashboard is passed its Quick-Add.
     */
    calendarViewModel: CalendarSettingsViewModel,
    design: DesignChoice,
    onDesignChange: (AppDesign) -> Unit,
    onBrightnessChange: (Brightness) -> Unit,
    modifier: Modifier = Modifier,
    onDebugReset: (() -> Unit)? = null,
    onOpenVacation: () -> Unit = {},
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val openDays by viewModel.openDays.collectAsStateWithLifecycle()
    val phrase by viewModel.phrase.collectAsStateWithLifecycle()
    val phraseAccepted by viewModel.phraseAccepted.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val morningSteps by viewModel.morningSteps.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    EtaScreen(modifier = modifier, bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Einstellungen", style = EtaTheme.typography.display)
                EtaText(
                    text = "Was dauerhaft gilt.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            message?.let { MessageBox(it, viewModel::dismissMessage) }

            DesignBox(design, onDesignChange, onBrightnessChange)

            AlarmBox(alarms)

            CalendarSettingsBox(calendarViewModel)

            DataBox(
                onExport = { exportLauncher.launch(BACKUP_FILE_NAME) },
                onImport = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
            )

            EtaSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                    EtaText(text = "Urlaubsmodus", style = EtaTheme.typography.heading)
                    EtaText(
                        text = "Für einen Zeitraum wiederkehrende Aufgaben aussetzen oder " +
                            "verschieben. Danach gilt wieder der gewohnte Plan.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                    EtaButton(
                        text = "Urlaub planen",
                        style = EtaButtonStyle.Secondary,
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
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaText(
                        text = "Einrichtung wird geladen …",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            } else {
                EtaText(text = "Deine Einrichtung", style = EtaTheme.typography.title)
                EtaText(
                    text = "Essen, Hausputz, Sport, Freizeit, Achtsamkeit und Arbeit sind " +
                        "wiederkehrende Aufgaben und werden im Reiter Listen bearbeitet. " +
                        "Hier bleibt, was an deiner Nacht hängt, und was dauerhaft gilt.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )

                SleepStep(setup, viewModel::update)
                MorningRoutineBox(morningSteps, viewModel::saveMorningSteps)
                WakeAlarmBox(setup, viewModel::update)
                SocialTimeBox(setup, viewModel::update)
                StillActiveBox(setup, viewModel::update)
                TaskAnnouncementBox(setup, viewModel::update)
                PlanningStep(setup, viewModel::update)
                AdvancedFeaturesBox(setup, viewModel::setFeature)
                // The two levers on the account go out of sight with it. Off the
                // draft rather than the stored answer, so the box above folds
                // them away the moment it is unticked.
                if (setup.pointsSystem) {
                    InflationBox(setup, viewModel::update)
                    CancellationBox(setup, viewModel::update)
                }

                EtaButton(text = "Einrichtung sichern", onClick = viewModel::save)
            }

            if (onDebugReset != null) {
                DebugBox(onReset = onDebugReset)
            }

            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }
}

/**
 * Which look the app wears.
 *
 * Not part of the draft below and not waiting for "Einrichtung sichern": a design
 * is judged by looking at it, so choosing one puts it on at once.
 */
@Composable
private fun DesignBox(
    choice: DesignChoice,
    onDesignChange: (AppDesign) -> Unit,
    onBrightnessChange: (Brightness) -> Unit,
) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Design", style = EtaTheme.typography.heading)
            EtaChoice(
                options = AppDesign.entries.map { it to it.label },
                selected = choice.design,
                onSelect = onDesignChange,
            )
            EtaText(
                text = choice.design.hint,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            // One button that steps through the three, as asked for: the answer
            // is on the button, and a tap gives the next one.
            EtaField(
                label = "Hell oder dunkel",
                hint = "Tippen wechselt weiter: dem Handy anpassen, hell, dunkel.",
            ) {
                EtaButton(
                    text = choice.brightness.label,
                    style = EtaButtonStyle.Secondary,
                    onClick = { onBrightnessChange(choice.brightness.next()) },
                )
            }
        }
    }
}

/**
 * The weekly budget of social time.
 *
 * It used to sit under the free-time question, which is a standing task now and
 * lives on the Listen tab. This one has no hour and lays down no block — it is a
 * lump the weekly planning takes out of the free hours — so it stays here.
 */
@Composable
private fun SocialTimeBox(setup: UserSetup, onChange: OnSetupChange) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Soziale Interaktion", style = EtaTheme.typography.heading)
            EtaField(
                label = "Pro Woche",
                hint = "Ohne feste Uhrzeit — wird in der Wochenplanung von den freien " +
                    "Stunden abgezogen.",
            ) {
                EtaDurationPicker(
                    value = setup.socialTimePerWeek,
                    onValueChange = { duration -> onChange { it.copy(socialTimePerWeek = duration) } },
                    step = 30.minutes,
                )
            }
        }
    }
}

/**
 * "Bin ich noch bei der Sache?" — asked at random moments during long tasks.
 *
 * Off by default. The count is per day across all tasks rather than per task, so
 * a day of meetings is not asked more often than a day with one long session.
 */
@Composable
private fun StillActiveBox(setup: UserSetup, onChange: OnSetupChange) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Bin ich noch bei der Sache?", style = EtaTheme.typography.heading)
            CheckRow(
                checked = setup.stillActiveReminder,
                onCheckedChange = { on -> onChange { it.copy(stillActiveReminder = on) } },
                label = "Zwischendurch nachfragen",
                hint = "Zu zufälligen Zeiten während Aufgaben ab einer Stunde — nicht " +
                    "während Pausen, Freizeit oder Punkte-Einträgen.",
            )
            if (setup.stillActiveReminder) {
                EtaField(label = "Wie oft am Tag") {
                    EtaStepper(
                        value = "${setup.stillActivePerDay}×",
                        onDecrement = {
                            onChange {
                                it.copy(stillActivePerDay = (it.stillActivePerDay - 1).coerceAtLeast(1))
                            }
                        },
                        onIncrement = {
                            onChange {
                                it.copy(
                                    stillActivePerDay = (it.stillActivePerDay + 1)
                                        .coerceAtMost(STILL_ACTIVE_MAX_PER_DAY),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Sound or voice for what a task has to say: its start, its end, the pomodoro
 * turns and the still-active question.
 *
 * The planning alarm, the reminders and the wake alarm keep their own sounds —
 * none of them has a task's name to read.
 *
 * "Probe hören" speaks at once and without saving, because the one thing this
 * choice depends on — whether the phone has a speech engine with a German voice —
 * cannot be told from a setting, only heard.
 */
@Composable
private fun TaskAnnouncementBox(setup: UserSetup, onChange: OnSetupChange) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var noEngine by remember { mutableStateOf(false) }
    val speaking = setup.taskAnnouncement == TaskAnnouncement.SPEECH

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Ansagen", style = EtaTheme.typography.heading)
            EtaText(
                text = "Wie sich eine Aufgabe meldet — wenn sie beginnt, endet, im " +
                    "Pomodoro wechselt oder nachfragt. Beides läuft über die " +
                    "Wecker-Lautstärke und schweigt bei „Nicht stören“, solange das " +
                    "unter „Alarme“ nicht anders gewählt ist.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaChoice(
                options = listOf(
                    TaskAnnouncement.SOUND to "Töne",
                    TaskAnnouncement.SPEECH to "Vorlesen — der Name der Aufgabe",
                ),
                selected = setup.taskAnnouncement,
                onSelect = { choice -> onChange { it.copy(taskAnnouncement = choice) } },
            )
            if (speaking) {
                CheckRow(
                    checked = setup.speakNotes,
                    onCheckedChange = { on -> onChange { it.copy(speakNotes = on) } },
                    label = "Notizen mit vorlesen",
                    hint = "Nur zum Beginn einer Aufgabe. Sehr lange Notizen können " +
                        "abbrechen, wenn das Handy schläft.",
                )
                EtaButton(
                    text = "Probe hören",
                    style = EtaButtonStyle.Secondary,
                    onClick = {
                        scope.launch {
                            noEngine = !EtaSpeech.speak(context, "Jetzt: Beispielaufgabe.")
                        }
                    },
                )
                if (noEngine) {
                    EtaText(
                        text = "Auf diesem Gerät antwortet keine Sprachausgabe. Solange " +
                            "das so ist, spielt Eta weiter die Töne.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.danger,
                    )
                }
            }
        }
    }
}

/**
 * The four features a newcomer does not need on the first day, each on show or
 * not: the points, the Growth-Tasks, the contracts and the Belohn-o-mat.
 *
 * All off for a new setup, so the bar holds four tabs rather than seven. **A
 * switch hides and stops nothing**: points are booked, growth tasks grow,
 * contracts are paid as kept and rewards fill exactly as before, which is what
 * lets any of them be switched back on with everything where it would have been.
 *
 * Applied the moment it is flipped — see `SettingsViewModel.setFeature`.
 */
@Composable
private fun AdvancedFeaturesBox(setup: UserSetup, onChange: OnSetupChange) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Advanced Features", style = EtaTheme.typography.heading)
            EtaText(
                text = "Gilt sofort. Ausgeschaltet verschwindet ein Feature nur aus der " +
                    "Ansicht — im Hintergrund läuft es weiter, und wieder eingeschaltet " +
                    "steht alles so da, als wäre es nie aus gewesen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            CheckRow(
                checked = setup.pointsSystem,
                onCheckedChange = { on -> onChange { it.copy(pointsSystem = on) } },
                label = "Punktetracker",
                hint = "Punktestand, Abrechnung am Abend und alle Punktangaben.",
            )
            CheckRow(
                checked = setup.growthTasks,
                onCheckedChange = { on -> onChange { it.copy(growthTasks = on) } },
                label = "Growth-Tasks",
                hint = "Der Reiter, und die Option, eine wiederkehrende Aufgabe wachsen " +
                    "zu lassen.",
            )
            CheckRow(
                checked = setup.contracts,
                onCheckedChange = { on -> onChange { it.copy(contracts = on) } },
                label = "Verträge",
                hint = "Der Reiter, und die Frage am Abend, ob sie gehalten wurden. " +
                    "Ausgeschaltet gelten laufende Verträge als gehalten.",
            )
            CheckRow(
                checked = setup.rewards,
                onCheckedChange = { on -> onChange { it.copy(rewards = on) } },
                label = "Belohn-o-mat",
                hint = "Der Reiter, und die Seite am Abend, auf der sich die Belohnung füllt.",
            )
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
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Wertverfall", style = EtaTheme.typography.heading)
            EtaText(
                text = "Einmal pro Woche verlieren gesparte Punkte 30 %. Der Abzug wird beim " +
                    "ersten Öffnen nach diesem Tag gebucht — auch ohne Wochenplanung.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaChoice(
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
                EtaField(label = "Fällig am") {
                    EtaWeekdayPicker(
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
 * What calling something off costs.
 *
 * A lever rather than a constant, because how hard the app should lean on a
 * cancellation is a matter for the person being leaned on. Zero switches the
 * charge off entirely, which is a legitimate answer — the day still charges for
 * the hours if they end up empty, and that was always the older rule.
 */
@Composable
private fun CancellationBox(setup: UserSetup, onChange: OnSetupChange) {
    val rate = setup.cancellationPenaltyPerHour

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Absagen", style = EtaTheme.typography.heading)
            EtaText(
                text = "Ein abgesagter Termin kostet seine Stunden an Punkten, abends " +
                    "mit dem Tag abgerechnet. Höhere Gewalt lässt sich im Rückblick " +
                    "angeben; verzichtete Freizeit ist ausgenommen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaField(
                label = "Punkte je abgesagter Stunde",
                hint = if (rate <= 0.0) {
                    "Aus — eine Absage kostet nichts."
                } else {
                    "Eine Viertelstunde kostet ${formatPoints(rate / 4)}, " +
                        "zwei Stunden ${formatPoints(rate * 2)}."
                },
            ) {
                EtaStepper(
                    value = formatPoints(rate),
                    valueWidth = 72.dp,
                    onDecrement = {
                        onChange {
                            it.copy(
                                cancellationPenaltyPerHour =
                                    (it.cancellationPenaltyPerHour - CANCELLATION_STEP)
                                        .coerceAtLeast(0.0),
                            )
                        }
                    },
                    onIncrement = {
                        onChange {
                            it.copy(
                                cancellationPenaltyPerHour =
                                    it.cancellationPenaltyPerHour + CANCELLATION_STEP,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Half a point, the scale the rest of the app counts in. */
private const val CANCELLATION_STEP = 0.5

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
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Deine Daten", style = EtaTheme.typography.heading)
            EtaText(
                text = "Eta speichert alles auf diesem Gerät, nichts geht nach außen. " +
                    "Wohin gesichert wird, bestimmst du — die Datei enthält den " +
                    "kompletten Bestand.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(text = "Sichern", onClick = onExport)
                EtaButton(
                    text = "Wiederherstellen",
                    style = EtaButtonStyle.Secondary,
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

    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Offene Tage", style = EtaTheme.typography.heading)
            EtaText(
                text = "${openDays.size} Tage wurden nie abgerechnet. Das kostet die Serie" +
                    (if (LocalPointsVisible.current) " und die Punkte dieser Tage" else "") +
                    " — so ist es gedacht. Nachtragen ist nur " +
                    "vorgesehen, wenn technische Gründe im Weg standen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            EtaField(
                label = "Zum Freischalten »$CATCH_UP_PHRASE« eingeben",
            ) {
                EtaTextField(
                    value = phrase,
                    onValueChange = onPhraseChange,
                    placeholder = CATCH_UP_PHRASE,
                )
            }

            // The list stays folded until the phrase is typed. It can run to
            // thirty rows, none of which can be acted on before then — unfolded,
            // it simply pushed the rest of the settings tab off the screen. The
            // count above still says the days are there; only the rows wait.
            if (unlocked) {
                openDays.forEach { date ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EtaText(
                            text = date.formatLong(),
                            style = EtaTheme.typography.body,
                            modifier = Modifier.weight(1f),
                        )
                        EtaButton(
                            text = "Nachtragen",
                            style = EtaButtonStyle.Secondary,
                            onClick = { onCatchUp(date) },
                        )
                    }
                }

                EtaText(
                    text = "Nachgetragen wird die Ernte des Tages — Verträge nicht: die " +
                        "werden am Tag selbst bezeugt, nicht eine Woche später.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            } else {
                EtaText(
                    text = "Die Liste erscheint, sobald der Satz vollständig dasteht.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun MessageBox(message: SettingsMessage, onDismiss: () -> Unit) {
    val danger = message is SettingsMessage.Failed

    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (danger) EtaTheme.colors.danger else EtaTheme.colors.success,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EtaText(
                    text = when (message) {
                        SettingsMessage.Saved -> "Einrichtung gesichert."
                        is SettingsMessage.Exported -> "Daten gesichert."
                        is SettingsMessage.Imported -> "Daten eingelesen."
                        is SettingsMessage.CaughtUp -> "Tag nachgetragen."
                        is SettingsMessage.Failed -> message.reason
                    },
                    style = EtaTheme.typography.bodyStrong,
                    color = if (danger) EtaTheme.colors.danger else EtaTheme.colors.textPrimary,
                )
                val detail = when (message) {
                    // The rule every edit to a standing task follows.
                    SettingsMessage.Saved ->
                        "Bettfertig und Morgenroutine gelten ab dem ersten noch nicht " +
                            "bestätigten Tag."

                    is SettingsMessage.Exported -> "${message.bytes / 1024} KB geschrieben."
                    is SettingsMessage.CaughtUp ->
                        "${message.date.formatLong()} zählt wieder für die Serie."

                    // Room still holds the old files open, so nothing on screen is
                    // from the restored data until the process starts over.
                    is SettingsMessage.Imported -> "Bitte Eta jetzt beenden und neu starten."
                    is SettingsMessage.Failed -> ""
                }
                if (detail.isNotEmpty()) {
                    EtaText(
                        text = detail,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                }
            }
            EtaButton(text = "OK", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

/**
 * The steps of the morning routine.
 *
 * Under the sleep card, because that is where the morning's hour and length are
 * set. Saved at once, unlike the answers around it: the steps are not part of
 * the draft, and a list edited in a dialog with its own "Sichern" that then
 * waited for a second one further down would lose edits.
 */
@Composable
private fun MorningRoutineBox(
    steps: List<SubtaskDraft>,
    onSave: (List<SubtaskDraft>) -> Unit,
) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Morgenroutine", style = EtaTheme.typography.heading)
            EtaText(
                text = "Beginnt mit dem Aufstehen und dauert so lange, wie oben unter " +
                    "„Morgenroutine“ steht. Die Schritte laufen im Routine-Modus: " +
                    "„Gerade“ zeigt immer nur den nächsten, Abhaken hält die Uhrzeit " +
                    "fest. Änderungen an den Schritten gelten sofort.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            MorningRoutineSteps(steps = steps, onSave = onSave)
        }
    }
}

/** Moved here from the dashboard, which is where it never belonged. */
@Composable
private fun DebugBox(onReset: () -> Unit) {
    var armed by remember { mutableStateOf(false) }

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = "Debug",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textMuted,
            )
            EtaText(
                text = if (armed) {
                    "Wirklich alles löschen? Aufgaben, Blöcke, Punkte, Verträge und die " +
                        "Einrichtung sind danach weg."
                } else {
                    "Setzt die App auf den Zustand vor dem ersten Start zurück und ruft " +
                        "den Fragebogen erneut auf."
                },
                style = EtaTheme.typography.caption,
                color = if (armed) EtaTheme.colors.danger else EtaTheme.colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = if (armed) "Ja, alles zurücksetzen" else "Alles zurücksetzen",
                    style = EtaButtonStyle.Secondary,
                    onClick = { if (armed) onReset() else armed = true },
                )
                if (armed) {
                    EtaButton(
                        text = "Abbrechen",
                        style = EtaButtonStyle.Secondary,
                        onClick = { armed = false },
                    )
                }
            }
        }
    }
}

/**
 * Eta's own wake alarm.
 *
 * It rings at the setup's `wakeTime` rather than at a time of its own, so the
 * hour the planner shades as the end of the night and the hour the phone rings
 * at can never drift apart. Off by default — an app that starts waking someone
 * because they answered a questionnaire has overstepped.
 */
@Composable
private fun WakeAlarmBox(setup: UserSetup, onChange: OnSetupChange) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Weckruf", style = EtaTheme.typography.heading)
            val weekendWake = setup.weekendNight?.wake
            val hours = setup.wakeTime.formatClock() +
                (weekendWake?.let { ", am Wochenende um ${it.formatClock()}" } ?: "")
            EtaText(
                text = "Eta weckt dich um $hours Uhr — dieselbe " +
                    "Aufstehzeit, die oben im Schlaf steht. Der Weckruf klingelt über den " +
                    "Sperrbildschirm und läutet, bis du ihn beendest oder neun Minuten " +
                    "schlummerst.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaChoice(
                options = listOf(
                    false to "Kein Weckruf",
                    true to if (weekendWake == null) {
                        "Jeden Tag um ${setup.wakeTime.formatClock()} wecken"
                    } else {
                        "Jeden Tag zur Aufstehzeit wecken"
                    },
                ),
                selected = setup.wakeAlarm,
                onSelect = { on -> onChange { it.copy(wakeAlarm = on) } },
            )
        }
    }
}

/**
 * Whether the alarms can actually get through, and when they are next due.
 *
 * This card exists because every way an alarm can be suppressed on Android is
 * **silent**: a refused notification permission makes the planning alarm fire,
 * reschedule and show nothing; a denied exact-alarm permission turns every alarm
 * into a window doze may push past its hour; a battery manager can stop the app
 * outright. From the outside all three look identical — no alarm and no reason —
 * so the app has to be able to name which one it is.
 *
 * The due times sit next to them for the same reason: without them, an alarm
 * that is armed for tomorrow cannot be told apart from one that was never set.
 */
@Composable
private fun AlarmBox(alarms: AlarmSchedule) {
    val context = LocalContext.current
    // Read again on demand rather than watched: these change in the system
    // settings, which means leaving the app, and coming back is the moment to
    // look. A tap is a smaller price than a poll that runs all day.
    var probe by remember { mutableIntStateOf(0) }
    val readiness = remember(probe) { alarmReadiness(context) }

    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (readiness.allGood) EtaTheme.colors.border else EtaTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Alarme", style = EtaTheme.typography.heading)
            var ignoreDnd by remember { mutableStateOf(EtaSound.ignoresDoNotDisturb(context)) }
            val dndOn = remember(probe) { EtaSound.isDoNotDisturbOn(context) }
            EtaText(
                text = when {
                    dndOn && ignoreDnd ->
                        "„Nicht stören“ ist gerade an — Etas Töne spielen trotzdem, " +
                            "solange das Handy Wecker durchlässt."
                    dndOn ->
                        "„Nicht stören“ ist gerade an — solange bleiben Etas Töne still. " +
                            "Benachrichtigungen erscheinen trotzdem."
                    ignoreDnd ->
                        "Etas Töne laufen über die Wecker-Lautstärke, unabhängig von der " +
                            "Lautstärke für Benachrichtigungen, auch bei „Nicht stören“."
                    else ->
                        "Etas Töne laufen über die Wecker-Lautstärke, unabhängig von der " +
                            "Lautstärke für Benachrichtigungen. Still sind sie nur bei „Nicht stören“."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            EtaField(
                label = "Bei „Nicht stören“",
                hint = "Gilt sofort, für Töne und Ansagen. Bei „Lautlos komplett“ " +
                    "schaltet das Handy auch Wecker stumm — daran kann Eta nichts ändern. " +
                    "Der Weckalarm klingelt ohnehin immer.",
            ) {
                EtaChoice(
                    options = listOf(false to "Still bleiben", true to "Trotzdem spielen"),
                    selected = ignoreDnd,
                    onSelect = { chosen ->
                        EtaSound.setIgnoresDoNotDisturb(context, chosen)
                        ignoreDnd = chosen
                    },
                )
            }

            ReadinessRow(
                label = "Benachrichtigungen",
                ok = readiness.notificationsAllowed,
                whenMissing = "Ohne sie klingelt der Alarm, zeigt aber nichts.",
                onFix = { context.startActivity(AlarmSettingsIntents.notifications(context)) },
            )
            ReadinessRow(
                label = "Exakte Alarme",
                ok = readiness.exactAlarmsAllowed,
                whenMissing = "Ohne sie kommen Alarme irgendwann im Zeitfenster — " +
                    "im Doze-Modus auch deutlich später.",
                onFix = { context.startActivity(AlarmSettingsIntents.exactAlarms(context)) },
            )
            ReadinessRow(
                label = "Akku-Optimierung",
                ok = readiness.ignoringBatteryOptimisation,
                whenMissing = "Manche Hersteller stoppen die App im Hintergrund ganz. " +
                    "Eta von der Optimierung ausnehmen.",
                onFix = { context.startActivity(AlarmSettingsIntents.batteryOptimisation(context)) },
            )

            EtaText(
                text = "Als Nächstes",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textSecondary,
            )
            DueRow("Tagesplanung", alarms.daily)
            DueRow("Wochenplanung", alarms.weekly)
            DueRow("Weckruf", alarms.wake, offHint = "aus")
            DueRow("Aufgaben-Töne", alarms.task, offHint = "nichts geplant")
            DueRow("Erinnerung", alarms.reminder, offHint = "keine")

            EtaButton(
                text = "Neu prüfen",
                style = EtaButtonStyle.Secondary,
                onClick = { probe++ },
            )
        }
    }
}

@Composable
private fun ReadinessRow(
    label: String,
    ok: Boolean,
    whenMissing: String,
    onFix: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtaText(
                text = label,
                style = EtaTheme.typography.body,
                modifier = Modifier.weight(1f),
            )
            EtaText(
                text = if (ok) "erlaubt" else "fehlt",
                style = EtaTheme.typography.label,
                color = if (ok) EtaTheme.colors.success else EtaTheme.colors.warning,
            )
        }
        if (!ok) {
            EtaText(
                text = whenMissing,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaButton(text = "Einstellen", style = EtaButtonStyle.Secondary, onClick = onFix)
        }
    }
}

@Composable
private fun DueRow(label: String, at: Instant?, offHint: String = "nicht gesetzt") {
    val zone = TimeZone.currentSystemDefault()
    Row(verticalAlignment = Alignment.CenterVertically) {
        EtaText(
            text = label,
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        EtaText(
            text = at?.toLocalDateTime(zone)?.let {
                "${it.date.formatLong()}, ${it.time.formatClock()}"
            } ?: offHint,
            style = EtaTheme.typography.caption,
            color = if (at == null) EtaTheme.colors.textMuted else EtaTheme.colors.textSecondary,
        )
    }
}
