package com.example.erik_iteration_2.ui.planner

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
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.autoScrollStep
import com.example.erik_iteration_2.domain.planning.minuteOfDay
import com.example.erik_iteration_2.domain.planning.snapToGrid
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What is being dragged out of a revolver, and where the finger has it. */
private sealed interface DragPayload {
    data class Task(val item: Item) : DragPayload
    data class Spend(val kind: SpendKind) : DragPayload
    data object NewTodo : DragPayload
}

private data class RevolverDrag(
    val entry: RevolverEntry,
    val payload: DragPayload,
    val position: Offset,
)

private enum class RevolverKind { TASKS, SPEND }

/** The second revolver's first chamber. */
private const val NEW_TODO_ID = "NEW_TODO"

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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val breakPrompt by viewModel.breakPrompt.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    var revolverKind by remember { mutableStateOf(RevolverKind.TASKS) }
    var revolverIndex by remember { mutableIntStateOf(0) }

    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var timelineTop by remember { mutableFloatStateOf(0f) }
    var minutePx by remember { mutableFloatStateOf(1f) }
    var drag by remember { mutableStateOf<RevolverDrag?>(null) }

    var editing by remember { mutableStateOf<BlockWithItem?>(null) }
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

    val edgeDragging = dragPointerY != null
    LaunchedEffect(edgeDragging) {
        if (!edgeDragging) return@LaunchedEffect
        while (true) {
            // One step per frame, and the pointer is read fresh each time: an
            // auto-scroll with a perfectly still finger has to keep going.
            withFrameNanos { }
            val y = dragPointerY ?: break
            val step = autoScrollStep(y, viewportTop, viewportHeight)
            if (step != 0f) autoScrolled.floatValue += scrollState.scrollBy(step)
        }
    }

    val taskEntries = state.revolver.map { it.toColouredRevolverEntry() }
    // The spontaneous ToDo leads the second revolver: it is the one a stray
    // thought reaches for, and dropping it is how it gets a time.
    val spendEntries = listOf(
        RevolverEntry(id = NEW_TODO_ID, title = "Neues ToDo", subtitle = "spontan", accent = null),
    ) + SpendKind.entries.map {
        RevolverEntry(id = it.name, title = it.label, subtitle = "1 h", accent = null)
    }
    val entries = if (revolverKind == RevolverKind.TASKS) taskEntries else spendEntries

    /** The minute the finger is currently over, or null when it is off the day. */
    fun minuteUnder(position: Offset): Int? {
        if (minutePx <= 0f) return null
        val minute = ((position.y - timelineTop) / minutePx).roundToInt()
        return snapToGrid(minute).takeIf { it in 0 until MINUTES_PER_DAY }
    }

    // Open where the day is being looked at from: the morning when planning
    // tomorrow, because most of a plan is there, and the hour before now when
    // correcting today, because that is what is being corrected.
    LaunchedEffect(minutePx, state.isToday) {
        if (minutePx <= 1f) return@LaunchedEffect
        val minute = if (state.isToday) {
            (Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .time.minuteOfDay() - 60).coerceAtLeast(0)
        } else {
            7 * 60
        }
        scrollState.scrollTo((minute * minutePx).roundToInt())
    }

    ErikScreen(modifier = modifier) {
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
                        start = ErikTheme.spacing.lg,
                        end = ErikTheme.spacing.lg,
                        top = ErikTheme.spacing.lg,
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
                    ErikButton(
                        text = "Wochenliste füllen",
                        style = ErikButtonStyle.Secondary,
                        onClick = onTopUpWeek,
                        modifier = Modifier.padding(horizontal = ErikTheme.spacing.lg),
                    )
                }

                if (!state.isConfirmed) {
                    Revolver(
                        entries = entries,
                        index = revolverIndex,
                        onIndexChange = { revolverIndex = it },
                        emptyHint = if (revolverKind == RevolverKind.TASKS) {
"Keine ToDos für diese Woche."
                        } else {
                            "Nichts zu verplanen."
                        },
                        onDragStart = { entry, position ->
                            val payload = if (revolverKind == RevolverKind.TASKS) {
                                state.revolver.firstOrNull { it.id == entry.id }
                                    ?.let { DragPayload.Task(it) }
                            } else if (entry.id == NEW_TODO_ID) {
                                DragPayload.NewTodo
                            } else {
                                SpendKind.entries.firstOrNull { it.name == entry.id }
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
                                    is DragPayload.Spend -> spendPrompt = payload.kind to minute
                                    is DragPayload.NewTodo -> newTodoMinute = minute
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
                    DayTimeline(
                        blocks = state.blocks,
                        sleep = state.sleep,
                        editable = !state.isConfirmed,
                        dropIndicatorMinute = drag?.let { minuteUnder(it.position) },
                        autoScrollPx = { autoScrolled.floatValue },
                        onGeometry = { top, perMinute ->
                            timelineTop = top
                            minutePx = perMinute
                        },
                        onDragPointer = { dragPointerY = it },
                        onBlockLongPress = { editing = it },
                        onBlockMoved = viewModel::move,
                    )
                }

                // Said before confirming, while the revolver is still out and the
                // day can still be given some.
                if (!state.isConfirmed && !state.hasFreeTime) {
                    ErikSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ErikTheme.spacing.lg),
                        borderColor = ErikTheme.colors.warning,
                        contentPadding = ErikTheme.spacing.md,
                    ) {
                        ErikText(
                            text = if (state.isToday) {
                                "Für heute ist keine Freizeit eingeplant."
                            } else {
                                "Für morgen ist keine Freizeit eingeplant."
                            },
                            style = ErikTheme.typography.caption,
                            color = ErikTheme.colors.warning,
                        )
                    }
                }

                PlannerFooter(
                    confirmed = state.isConfirmed,
                    isToday = state.isToday,
                    revolverKind = revolverKind,
                    onSwitchRevolver = {
                        revolverKind = if (revolverKind == RevolverKind.TASKS) {
                            RevolverKind.SPEND
                        } else {
                            RevolverKind.TASKS
                        }
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

    editing?.let { entry ->
        BlockEditDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { name, category, start, duration, blockNote, itemNote, travel, pause, sound ->
                viewModel.edit(
                    entry, name, category, start, duration, blockNote, itemNote,
                    travel, pause, sound,
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
            onUncancelBlock = {
                viewModel.uncancel(entry)
                editing = null
            },
            onCopyToWeek = {
                viewModel.copyToWeek(entry)
                editing = null
            },
        )
    }

    newTodoMinute?.let { minute ->
        NewTodoDialog(
            today = state.date,
            onDismiss = { newTodoMinute = null },
            onCreate = { name, attributes ->
                viewModel.createTodo(name, attributes, minute)
                newTodoMinute = null
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
            .padding(horizontal = ErikTheme.spacing.lg, vertical = ErikTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            ErikText(
                text = priority.formatLong(),
                style = ErikTheme.typography.label,
                color = ErikTheme.colors.accent,
            )
            ErikText(
                text = when (lockedBelow) {
                    0 -> "Letzte Stufe."
                    1 -> "Eine weitere Stufe wartet."
                    else -> "$lockedBelow weitere Stufen warten."
                },
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
        if (lockedBelow > 0) {
            ErikButton(
                text = "Überspringen",
                style = ErikButtonStyle.Secondary,
                onClick = onSkip,
            )
        } else {
            ErikButton(
                text = "Von vorn",
                style = ErikButtonStyle.Secondary,
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
            ErikText(
                text = when {
                    state.isToday -> "Heute umplanen"
                    state.isConfirmed -> "Liste für Morgen"
                    else -> "Morgen planen"
                },
                style = ErikTheme.typography.title,
            )
            ErikText(
                text = state.date.formatLong(),
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textSecondary,
            )
        }
        ErikButton(text = "Zurück", style = ErikButtonStyle.Secondary, onClick = onClose)
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
            .padding(ErikTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
    ) {
        // A day being lived is never confirmed again. It was planned last night,
        // and writing `confirmedAt` a second time would misdate that. Changes are
        // saved as they are made, so leaving is all this needs to offer.
        if (isToday) {
            ErikText(
                text = "Änderungen gelten sofort.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            ErikButton(
                text = if (revolverKind == RevolverKind.TASKS) "Punkte" else "ToDos",
                style = ErikButtonStyle.Secondary,
                onClick = onSwitchRevolver,
            )
            ErikButton(text = "Fertig", onClick = onClose)
        } else if (confirmed) {
            ErikText(
                text = "Steht.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            ErikButton(text = "Bearbeiten", style = ErikButtonStyle.Secondary, onClick = onReopen)
            // The phase's job when the day was planned ahead: wave it through.
            ErikButton(text = "Passt so", onClick = onClose)
        } else {
            Spacer(Modifier.weight(1f))
            ErikButton(
                text = if (revolverKind == RevolverKind.TASKS) "Punkte" else "ToDos",
                style = ErikButtonStyle.Secondary,
                onClick = onSwitchRevolver,
            )
            ErikButton(text = "Bestätigen", onClick = onConfirm)
        }
    }
}

/** What the plan did with a drop that could not go where it was aimed. */
@Composable
private fun BoxScope.PlacementBanner(
    feedback: PlacementFeedback,
    onDismiss: () -> Unit,
) {
    ErikSurface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(ErikTheme.spacing.lg)
            .fillMaxWidth(),
        // Only one of these is bad news; a copy into the week is a thing that
        // worked, and a warning border would read as though it had not.
        borderColor = if (feedback is PlacementFeedback.CopiedToWeek) {
            ErikTheme.colors.success
        } else {
            ErikTheme.colors.warning
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ErikText(
                text = when (feedback) {
                    is PlacementFeedback.Moved ->
                        "»${feedback.name}« passte dort nicht — jetzt um ${feedback.to.formatClock()}."

                    is PlacementFeedback.NoRoom ->
                        "»${feedback.name}« passt heute nirgends mehr hin."

                    is PlacementFeedback.Blocked ->
                        "»${feedback.name}« steht auf der Sperrliste."

                    is PlacementFeedback.CopiedToWeek ->
                        "»${feedback.name}« liegt jetzt auch in der Wochenliste."
                },
                style = ErikTheme.typography.body,
                color = ErikTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            ErikButton(text = "OK", style = ErikButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}
