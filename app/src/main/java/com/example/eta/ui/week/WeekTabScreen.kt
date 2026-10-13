package com.example.eta.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.planning.TimelineDraft
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.nightDateAt
import com.example.eta.domain.setup.allNights
import com.example.eta.domain.setup.nightEndingOn
import com.example.eta.ui.planner.NightEditDialog
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.isOwnedBySettings
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatVeryShort
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.lists.RecurringCreateDialog
import com.example.eta.ui.lists.RecurringGroupEditDialog
import com.example.eta.ui.planner.BlockEditDialog
import com.example.eta.ui.planner.DayPlannerViewModel
import com.example.eta.ui.planner.DayTimeline
import com.example.eta.ui.planner.HOUR_HEIGHT
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/** The hour the timetable opens on: early enough that the morning is in sight. */
private const val OPENING_HOUR = 6.5f

/**
 * The "Woche" page of the dashboard: one weekday at a time, drawn as a
 * timetable, Monday to Sunday by swiping.
 *
 * The day planner's own [DayTimeline] draws it, so a block here looks and
 * behaves like a block there — tap for the name, hold to edit, and once the
 * lock is open, drag and pinch. What is written depends on the view: the
 * **standing task** in "Generell", **this week's one occurrence** in "Aktuell".
 * See [WeekTabViewModel].
 *
 * Not a screen of its own: it is mounted under the dashboard's three tabs.
 */
@Composable
fun WeekTabScreen(
    viewModel: WeekTabViewModel,
    modifier: Modifier = Modifier,
    /**
     * The day planner's own view model, for the window a long press opens in
     * "Aktuell" — the planner's "Bearbeiten", wired to the one implementation of
     * edit, call off and copy, as the dashboard's list is. Null leaves that
     * long press with a sentence instead.
     */
    plannerViewModel: DayPlannerViewModel? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var viewName by rememberSaveable { mutableStateOf(WeekView.CURRENT.name) }
    val view = WeekView.valueOf(viewName)
    // Locked again on every visit: the open lock is a state one is in for a
    // moment, and a timetable found unlocked a week later is one that gets
    // rearranged by a thumb on the way past.
    var unlocked by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = WEEK.indexOf(state.today.dayOfWeek)) { WEEK.size }
    val scope = rememberCoroutineScope()
    val day = WEEK[pagerState.currentPage]

    // One scroll position for all seven days: swiping to Wednesday keeps the
    // hour that was on screen on Tuesday, which is what makes it a timetable.
    val openingPx = with(LocalDensity.current) { (HOUR_HEIGHT * OPENING_HOUR).roundToPx() }
    val scrollState = rememberScrollState(initial = openingPx)

    // The box for a new task, and the weekday it stands on.
    var draft by remember { mutableStateOf<Pair<DayOfWeek, TimelineDraft>?>(null) }
    var creating by remember { mutableStateOf<Pair<DayOfWeek, TimelineDraft>?>(null) }
    // Held as an id and resolved against the tasks as they are now.
    var editingId by remember { mutableStateOf<String?>(null) }
    // The same for one occurrence, in "Aktuell": a block id.
    var editingBlockId by remember { mutableStateOf<String?>(null) }
    // The weekday whose night a long press landed in — the day it ends on.
    var editingNight by remember { mutableStateOf<DayOfWeek?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = EtaTheme.spacing.lg, end = EtaTheme.spacing.lg, top = EtaTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
        ) {
            WeekViewSwitch(selected = view, onSelect = { viewName = it.name })
            InfoButton(onClick = { showInfo = true })
            Spacer(Modifier.weight(1f))
            LockButton(unlocked = unlocked, onClick = { unlocked = !unlocked })
        }

        WeekdayStrip(
            selected = day,
            today = state.today.dayOfWeek.takeIf { view == WeekView.CURRENT },
            onSelect = { chosen -> scope.launch { pagerState.animateScrollToPage(WEEK.indexOf(chosen)) } },
        )

        Column(Modifier.padding(horizontal = EtaTheme.spacing.lg, vertical = EtaTheme.spacing.xs)) {
            EtaText(
                text = when (view) {
                    WeekView.GENERAL -> day.formatLong()
                    WeekView.CURRENT -> "${day.formatLong()}, ${state.dateOf(day).formatLong().substringAfter(", ")}"
                },
                style = EtaTheme.typography.heading,
            )
            EtaText(
                text = if (unlocked && view == WeekView.CURRENT) {
                    "Entsperrt: wiederkehrende Tasks verschieben, mit zwei Fingern länger oder " +
                        "kürzer ziehen. Geändert wird nur dieser eine Termin."
                } else if (unlocked) {
                    "Entsperrt: wiederkehrende Tasks verschieben, mit zwei Fingern länger oder " +
                        "kürzer ziehen. Geändert wird die Task selbst — alle künftigen Termine."
                } else {
                    "Tippen zeigt den Namen, gedrückt halten bearbeitet. Leere Stelle gedrückt " +
                        "halten: neue wiederkehrende Task."
                },
                style = EtaTheme.typography.caption,
                color = if (unlocked) EtaTheme.colors.accent else EtaTheme.colors.textMuted,
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val pageDay = WEEK[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                DayTimeline(
                    blocks = state.blocksOn(pageDay, view, unlocked),
                    sleep = state.sleepOn(pageDay, view),
                    // Read off the standing nights in both views: what the
                    // window changes is the weekday's night, whatever a single
                    // date in "Aktuell" happens to show.
                    onSleepLongPress = { minute ->
                        val date = state.dateOf(pageDay)
                        editingNight = state.setup?.nightDateAt(date, minute)?.dayOfWeek
                            ?: state.setup?.nightDateAt(date, minute, state.nightOverrides)?.dayOfWeek
                    },
                    sleepLabels = true,
                    editable = true,
                    subtasks = state.subtasks,
                    movable = {
                        unlocked && when (view) {
                            WeekView.CURRENT -> state.canMoveOccurrence(it)
                            WeekView.GENERAL -> state.canReschedule(it)
                        }
                    },
                    onBlockLongPress = { entry, _ ->
                        when {
                            !state.isStanding(entry) -> viewModel.refuseOneOff(entry.item.name)
                            // "Aktuell": this one occurrence, in the planner's
                            // own window — the settings' two tasks included,
                            // since a single day is theirs to change as well.
                            view == WeekView.CURRENT && plannerViewModel != null -> when {
                                state.canMoveOccurrence(entry) -> editingBlockId = entry.block.id
                                entry.block.isOpen -> viewModel.refusePast(entry.item.name)
                                else -> Unit
                            }

                            isOwnedBySettings(entry.item.id) -> viewModel.refuseSettingsOwned(entry.item.name)
                            else -> editingId = entry.item.id
                        }
                    },
                    // A block id comes back, and in "Aktuell" that is an
                    // occurrence's: the task behind it is looked up in the day
                    // as it stands now, not in whatever the gesture closed over.
                    onBlockMoved = { blockId, minute ->
                        if (view == WeekView.CURRENT) {
                            viewModel.moveOccurrence(blockId, startMinute = minute)
                        } else {
                            state.blocksOn(pageDay, view, unlocked)
                                .firstOrNull { it.block.id == blockId }
                                ?.let { viewModel.reschedule(it.item.id, pageDay, startMinute = minute) }
                        }
                    },
                    onBlockResized = if (unlocked) {
                        { blockId, startMinute, durationMinutes ->
                            if (view == WeekView.CURRENT) {
                                viewModel.moveOccurrence(blockId, startMinute, durationMinutes)
                            } else {
                                state.blocksOn(pageDay, view, unlocked)
                                    .firstOrNull { it.block.id == blockId }
                                    ?.let { viewModel.reschedule(it.item.id, pageDay, startMinute, durationMinutes) }
                            }
                        }
                    } else {
                        null
                    },
                    onEmptyLongPress = { minute -> draft = pageDay to TimelineDraft.at(minute) },
                    draft = draft?.takeIf { it.first == pageDay }?.second,
                    onDraftChange = { draft = pageDay to it },
                )
            }
        }

        val shaping = draft
        if (shaping != null) {
            DraftBar(
                day = shaping.first,
                draft = shaping.second,
                onCancel = { draft = null },
                onConfirm = {
                    creating = shaping
                    draft = null
                },
            )
        }
        notice?.let { NoticeBar(text = it, onDismiss = viewModel::dismissNotice) }
    }

    if (showInfo) WeekInfoDialog(onDismiss = { showInfo = false })

    editingNight?.let { wakeDay ->
        val setup = state.setup
        if (setup == null) {
            editingNight = null
        } else {
            NightEditDialog(
                title = "Nacht auf ${wakeDay.formatLong()}",
                scope = "Gilt ab jetzt für jede Nacht auf ${wakeDay.formatLong()} — und nur für " +
                    "diesen Wochentag. Morgenroutine und »Bettfertig machen« ziehen mit; " +
                    "heute, und morgen, wenn die Planung schon bestätigt ist, bleiben wie geplant.",
                night = setup.nightEndingOn(wakeDay),
                wakeDay = wakeDay,
                // Against the other six: this one is about to be the odd one out.
                compareWith = (setup.allNights() - wakeDay).values,
                onDismiss = { editingNight = null },
                onSave = { sleep, wake ->
                    viewModel.saveNight(wakeDay, sleep, wake)
                    editingNight = null
                },
            )
        }
    }

    pending?.let { request ->
        if (request.clashes == null) {
            ScopeDialog(
                request = request,
                onOnlyThisDay = { viewModel.answerScope(allDays = false) },
                onAllDays = { viewModel.answerScope(allDays = true) },
                onDismiss = viewModel::dismissPending,
            )
        } else {
            ClashDialog(
                request = request,
                clashes = request.clashes,
                onMoveAnyway = { viewModel.answerClashes(moveAnyway = true) },
                onLeaveOut = { viewModel.answerClashes(moveAnyway = false) },
                onDismiss = viewModel::dismissPending,
            )
        }
    }

    editingBlockId?.let { id ->
        // Resolved against the week as it stands: called off or ticked from
        // elsewhere while the window stood open, there is nothing left to edit.
        val entry = state.occurrence(id)?.takeIf { state.canMoveOccurrence(it) }
        if (entry == null || plannerViewModel == null) {
            editingBlockId = null
        } else {
            BlockEditDialog(
                entry = entry,
                subtasks = state.subtasks[entry.item.id].orEmpty(),
                foldCandidates = state.foldable.foldCandidatesExcept(entry.item.id),
                onSubtasks = { plannerViewModel.saveSubtasks(entry.block.id, it) },
                onDismiss = { editingBlockId = null },
                onSave = { n, cat, start, dur, bNote, iNote, travel, back, pause, sound, rate, flex ->
                    plannerViewModel.edit(
                        entry, n, cat, start, dur, bNote, iNote, travel, back, pause, sound, rate, flex,
                    )
                    editingBlockId = null
                },
                // A standing occurrence cannot be taken off its day; the button is not drawn.
                onRemove = { editingBlockId = null },
                onCancelBlock = {
                    plannerViewModel.cancel(entry)
                    editingBlockId = null
                },
                onCancelExcused = {
                    plannerViewModel.cancel(entry, forceMajeure = true)
                    editingBlockId = null
                },
                onCopyToWeek = {
                    plannerViewModel.copyToWeek(entry)
                    editingBlockId = null
                },
                // Only today is a promise already being lived; the days ahead are still planning.
                cancellationCosts = entry.block.date == state.today,
            )
        }
    }

    editingId?.let { id ->
        val group = state.groupOf(id)
        if (group == null) {
            // Ended or retired while the window stood open: nothing left to edit.
            editingId = null
        } else {
            RecurringGroupEditDialog(
                group = group,
                definitions = state.definitions,
                setup = state.setup,
                subtasks = state.subtasks[group.representative.id].orEmpty(),
                foldCandidates = state.foldable.foldCandidatesExcept(*group.ids.toTypedArray()),
                onDismiss = { editingId = null },
                onSave = { name, note, attributes ->
                    viewModel.saveGroup(group, name, note, attributes)
                    editingId = null
                },
            )
        }
    }

    creating?.let { (onDay, box) ->
        RecurringCreateDialog(
            definitions = state.definitions,
            setup = state.setup,
            foldCandidates = state.foldable.foldCandidatesExcept(),
            // The weekday the box was drawn on is ticked; the form's own
            // weekday row is where more are added.
            initial = RecurringAttributes(
                weekdays = setOf(onDay),
                startTime = minuteToLocalTime(box.startMinute),
                duration = box.duration,
            ),
            onDismiss = { creating = null },
            onCreate = { name, note, attributes ->
                viewModel.create(name, note, attributes)
                creating = null
            },
        )
    }
}

/** "Aktuell" or "Generell": two halves of one pill, the chosen one filled. */
@Composable
private fun WeekViewSwitch(selected: WeekView, onSelect: (WeekView) -> Unit) {
    Row(
        modifier = Modifier
            .background(EtaTheme.colors.surface, EtaTheme.shapes.pill)
            .border(1.dp, EtaTheme.colors.border, EtaTheme.shapes.pill)
            .padding(2.dp),
    ) {
        WeekView.entries.forEach { option ->
            val chosen = option == selected
            val interactionSource = remember { MutableInteractionSource() }
            EtaText(
                text = option.label,
                style = EtaTheme.typography.label,
                color = if (chosen) EtaTheme.colors.onAccent else EtaTheme.colors.textSecondary,
                modifier = Modifier
                    .background(
                        color = if (chosen) EtaTheme.colors.accent else EtaTheme.colors.surface,
                        shape = EtaTheme.shapes.pill,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = EtaTheme.spacing.md, vertical = EtaTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    EtaText(
        text = "ⓘ",
        style = EtaTheme.typography.title,
        color = EtaTheme.colors.accent,
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(EtaTheme.spacing.xs)
            .semantics { contentDescription = "Was zeigen Aktuell und Generell?" },
    )
}

/**
 * The lock over the standing tasks, drawn rather than loaded — there is no icon
 * set in this project. Shut, the shackle sits in the body on both sides; open,
 * it is lifted and one leg hangs free, in the accent colour.
 */
@Composable
private fun LockButton(unlocked: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val color = if (unlocked) EtaTheme.colors.accent else EtaTheme.colors.textSecondary
    // `label`, not `contentDescription`: inside `semantics` the bare name is the
    // receiver's own property, whose getter throws.
    val label = if (unlocked) "Wiederkehrende Tasks sperren" else "Wiederkehrende Tasks entsperren"

    Row(
        modifier = Modifier
            .background(
                color = if (unlocked) EtaTheme.colors.accentSoft else EtaTheme.colors.surface,
                shape = EtaTheme.shapes.pill,
            )
            .border(1.dp, if (unlocked) EtaTheme.colors.accent else EtaTheme.colors.border, EtaTheme.shapes.pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = EtaTheme.spacing.md, vertical = EtaTheme.spacing.sm)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.1f
            val lift = if (unlocked) h * 0.12f else 0f

            // The body.
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.18f, h * 0.48f),
                size = Size(w * 0.64f, h * 0.44f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                style = Stroke(width = stroke),
            )
            // The shackle: a half circle, and a leg down each side of it.
            val left = w * 0.32f
            val right = w * 0.68f
            val top = h * 0.12f - lift
            val bend = top + (right - left) / 2f
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(right - left, right - left),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawLine(color, Offset(left, bend), Offset(left, h * 0.48f), stroke, StrokeCap.Round)
            drawLine(
                color = color,
                start = Offset(right, bend),
                end = Offset(right, if (unlocked) bend + h * 0.08f else h * 0.48f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        EtaText(
            text = if (unlocked) "Offen" else "Gesperrt",
            style = EtaTheme.typography.caption,
            color = color,
        )
    }
}

/** Mo to So, the day on screen filled and today marked — and each a way straight there. */
@Composable
private fun WeekdayStrip(
    selected: DayOfWeek,
    today: DayOfWeek?,
    onSelect: (DayOfWeek) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EtaTheme.spacing.lg, vertical = EtaTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
    ) {
        WEEK.forEach { weekday ->
            val chosen = weekday == selected
            val interactionSource = remember { MutableInteractionSource() }
            EtaText(
                text = weekday.formatVeryShort(),
                style = if (chosen || weekday == today) EtaTheme.typography.label else EtaTheme.typography.caption,
                color = when {
                    chosen -> EtaTheme.colors.onAccent
                    weekday == today -> EtaTheme.colors.accent
                    else -> EtaTheme.colors.textSecondary
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (chosen) EtaTheme.colors.accent else EtaTheme.colors.surface,
                        shape = EtaTheme.shapes.small,
                    )
                    .border(
                        width = 1.dp,
                        color = if (chosen || weekday == today) EtaTheme.colors.accent else EtaTheme.colors.border,
                        shape = EtaTheme.shapes.small,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelect(weekday) },
                    )
                    .padding(vertical = EtaTheme.spacing.sm),
            )
        }
    }
}

/** What stands under the timetable while a new task's box is being shaped. */
@Composable
private fun DraftBar(
    day: DayOfWeek,
    draft: TimelineDraft,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(EtaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        EtaText(
            text = "Neue Task am ${day.formatLong()}, " +
                "${minuteToLocalTime(draft.startMinute).formatClock()} · ${draft.duration.formatShort()}. " +
                "Box verschieben, mit zwei Fingern länger oder kürzer ziehen.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onCancel)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Bestätigen", onClick = onConfirm)
        }
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    EtaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(EtaTheme.spacing.lg),
        borderColor = EtaTheme.colors.warning,
        contentPadding = EtaTheme.spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtaText(
                text = text,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            EtaButton(text = "OK", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

@Composable
private fun WeekInfoDialog(onDismiss: () -> Unit) {
    EtaDialog(title = "Aktuell und Generell", onDismiss = onDismiss) {
        EtaText(
            text = "»Aktuell« zeigt die laufende Woche mit allen Terminen: was für heute und " +
                "— soweit schon geplant — für morgen eingeplant ist, die wiederkehrenden " +
                "Tasks der übrigen Tage und alles, was aus Google Calendar importiert wurde.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaText(
            text = "»Generell« zeigt nur deinen festen Wochenplan: die wiederkehrenden Tasks " +
                "und Routinen jedes Wochentags mit Schlafens- und Aufstehzeit — ohne " +
                "einmalig eingeplante Aufgaben oder Termine.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaText(
            text = "Mit dem Schloss entsperrst du die wiederkehrenden Tasks. Dann lassen sie " +
                "sich verschieben und mit zwei Fingern verlängern oder verkürzen; gedrückt " +
                "halten öffnet das Bearbeiten-Fenster.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaText(
            text = "In »Aktuell« ändert das nur den einen Termin dieser Woche — wie im " +
                "Tagesplaner. Der Donnerstag dieser Woche ist dann anders, alle anderen " +
                "Donnerstage bleiben. In »Generell« ändert es die Task selbst, also alle " +
                "künftigen Termine; heute, und morgen, wenn die Planung schon bestätigt " +
                "ist, bleiben dabei wie geplant.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaButton(text = "Verstanden", onClick = onDismiss)
    }
}

/** "Nur Dienstag" or every day of the task — asked after a drag or a pinch. */
@Composable
private fun ScopeDialog(
    request: PendingReschedule,
    onOnlyThisDay: () -> Unit,
    onAllDays: () -> Unit,
    onDismiss: () -> Unit,
) {
    EtaDialog(title = "Für welche Tage?", onDismiss = onDismiss) {
        EtaText(
            text = "»${request.name}« steht an mehreren Tagen (${formatWeekdays(request.groupDays)}). " +
                "Neu: ${request.summary()}.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaText(
            text = "Gilt für künftige Termine. Heute — und morgen, wenn die Planung schon " +
                "bestätigt ist — bleibt wie geplant.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        // Three answers, one under the other: a row of three is wider than a phone.
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = "Nur ${request.day.formatLong()}",
                onClick = onOnlyThisDay,
                modifier = Modifier.fillMaxWidth(),
            )
            EtaButton(
                text = "Alle Tage (${formatWeekdays(request.groupDays)})",
                onClick = onAllDays,
                modifier = Modifier.fillMaxWidth(),
            )
            EtaButton(
                text = "Abbrechen",
                style = EtaButtonStyle.Secondary,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The other days on which the new slot is taken, and what to do about them. */
@Composable
private fun ClashDialog(
    request: PendingReschedule,
    clashes: Map<DayOfWeek, List<String>>,
    onMoveAnyway: () -> Unit,
    onLeaveOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    EtaDialog(title = "Terminkonflikt", onDismiss = onDismiss) {
        EtaText(
            text = "Mit ${request.summary()} überschneidet sich »${request.name}« an diesen Tagen:",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            WEEK.filter { it in clashes }.forEach { weekday ->
                EtaText(
                    text = "${weekday.formatLong()}: " +
                        clashes.getValue(weekday).joinToString(", ") { "»$it«" },
                    style = EtaTheme.typography.bodyStrong,
                    color = EtaTheme.colors.warning,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = "Trotzdem alle Tage verschieben",
                onClick = onMoveAnyway,
                modifier = Modifier.fillMaxWidth(),
            )
            EtaButton(
                text = "${formatWeekdays(clashes.keys)} ausnehmen",
                onClick = onLeaveOut,
                modifier = Modifier.fillMaxWidth(),
            )
            EtaButton(
                text = "Abbrechen",
                style = EtaButtonStyle.Secondary,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "09:30 · 1 h 15 min" */
private fun PendingReschedule.summary(): String =
    "${minuteToLocalTime(startMinute).formatClock()} · ${durationMinutes.minutes.formatShort()}"
