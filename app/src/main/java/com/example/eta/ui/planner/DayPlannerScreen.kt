package com.example.eta.ui.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.eta.domain.planning.UnplannedTone
import com.example.eta.domain.planning.unplannedMinutes
import com.example.eta.domain.planning.unplannedTone
import com.example.eta.domain.planning.BreakFindings
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.PlannerWarning
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.nightDateAt
import com.example.eta.domain.setup.nightEndingOn
import com.example.eta.domain.planning.TimelineDraft
import com.example.eta.domain.planning.autoScrollStep
import com.example.eta.domain.planning.coveringMinute
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.snapToGrid
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.tutorialAllows
import com.example.eta.domain.tutorial.TutorialGate
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatMinutes
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatSignedPoints
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.alertRed
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The card being dragged out of the revolver, and where the finger has it. */
private data class RevolverDrag(
    val entry: RevolverEntry,
    val item: Item,
    val position: Offset,
)

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
    val overlapPrompt by viewModel.overlapPrompt.collectAsStateWithLifecycle()
    val mergeRequest by viewModel.mergeRequest.collectAsStateWithLifecycle()
    val movePrompt by viewModel.movePrompt.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canSpend by viewModel.canSpend.collectAsStateWithLifecycle()
    // Whether the full builder is open, rather than the short question. Keyed by
    // the pair, so a second drop starts over instead of reopening the last one.
    var building by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
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

    // Something made on the spot: held into an empty stretch, pushed and pinched
    // into shape as [draft], then asked what it is ([choosingKind]) and only
    // then about itself ([newEntry]). A ToDo, a break and the two points entries
    // all come about this way; the last three used to be revolvers of their own.
    var draft by remember { mutableStateOf<TimelineDraft?>(null) }
    var choosingKind by remember { mutableStateOf<TimelineDraft?>(null) }
    var newEntry by remember { mutableStateOf<Pair<TimelineDraft, NewEntryKind>?>(null) }

    // The revolver folded away, so more of the day shows. The day keeps its
    // scroll position through it: the viewport grows upward and the hour that
    // lay against the revolver lies against the bar that is left of it.
    var revolverFolded by rememberSaveable { mutableStateOf(false) }

    // The night a long press landed in, as the date it ends on.
    var editingNight by remember { mutableStateOf<LocalDate?>(null) }

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

    // Folded, there is nothing above the day to drop a block on.
    fun overRevolver(y: Float?): Boolean =
        y != null && !revolverFolded && revolverBottom > 0f && y < revolverBottom

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

    val entries = state.revolver.map { it.toColouredRevolverEntry() }

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

                val revolverOut = !state.isConfirmed && !revolverFolded

                if (revolverOut) {
                    PriorityBar(
                        priority = state.revolverPriority,
                        lockedBelow = state.lockedPriorities,
                        passedAbove = state.passedPriorities,
                        canSkip = state.canSkipPriority,
                        skipped = state.prioritySkipped,
                        onSkip = viewModel::skipPriority,
                        onReset = viewModel::resetPriority,
                    )
                }

                // The empty revolver is where the missing week list is actually
                // felt, so the way to fill it belongs here rather than only on a
                // screen behind a swipe.
                if (revolverOut && state.revolver.isEmpty()) {
                    EtaButton(
                        text = "Wochenliste füllen",
                        style = EtaButtonStyle.Secondary,
                        onClick = onTopUpWeek,
                        modifier = Modifier.padding(horizontal = EtaTheme.spacing.lg),
                    )
                }

                if (revolverOut) {
                    Revolver(
                        modifier = Modifier.onGloballyPositioned {
                            revolverBottom = it.positionInRoot().y + it.size.height
                        },
                        entries = entries,
                        index = revolverIndex,
                        onIndexChange = { revolverIndex = it },
                        emptyHint = "Keine ToDos für diese Woche.",
                        onDragStart = { entry, position ->
                            state.revolver.firstOrNull { it.id == entry.id }?.let {
                                drag = RevolverDrag(entry, it, position)
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
                            if (dropped != null && minute != null) viewModel.place(dropped.item, minute)
                        },
                    )
                }

                if (!state.isConfirmed) {
                    RevolverFold(
                        folded = revolverFolded,
                        onToggle = { revolverFolded = !revolverFolded },
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
                            draft = draft,
                            onDraftChange = { draft = it },
                            // Null on the confirmed list, where a long press
                            // anywhere means "reopen".
                            onSleepLongPress = if (state.isConfirmed) {
                                null
                            } else {
                                { minute ->
                                    editingNight = state.setup
                                        ?.nightDateAt(state.date, minute, state.nightOverrides)
                                }
                            },
                            onEmptyLongPress = if (state.isConfirmed) {
                                null
                            } else {
                                // Always a box: what it may become is asked
                                // once it has its hours, and a break is allowed
                                // where a new ToDo is not.
                                { minute -> draft = TimelineDraft.at(minute) }
                            },
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

                if (!state.isConfirmed) {
                    BreakWarnings(
                        findings = state.breaks,
                        dismissed = state.dismissedWarnings,
                        onDismiss = viewModel::dismissWarning,
                    )
                }

                // A box being shaped takes the footer: what it needs is the
                // button that ends it, and confirming the day meanwhile would
                // put the revolver away from under it.
                val shaping = draft
                if (shaping != null && !state.isConfirmed) {
                    DraftFooter(
                        onCancel = { draft = null },
                        onConfirm = {
                            choosingKind = shaping
                            draft = null
                        },
                    )
                } else PlannerFooter(
                    confirmed = state.isConfirmed,
                    isToday = state.isToday,
                    onUndo = viewModel::undo.takeIf { canUndo },
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

    choosingKind?.let { box ->
        NewEntryKindDialog(
            from = box.startMinute,
            until = box.endMinute,
            canCreateTodo = state.canCreateTodo,
            canSpend = canSpend,
            onDismiss = { choosingKind = null },
            onChoose = { kind ->
                newEntry = box to kind
                choosingKind = null
            },
        )
    }

    newEntry?.let { (box, kind) ->
        when (kind) {
            NewEntryKind.TODO, NewEntryKind.APPOINTMENT -> NewTodoDialog(
                today = state.date,
                foldCandidates = state.foldable.foldCandidatesExcept(),
                duration = box.duration,
                appointment = kind == NewEntryKind.APPOINTMENT,
                onDismiss = { newEntry = null },
                onCreate = { name, attributes, note ->
                    viewModel.createTodo(
                        name,
                        attributes,
                        note,
                        box.startMinute,
                        appointment = kind == NewEntryKind.APPOINTMENT,
                    )
                    newEntry = null
                },
            )

            NewEntryKind.BREAK -> BreakDialog(
                duration = box.duration,
                onDismiss = { newEntry = null },
                onCreate = { note ->
                    viewModel.createBreak(note, box.startMinute, box.duration)
                    newEntry = null
                },
            )

            NewEntryKind.EARN, NewEntryKind.SPEND -> {
                val spend = if (kind == NewEntryKind.EARN) SpendKind.CUSTOM_EARN else SpendKind.CUSTOM_SPEND
                SpendDialog(
                    kind = spend,
                    onDismiss = { newEntry = null },
                    onCreate = { name, rate, note ->
                        viewModel.createSpend(spend, name, rate, note, box.startMinute, box.duration)
                        newEntry = null
                    },
                )
            }
        }
    }

    editingNight?.let { wakeDate ->
        val setup = state.setup
        if (setup == null) {
            editingNight = null
        } else {
            val usual = setup.nightEndingOn(wakeDate.dayOfWeek)
            NightEditDialog(
                title = "Nacht auf ${wakeDate.formatLong()}",
                scope = "Gilt nur für diese eine Nacht. Morgen- und Abendroutine " +
                    "und der Weckruf wandern mit. Deine üblichen Zeiten änderst du im " +
                    "Dashboard unter »Woche« oder in den Einstellungen.",
                night = setup.nightEndingOn(wakeDate, state.nightOverrides),
                wakeDay = wakeDate.dayOfWeek,
                // Measured against what this weekday's night usually is.
                compareWith = listOf(usual),
                onDismiss = { editingNight = null },
                onSave = { sleep, wake ->
                    viewModel.changeNight(wakeDate, sleep, wake)
                    editingNight = null
                },
                onReset = if (wakeDate in state.nightOverrides) {
                    {
                        viewModel.resetNight(wakeDate)
                        editingNight = null
                    }
                } else {
                    null
                },
            )
        }
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

    // Held as an id: a block that has gone while the question stood leaves
    // nothing to move, and the dialog closes itself.
    movePrompt?.let { prompt ->
        if (state.blocks.none { it.block.id == prompt.blockId }) {
            viewModel.dismissMove()
            return@let
        }
        MoveDialog(
            prompt = prompt,
            blocks = state.blocks,
            costs = state.isToday,
            onDismiss = viewModel::dismissMove,
            onNextFree = viewModel::moveToNextFree,
            onOverwrite = viewModel::moveOverwriting,
            onCancel = viewModel::cancelMove,
        )
    }

    overlapPrompt?.let { prompt ->
        OverlapDialog(
            prompt = prompt,
            blocks = state.blocks,
            costs = state.isToday,
            onDismiss = viewModel::dismissOverlap,
            onPlaceAnyway = viewModel::placeAnyway,
            onFit = viewModel::placeFitted,
            onAdjustPlan = viewModel::placeAdjustingPlan,
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

}

/**
 * The bar between the revolver and the day, and the way to fold the revolver
 * away so more of the day shows.
 *
 * Always there while the day is being planned, in both states, which is what
 * keeps the day where it is: it is the edge the hours lie against, and only what
 * is above it comes and goes.
 */
@Composable
private fun RevolverFold(folded: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(horizontal = EtaTheme.spacing.lg, vertical = EtaTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaText(
            text = if (folded) "ToDos ausklappen" else "Einklappen",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        EtaText(
            text = if (folded) "▾" else "▴",
            style = EtaTheme.typography.label,
            color = EtaTheme.colors.textMuted,
        )
    }
}

/**
 * What the Pausenchecker has to say about the day, each warning with its own
 * "Ignorieren".
 *
 * Above the footer, where the missing free time is said as well: both are
 * things to hear before the day is confirmed, and neither stops it.
 */
@Composable
private fun BreakWarnings(
    findings: BreakFindings?,
    dismissed: Set<PlannerWarning>,
    onDismiss: (PlannerWarning) -> Unit,
) {
    if (findings == null) return

    if (findings.tooFew && PlannerWarning.BREAK_TOTAL !in dismissed) {
        WarningBar(
            text = "Pausen sind wichtig — und hier sind sehr wenige verplant: " +
                "${findings.restMinutes.minutes.formatBreak()} Pause auf " +
                "${findings.taskMinutes.minutes.formatShort()} Aufgaben. Empfohlen wären " +
                "${findings.neededMinutes.minutes.formatShort()}.",
            onDismiss = { onDismiss(PlannerWarning.BREAK_TOTAL) },
        )
    }
    val stretch = findings.longStretches.firstOrNull()
    if (stretch != null && PlannerWarning.BREAK_STRETCH !in dismissed) {
        val more = findings.longStretches.size - 1
        WarningBar(
            text = "Von ${minuteToLocalTime(stretch.fromMinute).formatClock()} bis " +
                "${stretch.toMinute.formatEndClock()} liegen " +
                "${stretch.taskMinutes.minutes.formatShort()} Aufgaben ohne Pause" +
                when (more) {
                    0 -> "."
                    1 -> " — und an einer weiteren Stelle des Tages."
                    else -> " — und an $more weiteren Stellen des Tages."
                },
            onDismiss = { onDismiss(PlannerWarning.BREAK_STRETCH) },
        )
    }
}

/** "keine" reads better than "0 min" in the middle of that sentence. */
private fun Duration.formatBreak(): String = if (this == Duration.ZERO) "keine" else formatShort()

/** A stretch may end with the day, and midnight is not "00:00" there. */
private fun Int.formatEndClock(): String =
    if (this >= MINUTES_PER_DAY) "24:00" else minuteToLocalTime(this).formatClock()

@Composable
private fun WarningBar(text: String, onDismiss: () -> Unit) {
    EtaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EtaTheme.spacing.lg, vertical = EtaTheme.spacing.xs),
        borderColor = EtaTheme.colors.warning,
        contentPadding = EtaTheme.spacing.md,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
        ) {
            EtaText(
                text = text,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
                modifier = Modifier.weight(1f),
            )
            EtaButton(text = "Ignorieren", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

/** What the footer is while a new entry's box is being pushed and pinched into shape. */
@Composable
private fun DraftFooter(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(EtaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        EtaText(
            text = "Neuer Eintrag: Box verschieben, mit zwei Fingern länger oder kürzer " +
                "ziehen. Was es wird — ToDo, Pause oder Custom —, fragt »Bestätigen«.",
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

/**
 * Which priority the revolver is currently offering, and the way past it.
 *
 * The way past is a setting now ([canSkip], under Flexibilität): the point of
 * the rule is that the important things come first, and a button beside it that
 * waves them through made the rule a suggestion. A tier of which nothing fits
 * the day any more is passed by itself, and the bar says so.
 */
@Composable
private fun PriorityBar(
    priority: Priority?,
    lockedBelow: Int,
    passedAbove: Int,
    canSkip: Boolean,
    skipped: Boolean,
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
            if (passedAbove > 0) {
                EtaText(
                    text = if (passedAbove == 1) {
                        "Eine höhere Stufe passt nicht mehr in den Tag."
                    } else {
                        "$passedAbove höhere Stufen passen nicht mehr in den Tag."
                    },
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
                )
            }
        }
        if (canSkip && lockedBelow > 0) {
            EtaButton(
                text = "Überspringen",
                style = EtaButtonStyle.Secondary,
                onClick = onSkip,
            )
        } else if (skipped) {
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
            UnplannedLine(state)
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

/**
 * How much of the waking day still carries nothing — always in sight while a
 * day is planned (step 52). Red past the two hours the evening forgives, green
 * up to them, grey on a weekend day unless Flexibilität asks for it there too.
 */
@Composable
private fun UnplannedLine(state: PlannerUiState) {
    val minutes = unplannedMinutes(state.blocks + state.freed, state.sleep)
    val tone = unplannedTone(
        minutes = minutes,
        weekend = state.setup?.weekendDays?.contains(state.date.dayOfWeek) == true,
        weekendPlanning = state.setup?.weekendPlanning == true,
    )
    EtaText(
        text = "Unverplant: ${formatMinutes(minutes)}",
        style = EtaTheme.typography.caption,
        color = when (tone) {
            UnplannedTone.OVER -> EtaTheme.colors.alertRed
            UnplannedTone.WITHIN -> EtaTheme.colors.success
            UnplannedTone.RELAXED -> EtaTheme.colors.textMuted
        },
    )
}

@Composable
private fun PlannerFooter(
    confirmed: Boolean,
    isToday: Boolean,
    /** "Rückgängig" (step 53); null while there is nothing to take back. */
    onUndo: (() -> Unit)?,
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
        // Two buttons at most: with something to take back, "Rückgängig" takes
        // the place of the caption.
        if (!confirmed && onUndo != null) {
            EtaButton(text = "↶ Rückgängig", style = EtaButtonStyle.Secondary, onClick = onUndo)
            Spacer(Modifier.weight(1f))
        }
        if (isToday) {
            if (onUndo == null) {
                EtaText(
                    text = "Änderungen gelten sofort.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
            }
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
            // What a break or a points entry used to be reached by — a button
            // through three revolvers — is a long press on the day now, so the
            // footer says where it went.
            if (onUndo == null) {
                EtaText(
                    text = "Freie Stelle gedrückt halten für ToDo, Pause oder Custom.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
            }
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
            feedback is PlacementFeedback.DayCleared ||
            feedback is PlacementFeedback.Undone
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
                        "»${feedback.name}« passt an diesem Tag nirgends mehr hin."

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

                    is PlacementFeedback.Undone ->
                        "Rückgängig gemacht — der Tag steht wieder wie vor der letzten Änderung."

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
