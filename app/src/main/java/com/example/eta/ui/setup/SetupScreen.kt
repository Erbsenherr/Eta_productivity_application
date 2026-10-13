package com.example.eta.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.eta.domain.recurrence.recurringOverlaps
import com.example.eta.ui.settings.CalendarSettingsBox
import com.example.eta.ui.settings.CalendarSettingsViewModel
import com.example.eta.domain.setup.SetupConflict
import com.example.eta.domain.setup.SetupPart
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaProgressBar
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch

/**
 * The pages, in order.
 *
 * Not an enum any more: since step 46 the list is **made from the answers**. The
 * fixed pages come first — the welcome with Google, the night, the planning
 * times, free time — then the checklist of standing tasks, and after it one
 * page for every box ticked there: the five the questionnaire has pages of its
 * own for, and the user's own. The progress line, the pager and both buttons
 * count this one list.
 */
private sealed interface SetupPage {
    val key: String

    data object Welcome : SetupPage { override val key = "welcome" }
    data object Sleep : SetupPage { override val key = "sleep" }
    data object Planning : SetupPage { override val key = "planning" }
    data object FreeTime : SetupPage { override val key = "freetime" }
    data object Tasks : SetupPage { override val key = "tasks" }
    data class BuiltIn(val part: SetupPart) : SetupPage { override val key = "part:${part.name}" }
    data class Custom(val id: String) : SetupPage { override val key = "custom:$id" }
    data object Summary : SetupPage { override val key = "summary" }
}

/** The five the checklist offers, in the order their pages follow it. */
private val CHECKLIST_PARTS = listOf(
    SetupPart.MEALS, SetupPart.HOUSEKEEPING, SetupPart.SPORT, SetupPart.MINDFULNESS, SetupPart.WORK,
)

private fun pagesFor(skipped: Set<SetupPart>, custom: List<CustomTask>): List<SetupPage> =
    listOf(SetupPage.Welcome, SetupPage.Sleep, SetupPage.Planning, SetupPage.FreeTime, SetupPage.Tasks) +
        CHECKLIST_PARTS.filter { it !in skipped }.map { SetupPage.BuiltIn(it) } +
        custom.map { SetupPage.Custom(it.id) } +
        SetupPage.Summary

/**
 * The questionnaire that runs before the app is usable.
 *
 * It swipes rather than navigates, in the spirit of the rest of the app, and the
 * buttons move the same pager — so a user who prefers tapping and one who prefers
 * flicking end up in the same place.
 *
 * Its last act is not a page: "Fertig" lays the week down and hands over to the
 * week as a timetable — see `SetupWeekReview` — where what collides can be
 * dragged apart instead of typed apart.
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    modifier: Modifier = Modifier,
    /**
     * The Google connection, offered on the first page so appointments can be
     * read from the first planning phase on. Null leaves it out.
     */
    calendarViewModel: CalendarSettingsViewModel? = null,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val outlook by viewModel.outlook.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val skipped by viewModel.skipped.collectAsStateWithLifecycle()
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val morningSteps by viewModel.morningSteps.collectAsStateWithLifecycle()
    val pages = pagesFor(skipped, custom)
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val current = pages[pagerState.currentPage.coerceIn(0, pages.lastIndex)]

    EtaScreen(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            SetupHeader(
                stepNumber = pagerState.currentPage + 1,
                stepCount = pages.size,
                modifier = Modifier.padding(
                    start = EtaTheme.spacing.lg,
                    end = EtaTheme.spacing.lg,
                    top = EtaTheme.spacing.lg,
                ),
            )

            HorizontalPager(
                state = pagerState,
                // By what the page is, not where it stands: ticking a box in
                // the checklist inserts pages behind it, and a page's scroll
                // position and half-typed fields must stay with that page.
                key = { pages.getOrNull(it)?.key ?: it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { index ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(EtaTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
                ) {
                    when (val page = pages.getOrNull(index)) {
                        // The welcome is the top of the first page, not a page
                        // of its own; under it, the one thing worth doing
                        // before any question.
                        SetupPage.Welcome -> {
                            WelcomeStep()
                            if (calendarViewModel != null) {
                                EtaText(
                                    text = "Wenn du magst, verbinde zuerst deinen Google Kalender. " +
                                        "Deine Termine werden dir dann in den Planungsphasen " +
                                        "angeboten. Das geht auch später in den Einstellungen.",
                                    style = EtaTheme.typography.body,
                                    color = EtaTheme.colors.textSecondary,
                                )
                                CalendarSettingsBox(calendarViewModel)
                            }
                        }

                        SetupPage.Sleep -> SleepStep(
                            draft = draft,
                            onChange = viewModel::update,
                            morningSteps = morningSteps,
                            onMorningSteps = viewModel::setMorningSteps,
                        )

                        SetupPage.Planning -> PlanningStep(draft, viewModel::update)

                        SetupPage.FreeTime -> if (SetupPart.FREE_TIME in skipped) {
                            SkippedStep(title = "Freie Zeit", onAnswer = { viewModel.unskip(SetupPart.FREE_TIME) })
                        } else {
                            FreeTimeStep(draft, viewModel::update)
                        }

                        SetupPage.Tasks -> RecurringTasksStep(
                            skipped = skipped,
                            custom = custom.map { it.id to it.name },
                            onToggle = { part, on -> if (on) viewModel.unskip(part) else viewModel.skip(part) },
                            onAdd = viewModel::addCustom,
                            onRemove = viewModel::removeCustom,
                        )

                        is SetupPage.BuiltIn -> when (page.part) {
                            SetupPart.MEALS -> MealsStep(draft, viewModel::update)
                            SetupPart.HOUSEKEEPING -> HousekeepingStep(draft, viewModel::update)
                            SetupPart.SPORT -> SportStep(draft, viewModel::update)
                            SetupPart.MINDFULNESS -> MindfulnessStep(draft, viewModel::update)
                            SetupPart.WORK -> WorkStep(draft, viewModel::update)
                            SetupPart.FREE_TIME -> Unit
                        }

                        is SetupPage.Custom -> custom.firstOrNull { it.id == page.id }?.let { task ->
                            val overlaps = remember(task.attributes, outlook.generated) {
                                recurringOverlaps(task.attributes.slots(), outlook.generated)
                            }
                            CustomTaskStep(
                                name = task.name,
                                attributes = task.attributes,
                                overlaps = overlaps,
                                onName = { name -> viewModel.updateCustom(task.id) { it.copy(name = name) } },
                                onAttributes = { answers ->
                                    viewModel.updateCustom(task.id) { it.copy(attributes = answers) }
                                },
                            )
                        }

                        SetupPage.Summary -> SummaryStep(outlook)
                        null -> Unit
                    }

                    // The concept asks for double bookings to be flagged as they
                    // arise, so the warning travels along instead of waiting for
                    // the summary.
                    ConflictBox(conflicts = outlook.conflicts)

                    Spacer(Modifier.size(EtaTheme.spacing.xl))
                }
            }

            SetupFooter(
                isFirst = pagerState.currentPage == 0,
                isLast = pagerState.currentPage >= pages.lastIndex,
                saving = saving,
                // Free time keeps its own way out; the standing tasks are
                // chosen on the checklist, so their pages have none.
                onSkip = if (current == SetupPage.FreeTime && SetupPart.FREE_TIME !in skipped) {
                    {
                        viewModel.skip(SetupPart.FREE_TIME)
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                } else {
                    null
                },
                onBack = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                },
                onNext = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                onFinish = viewModel::finish,
            )
        }
    }
}

/**
 * The questionnaire's last screen: the standing week it has just laid down, as
 * the timetable the dashboard has under "Woche → Generell" — with a box at the
 * foot that explains it, page by page, the way the tutorial explains a screen.
 *
 * Here rather than as another page of questions, because this is where a week
 * is put right: what collides is dragged apart, not typed apart. [timetable] is
 * handed that box as its footer and draws the rest.
 */
@Composable
fun SetupWeekReview(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    timetable: @Composable (footer: @Composable () -> Unit) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    val notes = WEEK_REVIEW_NOTES
    val note = notes[step.coerceIn(0, notes.lastIndex)]

    EtaScreen(modifier = modifier) {
        timetable {
            EtaSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EtaTheme.spacing.lg),
                borderColor = EtaTheme.colors.accent,
                color = EtaTheme.colors.surfaceRaised,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    EtaText(
                        text = "${note.first} · ${step + 1} von ${notes.size}",
                        style = EtaTheme.typography.label,
                        color = EtaTheme.colors.accent,
                    )
                    EtaText(
                        text = note.second,
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        if (step > 0) {
                            EtaButton(
                                text = "Zurück",
                                style = EtaButtonStyle.Secondary,
                                onClick = { step -= 1 },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (step < notes.lastIndex) {
                            EtaButton(text = "Weiter", onClick = { step += 1 })
                        } else {
                            EtaButton(text = "Einrichtung abschließen", onClick = onDone)
                        }
                    }
                }
            }
        }
    }
}

/** What the box at the foot of the week review says, a heading and a paragraph each. */
private val WEEK_REVIEW_NOTES = listOf(
    "Dein Wochenplan" to
        "Das ist deine feste Woche, so wie du sie gerade eingerichtet hast: ein Wochentag " +
        "pro Seite, die Nacht schattiert. Wische zur Seite oder tippe oben auf einen Tag. " +
        "Tippst du einen Block an, zeigt er seinen Namen.",
    "Das Schloss" to
        "Oben rechts steht »Gesperrt«. Tippe auf das Schloss, um die wiederkehrenden Tasks " +
        "zu entsperren: Dann verschiebst du sie mit dem Finger und ziehst sie mit zwei " +
        "Fingern länger oder kürzer. So löst du auch Überschneidungen aus der Einrichtung. " +
        "Gedrückt halten öffnet eine Task zum Bearbeiten; auf einer freien Stelle legt es " +
        "eine neue an.",
    "Aktuell und Generell" to
        "»Generell« ist dieser feste Plan — was du hier änderst, gilt für alle künftigen " +
        "Wochen. »Aktuell« zeigt später die laufende Woche mit allem, was dazukommt, und " +
        "ändert nur den einen Termin. Jetzt ist »Aktuell« ausgegraut: Dort lassen sich nur " +
        "bestehende Termine bearbeiten, und die gibt es noch nicht. »Termine anzeigen« " +
        "blendet übernommene Google-Termine ein.",
    "Später wiederfinden" to
        "Diese Ansicht findest du jederzeit im Dashboard unter »Woche«. Passt alles, " +
        "schließe die Einrichtung ab — ändern kannst du es immer.",
)

@Composable
private fun SetupHeader(stepNumber: Int, stepCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        EtaText(
            text = "Einrichtung · Schritt $stepNumber von $stepCount",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        EtaProgressBar(fraction = stepNumber.toFloat() / stepCount)
    }
}

/** Every double booking the answers so far produce, named in plain German. */
@Composable
private fun ConflictBox(conflicts: List<SetupConflict>, modifier: Modifier = Modifier) {
    if (conflicts.isEmpty()) return

    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = if (conflicts.size == 1) {
                    "Eine Doppeltbelegung"
                } else {
                    "${conflicts.size} Doppeltbelegungen"
                },
                style = EtaTheme.typography.heading,
                color = EtaTheme.colors.warning,
            )
            conflicts.take(MAX_SHOWN_CONFLICTS).forEach { conflict ->
                EtaText(
                    text = "${conflict.weekday.formatLong()}: " +
                        "${conflict.first.label} und ${conflict.second.label} " +
                        "überschneiden sich um ${conflict.toMinute - conflict.fromMinute} min.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
            }
            if (conflicts.size > MAX_SHOWN_CONFLICTS) {
                EtaText(
                    text = "… und ${conflicts.size - MAX_SHOWN_CONFLICTS} weitere.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            EtaText(
                text = "Du kannst trotzdem fortfahren und das später lösen: am Ende der " +
                    "Einrichtung siehst du deine Woche als Stundenplan und kannst die " +
                    "Tasks dort einfach auseinanderziehen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
}

private const val MAX_SHOWN_CONFLICTS = 6

@Composable
private fun SetupFooter(
    isFirst: Boolean,
    isLast: Boolean,
    saving: Boolean,
    onSkip: (() -> Unit)?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(EtaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
    ) {
    // On a line of its own: three buttons in one row do not fit a phone, and the
    // last one is then laid out at no width at all.
    if (onSkip != null) {
        EtaButton(
            text = "Überspringen",
            style = EtaButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSkip,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
    ) {
        if (!isFirst) {
            EtaButton(
                text = "Zurück",
                style = EtaButtonStyle.Secondary,
                onClick = onBack,
            )
        }
        Spacer(Modifier.weight(1f))
        if (isLast) {
            EtaButton(
                text = if (saving) "Wird angelegt …" else "Anlegen und Woche ansehen",
                enabled = !saving,
                onClick = onFinish,
            )
        } else {
            EtaButton(text = "Weiter", onClick = onNext)
        }
    }
    }
}

/** A page the user left out, with the way back in. */
@Composable
private fun SkippedStep(title: String, onAnswer: () -> Unit) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = title, style = EtaTheme.typography.title)
            EtaText(
                text = "Übersprungen — dafür wird nichts angelegt. Du kannst es später " +
                    "jederzeit im Reiter Listen als wiederkehrende Aufgabe nachholen.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            EtaButton(
                text = "Doch beantworten",
                style = EtaButtonStyle.Secondary,
                onClick = onAnswer,
            )
        }
    }
}
