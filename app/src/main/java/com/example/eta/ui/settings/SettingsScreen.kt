package com.example.eta.ui.settings

import com.example.eta.domain.tutorial.TutorialId
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.eta.alarm.AudioMode
import com.example.eta.alarm.ClockApp
import com.example.eta.alarm.ClockAppAlarms
import com.example.eta.domain.setup.clockAlarms
import com.example.eta.ui.components.EtaSwatch
import com.example.eta.ui.components.EtaIcon
import com.example.eta.ui.components.EtaIconImage
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.theme.ACCENT_PRESETS
import com.example.eta.ui.theme.TabStyle
import com.example.eta.ui.theme.accentOfHue
import com.example.eta.ui.theme.colorsOf
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
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
import com.example.eta.domain.stats.StatSection
import com.example.eta.domain.stats.showsStat
import com.example.eta.domain.stats.withStat
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
import com.example.eta.domain.setup.NightMode
import com.example.eta.domain.setup.nightMode
import kotlin.time.Duration.Companion.hours
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
    /** An accent of the user's own, or null for the design's. */
    onAccentChange: (Int?) -> Unit = {},
    onTabStyleChange: (TabStyle) -> Unit = {},
    /** The tabs as the bar shows them, for the Navigation page to list. */
    tabs: List<Pair<String, EtaIcon>> = emptyList(),
    onDebugReset: (() -> Unit)? = null,
    onOpenVacation: () -> Unit = {},
    /**
     * Starts a tutorial: null is the choice of all of them, an id that one at
     * once. Offered on the tutorial page and beside the Advanced Features.
     */
    onStartTutorial: ((TutorialId?) -> Unit)? = null,
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

    val dirty by viewModel.dirty.collectAsStateWithLifecycle()

    // Which page is open, null being the menu. Saved by name, like the tabs.
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = SettingsPage.entries.firstOrNull { it.name == pageName }
    val setup = draft

    // Back leaves a page for the menu before it leaves the tab.
    BackHandler(enabled = page != null) { pageName = null }

    // Where the menu is scrolled to — its own, so a page and back does not
    // start it at the top again.
    val menuScroll = rememberScrollState()

    EtaScreen(modifier = modifier, bottomInset = false) {
        // Keyed by the page, so each opens at its top rather than wherever the
        // menu happened to be scrolled to. The menu's own position is held
        // outside the key: coming back from a page lands where it was left.
        key(page) {
            val pageScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(if (page == null) menuScroll else pageScroll)
                    .padding(EtaTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
            ) {
                if (page == null) {
                    Column {
                        EtaText(text = "Einstellungen", style = EtaTheme.typography.display)
                        EtaText(
                            text = "Was dauerhaft gilt.",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                    }
                } else {
                    Row {
                        EtaButton(
                            text = "‹ Einstellungen",
                            style = EtaButtonStyle.Secondary,
                            onClick = { pageName = null },
                        )
                    }
                    EtaText(text = page.title, style = EtaTheme.typography.title)
                }

                message?.let { MessageBox(it, viewModel::dismissMessage) }

                when (page) {
                    null -> SettingsMenu(
                        // The two levers on the account are out of sight with it.
                        pages = SettingsPage.entries.filter { entry ->
                            when (entry) {
                                SettingsPage.INFLATION, SettingsPage.CANCELLATION ->
                                    setup?.pointsSystem == true
                                SettingsPage.DEBUG -> onDebugReset != null
                                SettingsPage.TUTORIAL -> onStartTutorial != null
                                else -> true
                            }
                        },
                        dirty = dirty,
                        onOpen = { pageName = it.name },
                        onOpenVacation = onOpenVacation,
                        onSave = viewModel::save,
                        onDiscard = viewModel::discard,
                    )

                    SettingsPage.DESIGN -> DesignBox(design, onDesignChange, onBrightnessChange, onAccentChange)
                    SettingsPage.NAVIGATION -> NavigationBox(design.tabStyle, onTabStyleChange, tabs)
                    SettingsPage.ALARMS -> AlarmBox(alarms)
                    SettingsPage.CALENDAR -> CalendarSettingsBox(calendarViewModel)
                    SettingsPage.DATA -> DataBox(
                        onExport = { exportLauncher.launch(BACKUP_FILE_NAME) },
                        onImport = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
                    )

                    SettingsPage.CATCH_UP -> CatchUpBox(
                        openDays = openDays,
                        phrase = phrase,
                        unlocked = phraseAccepted,
                        onPhraseChange = viewModel::setPhrase,
                        onCatchUp = viewModel::catchUp,
                    )

                    SettingsPage.TUTORIAL -> onStartTutorial?.let { TutorialBox(onStart = it) }
                    SettingsPage.DEBUG -> onDebugReset?.let { DebugBox(onReset = it) }

                    // Everything below edits the draft of the stored answers.
                    else -> if (setup == null) {
                        EtaSurface(modifier = Modifier.fillMaxWidth()) {
                            EtaText(
                                text = "Einrichtung wird geladen …",
                                style = EtaTheme.typography.body,
                                color = EtaTheme.colors.textMuted,
                            )
                        }
                    } else {
                        when (page) {
                            SettingsPage.SLEEP -> {
                                EtaText(
                                    text = "Essen, Hausputz, Sport, Freizeit, Achtsamkeit und " +
                                        "Arbeit sind wiederkehrende Aufgaben und werden im " +
                                        "Reiter Listen bearbeitet. Hier bleibt, was an deiner " +
                                        "Nacht hängt.",
                                    style = EtaTheme.typography.caption,
                                    color = EtaTheme.colors.textMuted,
                                )
                                SleepStep(setup, viewModel::update)
                                MorningRoutineBox(morningSteps, viewModel::saveMorningSteps)
                            }

                            SettingsPage.WAKE -> {
                                WakeAlarmBox(setup, viewModel::update)
                                ClockAlarmsBox(setup = setup, dirty = dirty)
                            }
                            SettingsPage.STILL_ACTIVE -> StillActiveBox(setup, viewModel::update)
                            SettingsPage.ANNOUNCEMENT -> {
                                AudioModeBox()
                                TaskAnnouncementBox(setup, viewModel::update)
                            }
                            SettingsPage.PLANNING -> PlanningStep(setup, viewModel::update)
                            SettingsPage.FEATURES -> {
                                AdvancedFeaturesBox(setup, viewModel::setFeature)
                                onStartTutorial?.let { FeatureTutorialsBox(onStart = it) }
                            }
                            SettingsPage.FLEXIBILITY -> FlexibilityBox(setup, viewModel::setFeature)
                            SettingsPage.BREAKS -> BreakCheckerBox(setup, viewModel::setFeature)
                            SettingsPage.STATISTICS -> StatisticsBox(setup, viewModel::setFeature)
                            SettingsPage.INFLATION -> InflationBox(setup, viewModel::update)
                            SettingsPage.CANCELLATION -> CancellationBox(setup, viewModel::update)
                            else -> Unit
                        }

                        // The switches of the Advanced Features, the Flexibilität
                        // and the Pausenchecker apply at once and have nothing
                        // to save.
                        if (page != SettingsPage.FEATURES &&
                            page != SettingsPage.FLEXIBILITY &&
                            page != SettingsPage.BREAKS &&
                            page != SettingsPage.STATISTICS
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                                EtaButton(text = "Sichern", enabled = dirty, onClick = viewModel::save)
                                if (dirty) {
                                    EtaButton(
                                        text = "Verwerfen",
                                        style = EtaButtonStyle.Secondary,
                                        onClick = viewModel::discard,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.size(EtaTheme.spacing.xl))
            }
        }
    }
}

/**
 * The pages of the settings, in the order the menu lists them.
 *
 * One page per subject, and the menu shows their titles and nothing else: the
 * tab used to be every box under every other, and finding one meant scrolling
 * past all the rest.
 */
private enum class SettingsPage(val title: String, val section: SettingsSection) {
    DESIGN("Design", SettingsSection.APP),
    NAVIGATION("Navigation", SettingsSection.APP),
    ALARMS("Alarme", SettingsSection.APP),
    CALENDAR("Google Kalender", SettingsSection.APP),
    DATA("Deine Daten", SettingsSection.APP),
    CATCH_UP("Offene Tage", SettingsSection.APP),

    SLEEP("Schlaf und Morgen", SettingsSection.SETUP),
    WAKE("Weckruf", SettingsSection.SETUP),
    PLANNING("Planungsphasen", SettingsSection.SETUP),
    STILL_ACTIVE("Bin ich noch bei der Sache?", SettingsSection.SETUP),
    ANNOUNCEMENT("Audio", SettingsSection.SETUP),
    FEATURES("Advanced Features", SettingsSection.SETUP),
    FLEXIBILITY("Flexibilität", SettingsSection.SETUP),
    BREAKS("Pausenchecker", SettingsSection.SETUP),
    STATISTICS("Statistiken", SettingsSection.SETUP),
    INFLATION("Wertverfall", SettingsSection.SETUP),
    CANCELLATION("Absagen", SettingsSection.SETUP),

    /** Last, as asked: opened once in a long while, by someone looking for it. */
    DEBUG("Debug", SettingsSection.MORE),
    TUTORIAL("Tutorial", SettingsSection.MORE),
}

private enum class SettingsSection(val label: String) {
    APP("App"),
    SETUP("Deine Einrichtung"),
    MORE("Sonstiges"),
}

/**
 * The menu: titles, and the way into each.
 *
 * The holiday editor is a flow of its own rather than a page, so its row leads
 * straight there. Answers changed on a page and not yet saved are said here as
 * well — a page can be left by the tab bar, and the draft would otherwise sit
 * changed and unsaved with nothing on screen to show it.
 */
@Composable
private fun SettingsMenu(
    pages: List<SettingsPage>,
    dirty: Boolean,
    onOpen: (SettingsPage) -> Unit,
    onOpenVacation: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (dirty) {
        EtaSurface(modifier = Modifier.fillMaxWidth(), borderColor = EtaTheme.colors.warning) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaText(text = "Ungesicherte Änderungen", style = EtaTheme.typography.bodyStrong)
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaButton(text = "Sichern", onClick = onSave)
                    EtaButton(text = "Verwerfen", style = EtaButtonStyle.Secondary, onClick = onDiscard)
                }
            }
        }
    }

    SettingsSection.entries.forEach { section ->
        val rows = pages.filter { it.section == section }
        if (rows.isEmpty()) return@forEach

        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = section.label,
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textMuted,
            )
            rows.forEach { entry ->
                MenuRow(title = entry.title, onClick = { onOpen(entry) })
                // Beside the other thing that is about days rather than answers.
                if (entry == SettingsPage.CATCH_UP) {
                    MenuRow(title = "Urlaubsmodus", onClick = onOpenVacation)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    EtaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentPadding = EtaTheme.spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtaText(
                text = title,
                style = EtaTheme.typography.bodyStrong,
                modifier = Modifier.weight(1f),
            )
            EtaText(text = "›", style = EtaTheme.typography.heading, color = EtaTheme.colors.textMuted)
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
    onAccentChange: (Int?) -> Unit,
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

            AccentField(choice = choice, onAccentChange = onAccentChange)
        }
    }
}

/**
 * The accent: the design's own, one of the swatches, or any hue off the bar.
 *
 * Applied at once, like the rest of the card. The first swatch is the design's
 * own colour and takes the choice back — it shows that colour rather than a
 * word, so what a tap would give is on the button.
 */
@Composable
private fun AccentField(choice: DesignChoice, onAccentChange: (Int?) -> Unit) {
    val own = colorsOf(choice.design, choice.brightness.isDark(isSystemInDarkTheme())).accent
    val change by rememberUpdatedState(onAccentChange)

    EtaField(
        label = "Akzentfarbe",
        hint = "Gilt sofort, in jedem Design. Das erste Feld ist die Farbe des Designs " +
            "selbst. Das Widget und der Startbildschirm behalten ihre Farbe.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
            ) {
                EtaSwatch(color = own, selected = choice.accent == null, onClick = { onAccentChange(null) })
                ACCENT_PRESETS.forEach { preset ->
                    EtaSwatch(
                        color = Color(preset),
                        selected = choice.accent == preset,
                        onClick = { onAccentChange(preset) },
                    )
                }
            }

            EtaText(
                text = "Oder ein eigener Farbton — tippen oder ziehen:",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            // Every hue side by side. The gesture reads the callback through
            // State: `pointerInput(Unit)` keeps the lambda it was launched with.
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { change(accentOfHue(it.x / size.width * 360f)) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { input, _ ->
                            change(accentOfHue(input.position.x / size.width * 360f))
                        }
                    },
            ) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        (0..12).map { Color(accentOfHue(it * 30f)) },
                    ),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
        }
    }
}

/**
 * What the tab bar shows, and which icon stands for which tab.
 *
 * The icons are the app's own choice, one per destination; what the user picks
 * is whether to see them, the names, or both. Applied at once and kept with the
 * design — it is a look, and it has to be known before the first frame.
 */
@Composable
private fun NavigationBox(
    style: TabStyle,
    onStyleChange: (TabStyle) -> Unit,
    tabs: List<Pair<String, EtaIcon>>,
) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Navigation", style = EtaTheme.typography.heading)
            EtaText(
                text = "Was die Leiste am unteren Rand zeigt. Gilt sofort.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaChoice(
                options = TabStyle.entries.map { it to it.label },
                selected = style,
                onSelect = onStyleChange,
            )
            if (style == TabStyle.ICONS) {
                EtaText(
                    text = "Nur mit Icons passen die meisten Reiter ohne Wischen in die Leiste.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            EtaText(text = "Die Icons", style = EtaTheme.typography.bodyStrong)
            tabs.forEach { (name, icon) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtaIconImage(icon = icon, color = EtaTheme.colors.accent)
                    Spacer(Modifier.size(EtaTheme.spacing.md))
                    EtaText(text = name, style = EtaTheme.typography.body)
                }
            }
        }
    }
}

/**
 * The morning alarm, handed to the phone's clock app.
 *
 * A button rather than a switch, because that is all Android offers: an alarm
 * can be created in a clock app and neither changed nor removed afterwards —
 * see `ClockAppAlarms`. So the box says what would be made, makes it when
 * asked, and says what stays the user's to do.
 */
@Composable
private fun ClockAlarmsBox(setup: UserSetup, dirty: Boolean) {
    val context = LocalContext.current
    val apps = remember { ClockAppAlarms.available(context) }
    var app by remember { mutableStateOf(ClockAppAlarms.chosen(context).takeIf { it in apps } ?: ClockApp.SYSTEM) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val alarms = remember(setup) { setup.clockAlarms() }

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Wecker in der Uhr-App", style = EtaTheme.typography.heading)
            EtaText(
                text = "Statt selbst zu klingeln, kann Eta deine Aufstehzeiten als Wecker in " +
                    "der Uhr-App des Handys anlegen — die ist auf den meisten Geräten vom " +
                    "Akkusparen ausgenommen und damit am zuverlässigsten.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            if (apps.isEmpty()) {
                EtaText(
                    text = "Auf diesem Handy wurde keine Uhr-App gefunden, die Wecker annimmt.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.warning,
                )
                return@Column
            }

            if (apps.size > 1) {
                EtaField(label = "Uhr-App") {
                    EtaChoice(
                        options = apps.map { it to it.label },
                        selected = app,
                        onSelect = {
                            app = it
                            ClockAppAlarms.choose(context, it)
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                EtaText(
                    text = if (alarms.size == 1) "Angelegt wird ein Wecker:" else "Angelegt werden ${alarms.size} Wecker:",
                    style = EtaTheme.typography.bodyStrong,
                )
                alarms.forEach { alarm ->
                    EtaText(
                        text = "· ${alarm.time.formatClock()} — ${formatWeekdays(alarm.days)}",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textSecondary,
                    )
                }
            }

            EtaButton(
                text = "Wecker anlegen",
                enabled = !dirty,
                onClick = { result = ClockAppAlarms.setUp(context, alarms, app) },
            )
            when {
                dirty -> EtaText(
                    text = "Sichere zuerst deine Änderungen — angelegt werden die gesicherten Zeiten.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
                )

                result == true -> EtaText(
                    text = "An die Uhr-App übergeben. Sieh dort einmal nach, ob die Wecker stehen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.success,
                )

                result == false -> EtaText(
                    text = "Die Uhr-App hat nicht geantwortet.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
                )
            }

            if (setup.wakeAlarm) {
                EtaText(
                    text = "Etas eigener Weckruf ist noch an. Schalte ihn oben aus und sichere, " +
                        "sonst klingelt es morgens doppelt.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
                )
            }
            EtaText(
                text = "Android lässt Apps Wecker nur anlegen, nicht ändern oder löschen. " +
                    "Änderst du später deine Aufstehzeiten, lösche die alten Wecker in der " +
                    "Uhr-App und lege sie hier neu an. Eine Nacht, die du nur für einen Tag " +
                    "verschoben hast, kommt dort nicht an.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
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
/**
 * Sound, vibration or nothing — for everything Eta announces, the wake alarm
 * excepted.
 *
 * Above the choice between sounds and reading aloud, which only matters while
 * this says "Ton". Applied at once and kept beside the Do-Not-Disturb switch;
 * see `AudioMode`.
 */
@Composable
private fun AudioModeBox() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(EtaSound.mode(context)) }

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Audio", style = EtaTheme.typography.heading)
            EtaText(
                text = "Wie Eta sich meldet: bei Aufgaben, Pomodoro und Timer, bei den " +
                    "Planungsphasen und bei Erinnerungen. Gilt sofort. Der Weckruf am " +
                    "Morgen klingelt immer.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaChoice(
                options = AudioMode.entries.map { it to it.label },
                selected = mode,
                onSelect = {
                    mode = it
                    EtaSound.setMode(context, it)
                },
            )
            EtaText(
                text = when (mode) {
                    AudioMode.SOUND -> "Töne oder Ansage, wie unten gewählt."
                    AudioMode.VIBRATE ->
                        "Statt eines Tons vibriert das Handy kurz. Vorgelesen wird nichts. " +
                            "Steht das Handy auf »Lautlos«, bleibt auch die Vibration aus."

                    AudioMode.SILENT -> "Kein Ton und keine Vibration — nur die Benachrichtigung."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
}

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
 * Only the points are on for a new setup, so the bar holds four tabs rather
 * than seven. **A
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
            // Under the tracker and only with it: it is a rule of the points.
            if (setup.pointsSystem) {
                UnplannedPenaltyRows(setup, onChange)
            }
            CheckRow(
                checked = setup.growthTasks,
                onCheckedChange = { on -> onChange { it.copy(growthTasks = on) } },
                label = "Timer-Tasks und Ziele",
                hint = "Der Reiter »Ziele«, und die Option, eine wiederkehrende Aufgabe " +
                    "zur Timer-Task zu machen — mit Start-Knopf, Puffer und Zielzeit.",
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
            CheckRow(
                checked = setup.notes,
                onCheckedChange = { on -> onChange { it.copy(notes = on) } },
                label = "Notiz-Merger",
                hint = "Ein Reiter für Notizen mit Titel und Text, die sich per »Taskify« " +
                    "in ToDos verwandeln lassen. Ausgeschaltet bleiben die Notizen erhalten.",
            )
        }
    }
}

/**
 * The Statistiken tab: on or off, and which of its contents it shows.
 *
 * Applied at once, like the switches of the pages around it. Hiding something
 * stops nothing being counted — the tab works from the plan's own history, so a
 * figure switched back on is complete from the first day.
 */
@Composable
private fun StatisticsBox(setup: UserSetup, onChange: OnSetupChange) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Statistiken", style = EtaTheme.typography.heading)
            EtaText(
                text = "Gilt sofort.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            CheckRow(
                checked = setup.statistics,
                onCheckedChange = { on -> onChange { it.copy(statistics = on) } },
                label = "Reiter »Statistiken« anzeigen",
                hint = "Ausgeschaltet verschwindet der ganze Reiter. Die Fragen nach " +
                    "Aufstehen und Schlafengehen im Dashboard bleiben.",
            )
            if (setup.statistics) {
                EtaText(text = "Inhalte", style = EtaTheme.typography.bodyStrong)
                StatSection.entries.forEach { section ->
                    key(section) {
                        CheckRow(
                            checked = setup.showsStat(section),
                            onCheckedChange = { on -> onChange { it.withStat(section, on) } },
                            label = section.label,
                            hint = when (section) {
                                StatSection.WAKE -> "Wie oft du pünktlich aus dem Bett gekommen bist."
                                StatSection.BED -> "Wie oft du pünktlich ins Bett gegangen bist."
                                StatSection.PLANNED -> "Wie viel deiner wachen Zeit verplant war."
                                StatSection.COMPLETED ->
                                    "Wie viele der vorgenommenen Aufgaben abgeschlossen wurden."

                                StatSection.CATEGORIES -> "Stunden in Fokus, Nebenbei und Achtsam."
                                StatSection.RECURRING ->
                                    "Je wiederkehrender Aufgabe: Stunden, Ausfälle, Planänderungen."

                                StatSection.GOALS ->
                                    "Dasselbe für Timer- und Inkrement-Tasks, mit Verlauf des Pensums."
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The rules that may be loosened, each off until someone asks for it.
 *
 * The page opens on why: the app is meant to be used on its strictest rules, and
 * this is where to go when that does not work for someone — not a list of
 * features to try. Applied at once, like the Advanced Features: a switch that
 * waited for a save button further down would read as one that does nothing.
 */
@Composable
private fun FlexibilityBox(setup: UserSetup, onChange: OnSetupChange) {
    EtaSurface(modifier = Modifier.fillMaxWidth(), borderColor = EtaTheme.colors.accent) {
        EtaText(
            text = "Eta ist darauf ausgelegt, mit den striktesten Einstellungen genutzt zu " +
                "werden — die Regeln sind das, was die App leistet. Wenn du damit " +
                "überhaupt nicht zurechtkommst, kannst du sie hier lockern.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
    }
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Flexibilität", style = EtaTheme.typography.heading)
            EtaText(
                text = "Gilt sofort.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            CheckRow(
                checked = setup.allowPrioritySkip,
                onCheckedChange = { on -> onChange { it.copy(allowPrioritySkip = on) } },
                label = "Prioritäten überspringen",
                hint = "Im Tagesplaner lässt sich eine Prioritätsstufe von Hand " +
                    "überspringen. Ausgeschaltet kommt die nächste Stufe erst, wenn die " +
                    "höhere eingeplant ist oder nichts mehr von ihr in den Tag passt.",
            )
            CheckRow(
                checked = setup.allowTodoBesideWeek,
                onCheckedChange = { on -> onChange { it.copy(allowTodoBesideWeek = on) } },
                label = "Neue ToDos trotz offener Wochenliste",
                hint = "Im Tagesplaner lässt sich ein neues ToDo anlegen, obwohl in der " +
                    "Wochenliste noch Aufgaben warten, die in den Tag passen würden.",
            )
            CheckRow(
                checked = setup.allowPartialTimerGrowth,
                onCheckedChange = { on -> onChange { it.copy(allowPartialTimerGrowth = on) } },
                label = "Zielzeit wächst auch bei vorzeitigem Ende",
                hint = "Eine Timer-Task mit Zielzeit zählt einen Termin auch dann für das " +
                    "nächste Inkrement, wenn der Timer nicht voll abgearbeitet wurde. " +
                    "Ausgeschaltet wächst die Zeit nur nach vollständigen Terminen.",
            )
        }
    }
}

/**
 * The Pausenchecker: on or off, and what it measures a day against.
 *
 * A help, not a rule — it warns in the day planner and each warning can be
 * waved away for its day, so nothing here stops a plan from being confirmed.
 * Applied at once, like the switches above it in the menu.
 */
@Composable
private fun BreakCheckerBox(setup: UserSetup, onChange: OnSetupChange) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Pausenchecker", style = EtaTheme.typography.heading)
            EtaText(
                text = "Gilt sofort.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            CheckRow(
                checked = setup.breakChecker,
                onCheckedChange = { on -> onChange { it.copy(breakChecker = on) } },
                label = "Auf Pausen achten",
                hint = "Der Tagesplaner weist darauf hin, wenn für einen Tag zu wenig Pause " +
                    "verplant ist. Als Pause zählen Pausen, Freizeit, Essen, Achtsamkeit " +
                    "und Custom Spend — Pomodoro-Pausen kommen obendrauf und zählen " +
                    "nicht mit. Jeder Hinweis lässt sich für den Tag ignorieren.",
            )
            if (setup.breakChecker) {
                EtaField(
                    label = "Pause je 2 Stunden Aufgaben",
                    hint = "So viel Pause sollte auf je zwei volle Stunden Aufgaben kommen.",
                ) {
                    EtaDurationPicker(
                        value = setup.breakPerTwoHours,
                        onValueChange = { amount -> onChange { it.copy(breakPerTwoHours = amount) } },
                        step = 5.minutes,
                        minimum = 5.minutes,
                    )
                }
                EtaField(
                    label = "Längste Strecke ohne Pause",
                    hint = "Liegen zwischen zwei Pausen — oder vor der ersten, nach der " +
                        "letzten — mehr Aufgaben als das, weist der Planer darauf hin.",
                ) {
                    EtaDurationPicker(
                        value = setup.breakMaxStretch,
                        onValueChange = { length -> onChange { it.copy(breakMaxStretch = length) } },
                        step = 30.minutes,
                        minimum = 1.hours,
                    )
                }
            }
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
 * The charge for hours nothing was planned into: whether there is one, and how
 * much.
 *
 * Applied at once, like the switches it sits among. It never goes to zero by the
 * stepper — "nothing" is what the checkbox is for, and a rate of zero with the
 * box ticked would be a switch that looks on and does nothing.
 */
@Composable
private fun UnplannedPenaltyRows(setup: UserSetup, onChange: OnSetupChange) {
    Column(
        modifier = Modifier.padding(start = EtaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
    ) {
        CheckRow(
            checked = setup.unplannedPenalty,
            onCheckedChange = { on -> onChange { it.copy(unplannedPenalty = on) } },
            label = "Abzug für unverplante Zeit",
            hint = "Zwei Stunden am Tag dürfen leer bleiben; jede weitere wache Stunde " +
                "ohne Plan kostet Punkte. Geplante Freizeit zählt als verplant.",
        )
        if (setup.unplannedPenalty) {
            EtaField(
                label = "Punkte je unverplanter Stunde",
                hint = "Ab der dritten Stunde.",
            ) {
                EtaStepper(
                    value = formatPoints(setup.unplannedPenaltyPerHour),
                    valueWidth = 72.dp,
                    onDecrement = {
                        onChange {
                            it.copy(
                                unplannedPenaltyPerHour =
                                    (it.unplannedPenaltyPerHour - CANCELLATION_STEP)
                                        .coerceAtLeast(CANCELLATION_STEP),
                            )
                        }
                    },
                    onIncrement = {
                        onChange {
                            it.copy(unplannedPenaltyPerHour = it.unplannedPenaltyPerHour + CANCELLATION_STEP)
                        }
                    },
                )
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

/**
 * The tutorials, all of them.
 *
 * Says before the buttons what they will and will not touch, because a tutorial
 * that has the user add and tick off tasks reads like something that might do
 * so to their own.
 */
@Composable
private fun TutorialBox(onStart: (TutorialId?) -> Unit) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(text = "Tutorial", style = EtaTheme.typography.heading)
            EtaText(
                text = "Führt dich noch einmal durch einen simulierten Tag. Alle Tutorials " +
                    "arbeiten nur mit Übungsdaten — deine Aufgaben, Punkte und " +
                    "Einstellungen bleiben unberührt.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            EtaButton(
                text = "Tutorial wiederholen",
                style = EtaButtonStyle.Secondary,
                onClick = { onStart(null) },
            )
        }
    }
    FeatureTutorialsBox(onStart = onStart)
}

/**
 * The tutorials about one thing each: the Extras and the three Advanced Features.
 *
 * Mounted twice — on the tutorial page, and under the switches of the Advanced
 * Features, which is where the question "what is this?" is actually asked.
 */
@Composable
private fun FeatureTutorialsBox(onStart: (TutorialId?) -> Unit) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Tutorials zu einzelnen Themen", style = EtaTheme.typography.heading)
            EtaText(
                text = "Kurz, mit Übungsdaten, und unabhängig davon, ob das Feature bei " +
                    "dir eingeschaltet ist.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            TutorialId.further.forEach { id ->
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                    EtaButton(
                        text = id.title,
                        style = EtaButtonStyle.Secondary,
                        onClick = { onStart(id) },
                    )
                    EtaText(
                        text = id.summary,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            }
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
            // As many hours as the week has: one, the weekend's beside it, or
            // one per weekday, which is too many to list in a sentence.
            val hours = when (setup.nightMode) {
                NightMode.SAME -> "um ${setup.wakeTime.formatClock()} Uhr"
                NightMode.WEEKEND -> "um ${setup.wakeTime.formatClock()}, am Wochenende um " +
                    "${setup.weekendNight?.wake?.formatClock()} Uhr"
                NightMode.PER_DAY -> "zur Aufstehzeit des jeweiligen Tages"
            }
            EtaText(
                text = "Eta weckt dich $hours — dieselbe " +
                    "Aufstehzeit, die oben im Schlaf steht. Der Weckruf klingelt über den " +
                    "Sperrbildschirm und läutet, bis du ihn beendest oder zehn Minuten " +
                    "schlummerst.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaChoice(
                options = listOf(
                    false to "Kein Weckruf",
                    true to if (setup.nightMode == NightMode.SAME) {
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
            val muted = remember(probe) { EtaSound.isRingerMuted(context) }
            EtaText(
                text = when {
                    !dndOn && muted ->
                        "Das Handy ist gerade stumm oder auf Vibration — solange bleiben " +
                            "Etas Töne still. Benachrichtigungen erscheinen trotzdem."
                    dndOn && ignoreDnd ->
                        "„Nicht stören“ ist gerade an — Etas Töne spielen trotzdem, " +
                            "solange das Handy Wecker durchlässt."
                    dndOn ->
                        "„Nicht stören“ ist gerade an — solange bleiben Etas Töne still. " +
                            "Benachrichtigungen erscheinen trotzdem."
                    ignoreDnd ->
                        "Etas Töne laufen über die Wecker-Lautstärke, unabhängig von der " +
                            "Lautstärke für Benachrichtigungen, auch bei „Nicht stören“. Steht " +
                            "das Handy stumm oder auf Vibration, bleiben sie still."
                    else ->
                        "Etas Töne laufen über die Wecker-Lautstärke, unabhängig von der " +
                            "Lautstärke für Benachrichtigungen. Still sind sie bei „Nicht stören“ " +
                            "und wenn das Handy stumm oder auf Vibration steht."
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
