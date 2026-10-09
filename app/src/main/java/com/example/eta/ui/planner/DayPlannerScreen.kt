package com.example.eta.ui.planner

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.autoScrollStep
import com.example.eta.domain.planning.coveringMinute
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.snapToGrid
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.ReportToTutorial
import com.example.eta.ui.components.tutorialAllows
import com.example.eta.domain.tutorial.TutorialSignal
import com.example.eta.domain.tutorial.TutorialGate
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatSignedPoints
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.theme.EtaTheme
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What is being dragged out of a revolver, and where the finger has it. */
private sealed interface DragPayload {
    data class Task(val item: Item) : DragPayload
    data class Spend(val kind: SpendKind) : DragPayload
    data object NewTodo : DragPayload
    data object Break : DragPayload
}

private data class RevolverDrag(
    val entry: RevolverEntry,
    val payload: DragPayload,
    val position: Offset,
)

/**
 * The three revolvers, in the order the footer's button walks through them.
 *
 * The break has one of its own rather than a chamber among the points: it
 * neither earns nor costs, so it is not a points entry, and it is reached for
 * often enough that it should not sit behind four others.
 */
private enum class RevolverKind(val label: String) {
    TASKS("ToDos"),
    SPEND("Punkte"),
    BREAK("Pause"),
    ;

    fun next(): RevolverKind = entries[(ordinal + 1) % entries.size]

    /** [label], or what the second revolver is called while points are out of sight. */
    @Composable
    fun shownLabel(): String =
        if (this == SPEND && !LocalPointsVisible.current) "Spontan" else label
}

/** The second revolver's first chamber. */
private const val NEW_TODO_ID = "NEW_TODO"

/** The third revolver's only one. */
private const val BREAK_ID = "BREAK"

/**
 * A day: the revolver on top, the day below, and the one gesture that connects
 * them.
 *
 * Which day it is comes from the view model. Tomorrow is the planning phase and
 * ends by being confirmed; today is the same screen turned on the day already
 * running, so a plan can still be corrected once it turns out to be wrong.
 *
 * A drop has to become a time, which means turning a position on screen into a
 * minute of the day. The timeline reports where it sits and how tall a minute is;
 * because that report comes from layout, it stays correct while the day scrolls
 * under the finger.
 */
@Composable
fun DayPlannerScreen(
    viewModel: DayPlannerViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onTopUpWeek: () -> Unit = {},
    /**
     * The minute of the day to open centred on — an entry of the Tagesliste the
     * user asked to see in the plan. Null opens where the day is usually looked
     * at from.
     */
    focusMinute: Int? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val breakPrompt by viewModel.breakPrompt.collectAsStateWithLifecycle()
    val mergeRequest by viewModel.mergeRequest.collectAsStateWithLifecycle()
    val canSpend by viewModel.canSpend.collectAsStateWithLifecycle()
    // Whether the full builder is open, rather than the short question. Keyed by
    // the pair, so a second drop starts over instead of reopening the last one.
    var building by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    var revolverKind by remember { mutableStateOf(RevolverKind.TASKS) }
    ReportToTutorial(TutorialSignal.PLANNER_REVOLVER, revolverKind.name)
    var revolverIndex by remember { mutableIntStateOf(0) }

    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var timelineTop by remember { mutableFloatStateOf(0f) }
    var minutePx by remember { mutableFloatStateOf(1f) }
    var drag by remember { mutableStateOf<RevolverDrag?>(null) }

    var editing by remember { mutableStateOf<BlockWithItem?>(null) }
    // More than one block under the long press. Held as ids rather than rows, so
    // the dialog redraws from the current state instead of from whatever the
    // gesture happened to see.
    var choosingIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var clearingDay by remember { mutableStateOf(false) }
    var spendPrompt by remember { mutableStateOf<Pair<SpendKind, Int>?>(null) }
    var newTodoMinute by remember { mutableStateOf<Int?>(null) }

    // Dragging past the edge of the screen scrolls the day under the finger, so
    // the hours off-screen can be reached at all.
    //
    // A revolver drag needs nothing else: it is absolute, a position turned into a
    // minute through the timeline's reported geometry, so it follows a scroll for
    // free. A block drag is relative — it accumulates the finger's own travel,
    // which is what preserves the grab point on a tall block — so it is told how
    // far the day has moved and adds that to its own reckoning.
    val autoScrolled = remember { mutableFloatStateOf(0f) }
    var dragPointerY by remember { mutableStateOf<Float?>(null) }
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }

    // Where the revolver ends, in root coordinates. A block dropped above this
    // line goes back to the week list rather than to a time.
    var revolverBottom by remember { mutableFloatStateOf(0f) }

    fun overRevolver(y: Float?): Boolean = y != null && revolverBottom > 0f && y < revolverBottom

    val edgeDragging = dragPointerY != null
    LaunchedEffect(edgeDragging) {
        if (!edgeDragging) return@LaunchedEffect
        while (true) {
            // One step per frame, and the pointer is read fresh each time: an
            // auto-scroll with a perfectly still finger has to keep going.
            withFrameNanos { }
            val y = dragPointerY ?: break
            // Reaching the revolver means dragging up out of the day, which is
            // exactly what the top band answers with a scroll. Its own bounds
            // suppress it rather than the band being made smaller — the band is
            // right for what it is for.
            if (overRevolver(y)) continue
            val step = autoScrollStep(y, viewportTop, viewportHeight)
            if (step != 0f) autoScrolled.floatValue += scrollState.scrollBy(step)
        }
    }

    val taskEntries = state.revolver.map { it.toColouredRevolverEntry() }
    // The spontaneous ToDo leads the second revolver: it is the one a stray
    // thought reaches for, and dropping it is how it gets a time.
    // Custom Earn and Custom Spend are points and nothing else, so they leave
    // the revolver with the points system. What already stands on a day stays
    // and is booked as before; Social is neutral and stays offered.
    val pointsVisible = LocalPointsVisible.current
    val spendEntries = listOf(
        RevolverEntry(id = NEW_TODO_ID, title = "Neues ToDo", subtitle = "spontan", accent = null),
    ) + SpendKind.entries
        .filter { pointsVisible || it.pointsPerHour == 0.0 }
        .map { kind ->
            // Spending takes points to spend; the card stays, and says why not.
            val locked = kind == SpendKind.CUSTOM_SPEND && !canSpend
            RevolverEntry(
                id = kind.name,
                title = kind.label,
                subtitle = if (locked) "kein Guthaben" else "1 h",
                accent = null,
            )
        }
    val breakEntries = listOf(
        RevolverEntry(
            id = BREAK_ID,
            title = "Pause",
            subtitle = PLANNED_BREAK.formatShort() + if (pointsVisible) " · 0 Punkte" else "",
            accent = null,
        ),
    )
    val entries = when (revolverKind) {
        RevolverKind.TASKS -> taskEntries
        RevolverKind.SPEND -> spendEntries
        RevolverKind.BREAK -> breakEntries
    }

    /** The minute the finger is currently over, or null when it is off the day. */
    fun minuteUnder(position: Offset): Int? {
        if (minutePx <= 0f) return null
        val minute = ((position.y - timelineTop) / minutePx).roundToInt()
        return snapToGrid(minute).takeIf { it in 0 until MINUTES_PER_DAY }
    }

    // Open where the day is being looked at from: the morning when planning
    // tomorrow, because most of a plan is there, and the hour before now when
    // correcting today, because that is what is being corrected.
    // Keyed on the viewport as well: centring needs to know how tall it is, and
    // that is only reported once the day has been laid out.
    val viewportKnown = viewportHeight > 0f
    LaunchedEffect(minutePx, state.isToday, focusMinute, viewportKnown) {
        if (minutePx <= 1f) return@LaunchedEffect
        if (focusMinute != null) {
            if (!viewportKnown) return@LaunchedEffect
            val centred = focusMinute * minutePx - viewportHeight / 2f
            scrollState.scrollTo(centred.roundToInt().coerceAtLeast(0))
            return@LaunchedEffect
        }
        val minute = if (state.isToday) {
            (Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .time.minuteOfDay() - 60).coerceAtLeast(0)
        } else {
            7 * 60
        }
        scrollState.scrollTo((minute * minutePx).roundToInt())
    }

    EtaScreen(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOffset = it.positionInRoot() },
        ) {
            Column(Modifier.fillMaxSize()) {
                PlannerHeader(
                    state = state,
                    onClose = onClose,
                    modifier = Modifier.padding(
                        start = EtaTheme.spacing.lg,
                        end = EtaTheme.spacing.lg,
                        top = EtaTheme.spacing.lg,
                    ),
                )

                if (!state.isConfirmed && revolverKind == RevolverKind.TASKS) {
                    PriorityBar(
                        priority = state.revolverPriority,
                        lockedBelow = state.lockedPriorities,
                        onSkip = viewModel::skipPriority,
                        onReset = viewModel::resetPriority,
                    )
                }

                // The empty revolver is where the missing week list is actually
                // felt, so the way to fill it belongs here rather than only on a
                // screen behind a swipe.
                if (!state.isConfirmed &&
                    revolverKind == RevolverKind.TASKS &&
                    state.revolver.isEmpty()
                ) {
                    EtaButton(
                        text = "Wochenliste füllen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onTopUpWeek,
                        modifier = Modifier.padding(horizontal = EtaTheme.spacing.lg),
                    )
                }

                if (!state.isConfirmed) {
                    Revolver(
                        modifier = Modifier.onGloballyPositioned {
                            revolverBottom = it.positionInRoot().y + it.size.height
                        },
                        entries = entries,
                        index = revolverIndex,
                        onIndexChange = { revolverIndex = it },
                        emptyHint = if (revolverKind == RevolverKind.TASKS) {
"Keine ToDos für diese Woche."
                        } else {
                            "Nichts zu verplanen."
                        },
                        onDragStart = { entry, position ->
                            val payload = when {
                                revolverKind == RevolverKind.TASKS ->
                                    state.revolver.firstOrNull { it.id == entry.id }
                                        ?.let { DragPayload.Task(it) }

                                revolverKind == RevolverKind.BREAK -> DragPayload.Break
                                entry.id == NEW_TODO_ID -> DragPayload.NewTodo
                                else -> SpendKind.entries.firstOrNull { it.name == entry.id }
                                    ?.let { DragPayload.Spend(it) }
                            }
                            if (payload != null) {
                                drag = RevolverDrag(entry, payload, position)
                                dragPointerY = position.y
                            }
                        },
                        onDrag = { delta ->
                            drag = drag?.let { it.copy(position = it.position + delta) }
                            dragPointerY = drag?.position?.y
                        },
                        onDragEnd = {
                            val dropped = drag
                            drag = null
                            dragPointerY = null
                            val minute = dropped?.let { minuteUnder(it.position) }
                            if (dropped != null && minute != null) {
                                when (val payload = dropped.payload) {
                                    is DragPayload.Task -> viewModel.place(payload.item, minute)
                                    is DragPayload.Spend ->
                                        if (payload.kind == SpendKind.CUSTOM_SPEND && !canSpend) {
                                            viewModel.refuseSpend()
                                        } else {
                                            spendPrompt = payload.kind to minute
                                        }
                                    is DragPayload.NewTodo -> newTodoMinute = minute
                                    // No dialog: a break has nothing to ask. Its
                                    // length is changed like any block's, by a
                                    // long press on it.
                                    is DragPayload.Break -> viewModel.createBreak(minute)
                                }
                            }
                        },
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            viewportTop = it.positionInRoot().y
                            viewportHeight = it.size.height.toFloat()
                        }
                        .verticalScroll(scrollState)
                        // A long press anywhere on the confirmed list brings the
                        // revolver back out, as the concept asks.
                        .pointerInput(state.isConfirmed) {
                            if (!state.isConfirmed) return@pointerInput
                            detectTapGestures(onLongPress = { viewModel.reopen() })
                        },
                ) {
                    Column {
                        DayTimeline(
                            blocks = state.blocks,
                            freed = state.freed,
                            subtasks = state.subtasks,
                            checked = state.checked,
                            sleep = state.sleep,
                            editable = !state.isConfirmed,
                            dropIndicatorMinute = drag?.let { minuteUnder(it.position) },
                            autoScrollPx = { autoScrolled.floatValue },
                            onGeometry = { top, perMinute ->
                                timelineTop = top
                                minutePx = perMinute
                            },
                            onDragPointer = { dragPointerY = it },
                            onBlockLongPress = { entry, minute ->
                                // What else is drawn over that minute. Two blocks
                                // sharing an hour are stacked, and only the one on
                                // top is asked — so the one underneath was
                                // unreachable until this.
                                val under = state.blocks.coveringMinute(minute)
                                if (under.size > 1) {
                                    choosingIds = under.map { it.block.id }
                                } else {
                                    editing = entry
                                }
                            },
                            onBlockMoved = { blockId, minute ->
                                // Dropped on the revolver: unplanned rather than moved.
                                if (overRevolver(dragPointerY)) {
                                    viewModel.returnToWeek(blockId)
                                } else {
                                    viewModel.move(blockId, minute)
                                }
                            },
                        )

                        // Past midnight, under the end of the day: a day that is
                        // not going to happen at all is one decision, and having
                        // to long-press every card in turn is not the shape of it.
                        // Below the day rather than in the footer, because that is
                        // where it can be reached without being in the way.
                        if (!state.isConfirmed && state.blocks.any { it.block.isOpen }) {
                            EtaButton(
                                text = "Ganzen Tag absagen",
                                style = EtaButtonStyle.Secondary,
                                onClick = { clearingDay = true },
                                modifier = Modifier.padding(EtaTheme.spacing.lg),
                            )
                        }
                    }
                }

                // Said before confirming, while the revolver is still out and the
                // day can still be given some.
                if (!state.isConfirmed && !state.hasFreeTime) {
                    EtaSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EtaTheme.spacing.lg),
                        borderColor = EtaTheme.colors.warning,
                        contentPadding = EtaTheme.spacing.md,
                    ) {
                        EtaText(
                            text = if (state.isToday) {
                                "Für heute ist keine Freizeit eingeplant."
                            } else {
                                "Für morgen ist keine Freizeit eingeplant."
                            },
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.warning,
                        )
                    }
                }

                PlannerFooter(
                    confirmed = state.isConfirmed,
                    isToday = state.isToday,
                    revolverKind = revolverKind,
                    onSwitchRevolver = {
                        revolverKind = revolverKind.next()
                        revolverIndex = 0
                    },
                    onConfirm = viewModel::confirm,
                    onReopen = viewModel::reopen,
                    onClose = onClose,
                )
            }

            drag?.let { active ->
                DragGhost(
                    entry = active.entry,
                    modifier = Modifier.offset {
                        val local = active.position - rootOffset
                        IntOffset(
                            x = (local.x - 80.dp.toPx()).roundToInt(),
                            y = (local.y - 20.dp.toPx()).roundToInt(),
                        )
                    },
                )
            }

            feedback?.let { PlacementBanner(it, viewModel::dismissFeedback) }
        }
    }

    if (choosingIds.size > 1) {
        val candidates = choosingIds.mapNotNull { id ->
            state.blocks.firstOrNull { it.block.id == id }
        }
        // One of them may have gone while the dialog stood open — a day cleared
        // from elsewhere, an occurrence relaid. Then there is nothing to choose.
        if (candidates.size > 1) {
            BlockChoiceDialog(
                candidates = candidates,
                onDismiss = { choosingIds = emptyList() },
                onChoose = {
                    editing = it
                    choosingIds = emptyList()
                },
            )
        } else {
            choosingIds = emptyList()
        }
    }

    editing?.let { entry ->
        BlockEditDialog(
            entry = entry,
            subtasks = state.subtasks[entry.item.id].orEmpty(),
            foldCandidates = state.foldable.foldCandidatesExcept(entry.item.id),
            onSubtasks = { viewModel.saveSubtasks(entry.block.id, it) },
            onDismiss = { editing = null },
            onSave = { n, cat, start, dur, bNote, iNote, travel, back, pause, sound, rate, flex ->
                viewModel.edit(
                    entry, n, cat, start, dur, bNote, iNote, travel, back, pause, sound, rate, flex,
                )
                editing = null
            },
            onRemove = {
                viewModel.remove(entry)
                editing = null
            },
            onCancelBlock = {
                viewModel.cancel(entry)
                editing = null
            },
            onCancelExcused = {
                viewModel.cancel(entry, forceMajeure = true)
                editing = null
            },
            onCopyToWeek = {
                viewModel.copyToWeek(entry)
                editing = null
            },
            // Tomorrow has not started, so calling something off is still
            // planning and costs nothing. Today's plan is a promise already made.
            cancellationCosts = state.isToday,
        )
    }

    newTodoMinute?.let { minute ->
        NewTodoDialog(
            today = state.date,
            foldCandidates = state.foldable.foldCandidatesExcept(),
            onDismiss = { newTodoMinute = null },
            onCreate = { name, attributes ->
                viewModel.createTodo(name, attributes, minute)
                newTodoMinute = null
            },
        )
    }

    if (clearingDay) {
        ClearDayDialog(
            blocks = state.blocks.filter { it.block.isOpen },
            costs = state.isToday,
            onDismiss = { clearingDay = false },
            onConfirm = {
                viewModel.cancelWholeDay()
                clearingDay = false
            },
        )
    }

    breakPrompt?.let { prompt ->
        BreakDroppedDialog(
            name = prompt.name,
            onDismiss = viewModel::dismissBreakPrompt,
            onPlaceAnyway = viewModel::placeWithoutBreak,
        )
    }

    // Resolved from the current state every time it is drawn, and held as ids: a
    // candidate that has gone while the dialog stood open leaves nothing to
    // decide, and the dialog closes itself. The rule from step 12.
    mergeRequest?.let { request ->
        val dragged = state.blocks.firstOrNull { it.block.id == request.draggedId }
        val target = state.blocks.firstOrNull { it.block.id == request.targetId }
        if (dragged == null || target == null) {
            viewModel.dismissMerge()
            building = null
            return@let
        }
        val draggedSubtasks = state.subtasks[dragged.item.id].orEmpty()
        val targetSubtasks = state.subtasks[target.item.id].orEmpty()

        if (building == request.draggedId + request.targetId) {
            GroupTasksDialog(
                dragged = dragged,
                target = target,
                foldCandidates = state.foldable.foldCandidatesExcept(
                    dragged.item.id,
                    target.item.id,
                ),
                onDismiss = {
                    building = null
                    viewModel.dismissMerge()
                },
                onGroup = { answers ->
                    building = null
                    viewModel.fold(target.block.id, dragged.block.id, answers)
                },
            )
        } else {
            MergePromptDialog(
                dragged = dragged,
                target = target,
                draggedSubtasks = draggedSubtasks,
                targetSubtasks = targetSubtasks,
                onDismiss = viewModel::dismissMerge,
                onMoveOnly = { viewModel.moveAnyway(request.draggedId, request.toMinute) },
                onBuildGroup = { building = request.draggedId + request.targetId },
                onFold = { survivor, dissolved, deadline ->
                    viewModel.fold(
                        survivorId = survivor.block.id,
                        dissolvedId = dissolved.block.id,
                        answers = foldAnswers(
                            survivor = survivor,
                            survivorSubtasks = state.subtasks[survivor.item.id].orEmpty(),
                            dissolved = dissolved,
                            dissolvedSubtasks = state.subtasks[dissolved.item.id].orEmpty(),
                            deadlineAt = deadline,
                        ),
                    )
                },
            )
        }
    }

    spendPrompt?.let { (kind, minute) ->
        SpendDialog(
            kind = kind,
            onDismiss = { spendPrompt = null },
            onCreate = { name, rate ->
                viewModel.createSpend(kind, name, rate, minute)
                spendPrompt = null
            },
        )
    }

}

/**
 * Which priority the revolver is currently offering, and the way past it.
 *
 * Skipping is offered rather than done automatically: the point of the rule is
 * that the important things are offered first, so waving one past should take a
 * deliberate tap.
 */
@Composable
private fun PriorityBar(
    priority: Priority?,
    lockedBelow: Int,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (priority == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EtaTheme.spacing.lg, vertical = EtaTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            EtaText(
                text = priority.formatLong(),
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.accent,
            )
            EtaText(
                text = when (lockedBelow) {
                    0 -> "Letzte Stufe."
                    1 -> "Eine weitere Stufe wartet."
                    else -> "$lockedBelow weitere Stufen warten."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
        if (lockedBelow > 0) {
            EtaButton(
                text = "Überspringen",
                style = EtaButtonStyle.Secondary,
                onClick = onSkip,
            )
        } else {
            EtaButton(
                text = "Von vorn",
                style = EtaButtonStyle.Secondary,
                onClick = onReset,
            )
        }
    }
}

@Composable
private fun PlannerHeader(
    state: PlannerUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            EtaText(
                text = when {
                    state.isToday -> "Heute umplanen"
                    state.isConfirmed -> "Liste für Morgen"
                    else -> "Morgen planen"
                },
                style = EtaTheme.typography.title,
            )
            EtaText(
                text = state.date.formatLong(),
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            // Here as well as on the dashboard, because this is where a rate is
            // chosen: placing a Custom Spend should visibly cost something.
            if (LocalPointsVisible.current) {
                EtaText(
                    text = "Prognose: ${formatSignedPoints(state.plannedYield)} Punkte, " +
                        "wenn alles erledigt wird",
                    style = EtaTheme.typography.caption,
                    color = when {
                        state.plannedYield < 0 -> EtaTheme.colors.danger
                        else -> EtaTheme.colors.textMuted
                    },
                )
            }
        }
        EtaButton(text = "Zurück", style = EtaButtonStyle.Secondary, onClick = onClose)
    }
}

@Composable
private fun PlannerFooter(
    confirmed: Boolean,
    isToday: Boolean,
    revolverKind: RevolverKind,
    onSwitchRevolver: () -> Unit,
    onConfirm: () -> Unit,
    onReopen: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(EtaTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        // A day being lived is never confirmed again. It was planned last night,
        // and writing `confirmedAt` a second time would misdate that. Changes are
        // saved as they are made, so leaving is all this needs to offer.
        if (isToday) {
            EtaText(
                text = "Änderungen gelten sofort.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            EtaButton(
                // Names where the button leads, as it always did with two.
                text = revolverKind.next().shownLabel(),
                style = EtaButtonStyle.Secondary,
                onClick = onSwitchRevolver,
            )
            EtaButton(text = "Fertig", onClick = onClose)
        } else if (confirmed) {
            EtaText(
                text = "Steht.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            EtaButton(text = "Bearbeiten", style = EtaButtonStyle.Secondary, onClick = onReopen)
            // The phase's job when the day was planned ahead: wave it through.
            EtaButton(text = "Passt so", onClick = onClose)
        } else {
            Spacer(Modifier.weight(1f))
            EtaButton(
                // Names where the button leads, as it always did with two.
                text = revolverKind.next().shownLabel(),
                style = EtaButtonStyle.Secondary,
                onClick = onSwitchRevolver,
            )
            EtaButton(
                text = "Bestätigen",
                // Confirming puts the revolver away; in the tutorial that waits
                // until the card it asks for has been placed.
                enabled = tutorialAllows(TutorialGate.PLANNER_CONFIRM),
                onClick = onConfirm,
            )
        }
    }
}

/** What the plan did with a drop that could not go where it was aimed. */
@Composable
private fun BoxScope.PlacementBanner(
    feedback: PlacementFeedback,
    onDismiss: () -> Unit,
) {
    EtaSurface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(EtaTheme.spacing.lg)
            .fillMaxWidth(),
        // Only one of these is bad news; a copy into the week is a thing that
        // worked, and a warning border would read as though it had not.
        borderColor = if (
            feedback is PlacementFeedback.CopiedToWeek ||
            feedback is PlacementFeedback.ReturnedToWeek ||
            feedback is PlacementFeedback.Removed ||
            feedback is PlacementFeedback.DayCleared
        ) {
            EtaTheme.colors.success
        } else {
            EtaTheme.colors.warning
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtaText(
                text = when (feedback) {
                    is PlacementFeedback.Moved ->
                        "»${feedback.name}« passte dort nicht — jetzt um ${feedback.to.formatClock()}."

                    is PlacementFeedback.NoRoom ->
                        "»${feedback.name}« passt heute nirgends mehr hin."

                    is PlacementFeedback.Blocked ->
                        "»${feedback.name}« steht auf der Sperrliste."

                    is PlacementFeedback.CopiedToWeek ->
                        "»${feedback.name}« liegt jetzt auch in der Wochenliste."

                    is PlacementFeedback.ReturnedToWeek ->
                        "»${feedback.name}« liegt wieder in der Wochenliste."

                    is PlacementFeedback.Removed ->
                        "»${feedback.name}« ist vom Tag genommen."

                    is PlacementFeedback.Overlapping ->
                        "»${feedback.name}« ist gruppiert, passt aber nirgends ganz hin — " +
                            "der Block überschneidet sich jetzt."

                    PlacementFeedback.NoPoints ->
                        "Custom Spend braucht Guthaben — dein Punktestand liegt bei 0 " +
                            "oder darunter."

                    is PlacementFeedback.DayCleared -> when (feedback.count) {
                        0 -> "Es stand nichts mehr offen."
                        1 -> "Der Tag ist leer — eine Karte wurde abgeräumt."
                        else -> "Der Tag ist leer — ${feedback.count} Karten abgeräumt."
                    }
                },
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            EtaButton(text = "OK", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}
