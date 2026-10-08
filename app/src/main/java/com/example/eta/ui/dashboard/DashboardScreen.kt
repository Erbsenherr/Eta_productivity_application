package com.example.eta.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.nowAndNext
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.planner.BlockEditDialog
import com.example.eta.ui.planner.DayPlannerViewModel
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.quickadd.QuickAddPanel
import com.example.eta.ui.quickadd.QuickAddViewModel
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val PAGE_TODAY = 0
private const val PAGE_TOMORROW = 1
private const val PAGE_COUNT = 2

/**
 * The app's home screen. Swiping right reveals tomorrow, as the concept asks —
 * a pager rather than navigation, so the two days feel like one surface.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    quickAddViewModel: QuickAddViewModel,
    modifier: Modifier = Modifier,
    /**
     * Today's planner, for the "Bearbeiten" window a long press in "Heute
     * anstehend" opens. The same view model the "Heute umplanen" flow uses, so
     * an edit made here is the edit made there — one implementation, not two.
     */
    plannerViewModel: DayPlannerViewModel? = null,
    onPlanTomorrow: () -> Unit = {},
    onReplanToday: () -> Unit = {},
    onPlanWeek: () -> Unit = {},
    onCloseDay: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editFeedback by viewModel.blockEditFeedback.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = PAGE_TODAY) { PAGE_COUNT }
    val now = rememberTickingNow()

    EtaScreen(modifier = modifier, bottomInset = false) {
        Column(Modifier.fillMaxSize()) {
            PageIndicator(
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = EtaTheme.spacing.lg,
                        end = EtaTheme.spacing.lg,
                        top = EtaTheme.spacing.lg,
                    ),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_TODAY -> TodayPage(
                        state = state,
                        editFeedback = editFeedback,
                        now = now,
                        viewModel = viewModel,
                        quickAddViewModel = quickAddViewModel,
                        plannerViewModel = plannerViewModel,
                        onCloseDay = onCloseDay,
                        onPlanTomorrow = onPlanTomorrow,
                        onReplanToday = onReplanToday,
                    )

                    else -> TomorrowPage(state, onPlanTomorrow, onPlanWeek)
                }
            }
        }
    }
}

@Composable
private fun TodayPage(
    state: DashboardUiState,
    editFeedback: BlockEditFeedback?,
    now: Instant,
    viewModel: DashboardViewModel,
    quickAddViewModel: QuickAddViewModel,
    plannerViewModel: DayPlannerViewModel?,
    onCloseDay: () -> Unit,
    onPlanTomorrow: () -> Unit,
    onReplanToday: () -> Unit,
) {
    // Held as an id and resolved against the list as it is now: the row may
    // have been ticked off or called off while the dialog stood open.
    var editingId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(EtaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
    ) {
        PageHeader(title = "Heute", subtitle = state.today.formatLong())

        // First of all, because two things at the same time is the one thing on
        // this screen that cannot wait until the evening to be noticed.
        ConflictBox(
            conflicts = state.conflicts,
            today = state.today,
            onIgnore = viewModel::dismissConflict,
            onResolve = { date ->
                // The planner of the day in question is the "bearbeiten" screen
                // for a block: it is the one place a block can be moved, shortened
                // or called off.
                if (date == state.today) onReplanToday() else onPlanTomorrow()
            },
        )

        CriticalBox(critical = state.critical)

        // Recomputed as the clock ticks, so "gerade" stays true without a refresh.
        val nowAndNext = remember(now, state.todayBlocks) {
            nowAndNext(state.todayBlocks, now.toLocalDateTime(TimeZone.currentSystemDefault()).time.minuteOfDay())
        }
        val minuteOfDay = now.toLocalDateTime(TimeZone.currentSystemDefault()).time.minuteOfDay()

        // Long press on either page: the pomodoro switch for that block.
        var pomodoroFor by remember { mutableStateOf<Pair<BlockWithItem, Boolean>?>(null) }

        var confirming by remember { mutableStateOf<BlockWithItem?>(null) }
        val followUp by viewModel.followUp.collectAsStateWithLifecycle()

        NowBox(
            current = nowAndNext.current,
            next = nowAndNext.next,
            minuteOfDay = minuteOfDay,
            onLongPress = { entry, running -> pomodoroFor = entry to running },
            subtasks = state.subtasks,
            checked = state.checked,
            onCheckSubtask = viewModel::setSubtaskChecked,
            onConfirm = { confirming = it },
        )

        // Asked by the screen rather than inside the box: what is being agreed to is
        // that the task counts as done, points and all, and a button that did that
        // on one tap would be one slip away from a wrong day.
        confirming?.let { entry ->
            val steps = state.subtasks[entry.item.id].orEmpty()
            val open = steps.count { it.id !in state.checked[entry.block.id].orEmpty() }
            ConfirmDialog(
                title = "Als erledigt abhaken?",
                message = buildString {
                    append("»")
                    append(entry.item.name)
                    append("« gilt damit als erledigt.")
                    if (open > 0) {
                        append(" ")
                        append(
                            if (open == 1) {
                                "Ein Schritt ist noch offen — der Abendrückblick fragt danach."
                            } else {
                                "$open Schritte sind noch offen — der Abendrückblick fragt danach."
                            },
                        )
                    }
                    append(" Punkte werden erst am Abend verbucht.")
                },
                confirm = "Erledigt",
                onDismiss = { confirming = null },
                onConfirm = {
                    viewModel.toggleCompleted(entry)
                    confirming = null
                },
            )
        }

        // What to do with the time that just came free. Only put where the tick was
        // about now — see `askFollowUp`.
        followUp?.let { question ->
            FollowUpDialog(
                question = question,
                onBilledInFull = viewModel::setBilledInFull,
                onKeepPlan = viewModel::dismissFollowUp,
                onPullForward = { viewModel.pullNextForward(withBreak = false) },
                onBreakThenPull = { viewModel.pullNextForward(withBreak = true) },
            )
        }

        pomodoroFor?.let { (entry, running) ->
            // The freshest copy of the block, so the dialog offers "Ausschalten"
            // the moment the rhythm is on rather than after the next recomposition.
            val fresh = state.todayBlocks.firstOrNull { it.block.id == entry.block.id } ?: entry
            PomodoroDialog(
                entry = fresh,
                running = running,
                onDismiss = { pomodoroFor = null },
                onSetUp = { work, pause ->
                    viewModel.setPomodoro(fresh.block.id, work, pause, fromNow = running)
                    pomodoroFor = null
                },
                onSwitchOff = {
                    viewModel.clearPomodoro(fresh.block.id)
                    pomodoroFor = null
                },
            )
        }

        // Long press on the account: booking a correction by hand.
        var bookingPoints by remember { mutableStateOf(false) }

        PointsBox(
            balance = state.balance,
            pendingHarvest = state.pendingHarvest,
            plannedYield = state.plannedYield,
            crowned = state.crowned,
            onLongPress = { bookingPoints = true },
        )

        if (bookingPoints) {
            ManualPointsDialog(
                balance = state.balance,
                onDismiss = { bookingPoints = false },
                onBook = { amount, note ->
                    viewModel.adjustPoints(amount, note)
                    bookingPoints = false
                },
            )
        }

        // Owed, it belongs where the eye lands. Before the planning time it is a
        // countdown rather than a demand, and the user asked for it to move out
        // of the way of the boxes that are about right now — so the same box is
        // drawn at the bottom instead. One call site, two places, rather than
        // two boxes that could drift apart.
        val phaseOwed = state.phase?.isOwed(now.toLocalDateTime(TimeZone.currentSystemDefault()))
        if (phaseOwed == true) {
            state.phase?.let { phase ->
                PhaseBox(
                    phase = phase,
                    streak = state.streak,
                    // Off the ticking clock, so the box lights up at the minute itself.
                    owed = true,
                    onCloseDay = onCloseDay,
                    onPlanTomorrow = onPlanTomorrow,
                )
            }
        }

        // Between the account and the Quick-Add box, and only while something is
        // actually running out: a deadline is the one thing on this screen with a
        // clock of its own, and an empty box saying so every day would be a
        // permanent reminder that there is nothing to remind anyone of.
        if (state.deadlines.isNotEmpty()) {
            DeadlinesBox(deadlines = state.deadlines, now = now)
        }

        QuickAddPanel(viewModel = quickAddViewModel)

        TasksBox(
            title = "Heute anstehend",
            blocks = state.todayBlocks,
            foldKey = "today.tasks",
            onToggle = viewModel::toggleCompleted,
            onDurationChange = { entry, duration ->
                viewModel.setActualDuration(entry.block, duration)
            },
            onStartChange = viewModel::setStart,
            onUncancel = viewModel::uncancel,
            onEdit = plannerViewModel?.let { { id: String -> editingId = id } },
        )

        // Small corrections happen in the list above. This is the way to the
        // planner itself, for the day already under way — cancelling something,
        // moving it by more than a nudge, or adding what was not foreseen.
        EtaButton(
            text = "Heute umplanen",
            style = EtaButtonStyle.Secondary,
            onClick = onReplanToday,
        )

        BlockEditFeedbackLine(editFeedback, viewModel::dismissBlockEditFeedback)

        // Not yet owed: last, under everything about the day being lived. Closing
        // the day early is still one tap away, it simply no longer sits between
        // the account and today's tasks.
        if (phaseOwed == false) {
            state.phase?.let { phase ->
                PhaseBox(
                    phase = phase,
                    streak = state.streak,
                    owed = false,
                    onCloseDay = onCloseDay,
                    onPlanTomorrow = onPlanTomorrow,
                )
            }
        }

        Spacer(Modifier.size(EtaTheme.spacing.xl))
    }

    if (plannerViewModel != null) {
        state.todayBlocks
            .firstOrNull { it.block.id == editingId && it.block.isOpen }
            ?.let { entry ->
                TodayBlockEditor(entry, plannerViewModel, onDone = { editingId = null })
            }
    }
}

/**
 * The planner's own edit dialog, opened from "Heute anstehend".
 *
 * Everything it does goes through [plannerViewModel], exactly as it does on the
 * planner screen; this only decides which block it is about.
 */
@Composable
private fun TodayBlockEditor(
    entry: BlockWithItem,
    plannerViewModel: DayPlannerViewModel,
    onDone: () -> Unit,
) {
    val planner by plannerViewModel.uiState.collectAsStateWithLifecycle()

    BlockEditDialog(
        entry = entry,
        subtasks = planner.subtasks[entry.item.id].orEmpty(),
        foldCandidates = planner.foldable.foldCandidatesExcept(entry.item.id),
        onSubtasks = { plannerViewModel.saveSubtasks(entry.block.id, it) },
        onDismiss = onDone,
        onSave = { n, cat, start, dur, bNote, iNote, travel, back, pause, sound, rate ->
            plannerViewModel.edit(
                entry, n, cat, start, dur, bNote, iNote, travel, back, pause, sound, rate,
            )
            onDone()
        },
        onRemove = {
            plannerViewModel.remove(entry)
            onDone()
        },
        onCancelBlock = {
            plannerViewModel.cancel(entry)
            onDone()
        },
        onCancelExcused = {
            plannerViewModel.cancel(entry, forceMajeure = true)
            onDone()
        },
        onCopyToWeek = {
            plannerViewModel.copyToWeek(entry)
            onDone()
        },
        // This list is today's, and today's plan is a promise already made.
        cancellationCosts = true,
    )
}

@Composable
private fun TomorrowPage(
    state: DashboardUiState,
    onPlanTomorrow: () -> Unit,
    onPlanWeek: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(EtaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
    ) {
        PageHeader(title = "Morgen", subtitle = state.tomorrow.formatLong())

        TasksBox(
            title = "Morgen anstehend",
            blocks = state.tomorrowBlocks,
            emptyHint = "Für morgen ist noch nichts geplant.",
        )

        // Until the planning alarms exist, these are the ways into the two phases.
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Morgen planen", onClick = onPlanTomorrow)
            EtaButton(
                text = "Woche planen",
                style = EtaButtonStyle.Secondary,
                onClick = onPlanWeek,
            )
        }

        Spacer(Modifier.size(EtaTheme.spacing.xl))
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column {
        EtaText(text = title, style = EtaTheme.typography.display)
        EtaText(
            text = subtitle,
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
    }
}

/** What a start-time correction did, when it could not do exactly as asked. */
@Composable
private fun BlockEditFeedbackLine(
    feedback: BlockEditFeedback?,
    onDismiss: () -> Unit,
) {
    if (feedback == null) return

    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.warning,
        contentPadding = EtaTheme.spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtaText(
                text = when (feedback) {
                    is BlockEditFeedback.SlotTaken ->
                        "»${feedback.name}« lässt sich nicht mehr einplanen — die Zeit " +
                            "ist inzwischen anderweitig verplant."

                    is BlockEditFeedback.Uncancelled ->
                        "»${feedback.name}« steht wieder im Plan."

                    is BlockEditFeedback.Moved ->
                        "»${feedback.name}« lag dort nicht frei — jetzt um ${feedback.to.formatClock()}."

                    is BlockEditFeedback.NoRoom ->
                        "»${feedback.name}« passt heute nirgends mehr hin."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            EtaButton(text = "OK", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

@Composable
private fun PageIndicator(currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { page ->
            Box(
                modifier = Modifier
                    .size(if (page == currentPage) 8.dp else 6.dp)
                    .background(
                        color = if (page == currentPage) {
                            EtaTheme.colors.accent
                        } else {
                            EtaTheme.colors.border
                        },
                        shape = CircleShape,
                    ),
            )
        }
        Spacer(Modifier.width(EtaTheme.spacing.sm))
        EtaText(
            text = if (currentPage == PAGE_TODAY) "wischen für morgen" else "wischen für heute",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}

/**
 * A clock that advances once a second, so deadline countdowns tick.
 * Only recomposes what reads it.
 */
@Composable
private fun rememberTickingNow(): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Clock.System.now()
        }
    }
    return now
}
