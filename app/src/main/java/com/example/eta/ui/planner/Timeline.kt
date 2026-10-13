package com.example.eta.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.eta.data.local.BlockWithItem
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.TimelineDraft
import com.example.eta.domain.planning.coveringMinute
import com.example.eta.domain.planning.pinchedSpan
import com.example.eta.domain.planning.containerEndMinute
import com.example.eta.domain.planning.containerStartMinute
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.breakStartMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.returnStartMinute
import com.example.eta.domain.planning.notesInOrder
import com.example.eta.domain.growth.quantityLabel
import com.example.eta.domain.planning.snapToGrid
import com.example.eta.domain.planning.startMinute
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.QuantityText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.colorOf
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/** One hour of the day. Tall enough that a 15-minute block is still a target. */
val HOUR_HEIGHT: Dp = 64.dp

/** The clock column on the left. */
private val GUTTER_WIDTH: Dp = 56.dp

/**
 * The day, midnight to midnight.
 *
 * Scrolling is the caller's, so the planner can convert a drop anywhere on screen
 * into a time: it needs the scroll offset and where the timeline sits, which
 * [onGeometry] reports back.
 */
@Composable
fun DayTimeline(
    blocks: List<BlockWithItem>,
    sleep: List<IntRange>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Stretches a finished task has released — shaded green rather than drawn.
     *
     * Not blocks any more: they hold nothing, they are in nobody's way, and
     * anything may be planned into them. They are still shown, because a gap
     * where a task used to be says nothing about why it is a gap.
     */
    freed: List<BlockWithItem> = emptyList(),
    /**
     * The steps inside each card, by item id, and which of them this day has ticked
     * off, by block id.
     *
     * A group says what it consists of on the block itself: the name alone would
     * leave the day looking like it holds one vague errand where it holds four
     * things in a row.
     */
    subtasks: Map<String, List<Subtask>> = emptyMap(),
    checked: Map<String, Set<String>> = emptyMap(),
    dropIndicatorMinute: Int? = null,
    /**
     * How far the screen has auto-scrolled the day so far, in pixels.
     *
     * A block drag is relative — it accumulates the finger's own travel, which is
     * what keeps the grab point on a tall block — so scrolling the ground under a
     * still finger would otherwise not move the block at all. Read as state, so a
     * scroll that happens while nothing moves still updates everything.
     */
    autoScrollPx: () -> Float = { 0f },
    onGeometry: (topInRoot: Float, minutePx: Float) -> Unit = { _, _ -> },
    /** The finger's Y in root coordinates while a block is being dragged. */
    onDragPointer: (Float?) -> Unit = {},
    /**
     * A long press, with the minute of the day it landed on.
     *
     * The minute matters because blocks are drawn stacked: two that share an hour
     * both cover the finger, and only the topmost is asked. The caller works out
     * from that minute what else is under there — see [coveringMinute].
     */
    onBlockLongPress: (entry: BlockWithItem, minute: Int) -> Unit = { _, _ -> },
    onBlockMoved: (blockId: String, minute: Int) -> Unit = { _, _ -> },
    /**
     * Which blocks a finger may drag. The planner's own answer by default; the
     * week timetable has one of its own, where what moves is a standing task
     * that has been unlocked and nothing else.
     */
    movable: (BlockWithItem) -> Boolean = { it.block.isMovable },
    /**
     * A long press on a stretch nothing stands on, with its minute. Null where
     * the day cannot take anything new.
     */
    onEmptyLongPress: ((minute: Int) -> Unit)? = null,
    /**
     * The same press landing in the shaded night, which there means "change
     * this night" rather than "put something here". Null leaves the night to
     * [onEmptyLongPress] like any other empty stretch.
     */
    onSleepLongPress: ((minute: Int) -> Unit)? = null,
    /**
     * The box that long press left behind, while it is being pushed and pinched
     * into shape. Held by the caller, who also owns the button that ends it.
     */
    draft: TimelineDraft? = null,
    onDraftChange: (TimelineDraft) -> Unit = {},
    /**
     * A block pinched longer or shorter, with where it now starts and how long it
     * is. Null where blocks are not resized by hand; the [draft] always is.
     */
    onBlockResized: ((blockId: String, startMinute: Int, durationMinutes: Int) -> Unit)? = null,
    /** Whether the night says when it begins and ends — the week timetable's request. */
    sleepLabels: Boolean = false,
) {
    val minutePx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() / 60f }

    // A pinch under way: which block (null is the draft) and the span it has
    // reached. Drawn from here until the fingers lift, then handed up once.
    var pinch by remember { mutableStateOf<Pinch?>(null) }

    // Everything the two gesture blocks below read. They are launched once and
    // outlive every recomposition, so nothing may be closed over directly — the
    // rule from *Compose traps*.
    val currentBlocks by rememberUpdatedState(blocks)
    val currentMovable by rememberUpdatedState(movable)
    val currentDraft by rememberUpdatedState(draft)
    val currentDraftChange by rememberUpdatedState(onDraftChange)
    val currentResized by rememberUpdatedState(onBlockResized)
    val currentEmptyLongPress by rememberUpdatedState(onEmptyLongPress)
    val currentSleepLongPress by rememberUpdatedState(onSleepLongPress)
    val currentSleep by rememberUpdatedState(sleep)

    // Where a block already on the day would land if the finger lifted now, and
    // which block that is. The same indicator a revolver drop gets: without it
    // the finger covers the target and nothing says what time it has reached.
    var movingId by remember { mutableStateOf<String?>(null) }
    var movingMinute by remember { mutableStateOf<Int?>(null) }

    // Which block was tapped, if any. A quarter-hour block is sixteen pixels tall
    // and will never hold its own name, so the short tap — which did nothing
    // before — buys a readable label. Long-press already means "edit".
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = blocks.firstOrNull { it.block.id == selectedId }
    val currentSelectedId by rememberUpdatedState(selectedId)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT * 24)
            .onGloballyPositioned { onGeometry(it.positionInRoot().y, minutePx) }
            // Two fingers: a pinch. Watched from here rather than from the block,
            // and on the way *down* to the children (the initial pass), because a
            // quarter-hour block is sixteen pixels tall and no two fingers fit on
            // it — the pinch has to be allowed to start around what it means.
            // Nothing is consumed until there are two fingers and something to
            // resize, so every one-finger gesture below works as before.
            .pointerInput(minutePx) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var target: Pinch? = null
                    var resolved = false
                    var topTravel = 0f
                    var bottomTravel = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size < 2) {
                            // One finger lifted mid-pinch: the other must not go
                            // on to drag what was just being resized.
                            if (target != null) pressed.forEach { it.consume() }
                            continue
                        }
                        val top = pressed.minBy { it.position.y }
                        val bottom = pressed.maxBy { it.position.y }
                        if (!resolved) {
                            resolved = true
                            target = pinchTarget(
                                draft = currentDraft,
                                blocks = if (currentResized != null) {
                                    currentBlocks.filter { currentMovable(it) && !it.block.isDiscarded }
                                } else {
                                    emptyList()
                                },
                                selectedId = currentSelectedId,
                                topMinute = (top.position.y / minutePx).toInt(),
                                bottomMinute = (bottom.position.y / minutePx).toInt(),
                            )
                        }
                        val origin = target ?: continue
                        topTravel += top.positionChange().y / minutePx
                        bottomTravel += bottom.positionChange().y / minutePx
                        val (from, to) = pinchedSpan(origin.from, origin.to, topTravel, bottomTravel)
                        pinch = origin.copy(from = from, to = to)
                        if (origin.blockId == null) {
                            currentDraftChange(TimelineDraft(from, to - from))
                        }
                        event.changes.forEach { it.consume() }
                    }
                    val done = pinch
                    pinch = null
                    if (done?.blockId != null) {
                        currentResized?.invoke(done.blockId, done.from, done.to - done.from)
                    }
                }
            }
            // One finger held on a stretch nothing stands on. A block consumes
            // the press that lands on it, so this only ever hears the others.
            .pointerInput(onEmptyLongPress != null || onSleepLongPress != null, minutePx) {
                if (onEmptyLongPress == null && onSleepLongPress == null) return@pointerInput
                detectTapGestures(
                    onLongPress = { offset ->
                        val minute = (offset.y / minutePx).toInt().coerceIn(0, MINUTES_PER_DAY - 1)
                        val onNight = currentSleepLongPress
                        if (onNight != null && currentSleep.any { minute in it }) {
                            onNight(minute)
                        } else {
                            currentEmptyLongPress?.invoke(minute)
                        }
                    },
                )
            },
    ) {
        SleepShading(sleep, minutePx, labels = sleepLabels)
        HourGrid()

        // Behind everything a placement can put there, which is the point: the
        // stretch is free, so whatever is planned into it is drawn on top.
        freed.forEach { entry -> FreedStretch(entry = entry, minutePx = minutePx) }

        // Drawn in a pass of its own, before the blocks: the frame belongs behind
        // what it encloses, and the task has to stay on top of its own margins.
        blocks.filter { it.block.hasMargins }.forEach { entry ->
            // The frame drags with its block. It always moved in the database;
            // on screen it used to sit still while the task slid out of it.
            val shift = if (entry.block.id == movingId && movingMinute != null) {
                (movingMinute!! - entry.block.startMinute()) * minutePx
            } else {
                0f
            }
            MarginContainer(entry = entry, minutePx = minutePx, offsetPx = shift)
        }

        blocks.forEach { entry ->
            TimelineBlock(
                entry = entry,
                minutePx = minutePx,
                editable = editable,
                canMove = movable(entry),
                resized = pinch?.takeIf { it.blockId == entry.block.id }?.let { it.from to it.to },
                steps = subtasks[entry.item.id].orEmpty(),
                checkedIds = checked[entry.block.id].orEmpty(),
                selected = entry.block.id == selectedId,
                onTap = {
                    selectedId = if (selectedId == entry.block.id) null else entry.block.id
                },
                onLongPress = { minute -> onBlockLongPress(entry, minute) },
                autoScrollPx = autoScrollPx,
                onDragPointer = onDragPointer,
                onDragPreview = { minute ->
                    movingId = if (minute == null) null else entry.block.id
                    movingMinute = minute
                },
                // The id, never the row: a view model handed a row cannot tell how
                // old it is, and this lambda outlives the composition it came from.
                onMoved = { minute -> onBlockMoved(entry.block.id, minute) },
            )
        }

        // Over the blocks: it is what is being worked on, and it may be pushed
        // across something that already stands there on its way to a free hour.
        draft?.let { DraftBlock(draft = it, minutePx = minutePx, onChange = onDraftChange) }

        val indicator = dropIndicatorMinute ?: movingMinute
        if (indicator != null) {
            DropIndicator(minute = indicator, minutePx = minutePx)
        }

        // The length as it changes, said where the lower finger is pulling it.
        pinch?.let { DurationTag(fromMinute = it.from, toMinute = it.to, minutePx = minutePx) }

        // Drawn last, so it sits over whatever it has to overlap to be legible.
        selected?.let {
            NameBubble(
                entry = it,
                minutePx = minutePx,
                steps = subtasks[it.item.id].orEmpty(),
                checkedIds = checked[it.block.id].orEmpty(),
            )
        }
    }
}

/**
 * The frame around a task that brings a journey or a break with it.
 *
 * One box enclosing two or three, as the note asks: the hatched stretches above
 * and below are the margins, the task keeps its own box inside. It is not a
 * separate block and cannot come adrift from one — the margins are columns on the
 * task itself, so moving the task moves the frame by construction.
 */
@Composable
private fun MarginContainer(entry: BlockWithItem, minutePx: Float, offsetPx: Float = 0f) {
    val block = entry.block
    val accent = if (block.origin == BlockOrigin.CALENDAR_IMPORT) {
        EtaTheme.colors.calendar
    } else {
        colorOf(entry.item)
    }
    val from = block.containerStartMinute()
    val to = block.containerEndMinute()
    val height = with(LocalDensity.current) { ((to - from) * minutePx).toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (from * minutePx + offsetPx).roundToInt()) }
            .padding(start = GUTTER_WIDTH - 6.dp, end = EtaTheme.spacing.sm - 6.dp)
            .height(height)
            .background(accent.copy(alpha = 0.06f), EtaTheme.shapes.medium)
            .border(1.dp, accent.copy(alpha = 0.45f), EtaTheme.shapes.medium),
    ) {
        MarginStrip(
            label = "Anfahrt",
            duration = block.travelBefore,
            fromMinute = 0,
            minutePx = minutePx,
            accent = accent,
        )
        MarginStrip(
            label = "Rückweg",
            duration = block.returnAfter,
            fromMinute = block.returnStartMinute() - from,
            minutePx = minutePx,
            accent = accent,
        )
        MarginStrip(
            label = "Pause",
            duration = block.breakAfter,
            fromMinute = block.breakStartMinute() - from,
            minutePx = minutePx,
            accent = accent,
        )
    }
}

/** One hatched stretch inside the frame — the journey, or the break. */
@Composable
private fun MarginStrip(
    label: String,
    duration: kotlin.time.Duration?,
    fromMinute: Int,
    minutePx: Float,
    accent: Color,
) {
    if (duration == null) return
    val minutes = duration.inWholeMinutes.toInt()
    val height = with(LocalDensity.current) { (minutes * minutePx).toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offsetMinutes(fromMinute, minutePx)
            .height(height)
            .padding(horizontal = 6.dp)
            .background(accent.copy(alpha = 0.12f), EtaTheme.shapes.small)
            .padding(horizontal = EtaTheme.spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Below a quarter of an hour there is no room for a word.
        if (height > 14.dp) {
            EtaText(
                text = "$label · $minutes min",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The full name of the tapped block, spelled out over the day.
 *
 * Placed at the block's own minute and allowed to cover its neighbours: the whole
 * point is that it says what would not fit inside the block. It carries the time
 * and the note too, since a tap on something unreadable is a request to read all
 * of it, not just the first line.
 */
@Composable
private fun NameBubble(
    entry: BlockWithItem,
    minutePx: Float,
    steps: List<Subtask> = emptyList(),
    checkedIds: Set<String> = emptySet(),
) {
    val block = entry.block
    val accent = if (block.origin == BlockOrigin.CALENDAR_IMPORT) {
        EtaTheme.colors.calendar
    } else {
        colorOf(entry.item)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offsetMinutes(block.startMinute(), minutePx)
            .padding(start = GUTTER_WIDTH, end = EtaTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .background(EtaTheme.colors.surfaceRaised, EtaTheme.shapes.medium)
                .border(2.dp, accent, EtaTheme.shapes.medium)
                .padding(horizontal = EtaTheme.spacing.md, vertical = EtaTheme.spacing.sm),
        ) {
            Column {
                EtaText(
                    text = entry.item.name,
                    style = EtaTheme.typography.bodyStrong,
                    textDecoration = if (block.isDiscarded) TextDecoration.LineThrough else null,
                )
                EtaText(
                    text = buildString {
                        append(block.start.formatClock())
                        append(" – ")
                        append(minuteToLocalTime(block.endMinute()).formatClock())
                        append(" · ")
                        append(block.effectiveDuration.formatShort())
                        if (block.isDiscarded) append(" · abgesagt")
                        else if (block.isCompleted) append(" · erledigt")
                    },
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
                // The bubble is where a quarter-hour group can be read at all, so
                // it lists every step rather than the two the block itself fits.
                steps.sortedBy { it.position }.forEach { step ->
                    EtaText(
                        text = stepLabel(step, step.id in checkedIds),
                        style = EtaTheme.typography.caption,
                        color = if (step.id in checkedIds) {
                            EtaTheme.colors.textMuted
                        } else {
                            EtaTheme.colors.textSecondary
                        },
                    )
                }
                if (block.hasMargins) {
                    EtaText(
                        text = listOfNotNull(
                            block.travelBefore?.let { "Anfahrt ${it.inWholeMinutes} min" },
                            block.returnAfter?.let { "Rückweg ${it.inWholeMinutes} min" },
                            block.breakAfter?.let { "Pause ${it.inWholeMinutes} min" },
                        ).joinToString(" · "),
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
                // Where the note stands, but louder: it is what the task is done with.
                QuantityText(entry.item)
                entry.notesInOrder().forEach { note ->
                    EtaText(
                        text = note,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

/**
 * The green wash over a stretch a finished task has given back.
 *
 * It carries the name, because the point of shading it rather than simply leaving
 * a gap is to say *why* the time is free — and the answer, "you did that
 * already", is the one worth reading. Muted and behind everything else: it is a
 * record, not a card, and the moment something is planned into it that something
 * is what the eye should find.
 *
 * No gestures. The block is no longer part of the plan, and the place to take a
 * tick back is the list it was ticked off in.
 */
@Composable
private fun FreedStretch(entry: BlockWithItem, minutePx: Float) {
    val block = entry.block
    val from = block.containerStartMinute()
    val height = ((block.containerEndMinute() - from) * minutePx)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER_WIDTH, end = EtaTheme.spacing.sm)
            .offsetMinutes(from, minutePx)
            .height(with(LocalDensity.current) { height.coerceAtLeast(0f).toDp() })
            .background(EtaTheme.colors.successSoft, EtaTheme.shapes.draggedBlock)
            .padding(horizontal = EtaTheme.spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        EtaText(
            text = "✓ ${entry.item.name} · frei",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.success,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SleepShading(sleep: List<IntRange>, minutePx: Float, labels: Boolean = false) {
    sleep.forEach { stretch ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offsetMinutes(stretch.first, minutePx)
                .height(with(LocalDensity.current) { ((stretch.last + 1 - stretch.first) * minutePx).toDp() })
                .background(EtaTheme.colors.sleep),
        ) {
            if (!labels) return@Box
            // An edge at midnight is the night running on into the next day, not
            // a time anyone goes to bed or gets up at.
            if (stretch.first > 0) {
                EtaText(
                    text = "Schlafenszeit · ${minuteToLocalTime(stretch.first).formatClock()}",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = EtaTheme.spacing.md, vertical = EtaTheme.spacing.xs),
                )
            }
            if (stretch.last + 1 < MINUTES_PER_DAY) {
                EtaText(
                    text = "Aufstehen · ${minuteToLocalTime(stretch.last + 1).formatClock()}",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = EtaTheme.spacing.md, vertical = EtaTheme.spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun HourGrid() {
    Column(Modifier.fillMaxSize()) {
        (0 until 24).forEach { hour ->
            Box(Modifier.fillMaxWidth().height(HOUR_HEIGHT)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(EtaTheme.colors.border),
                )
                EtaText(
                    text = minuteToLocalTime(hour * 60).formatClock(),
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                    modifier = Modifier.padding(start = EtaTheme.spacing.sm, top = EtaTheme.spacing.xs),
                )
            }
        }
    }
}

/**
 * One block.
 *
 * Corners carry meaning: rounded means the user placed it by hand and may move it
 * again, square means it comes from a recurrence or the calendar and stays put.
 */
@Composable
private fun TimelineBlock(
    entry: BlockWithItem,
    minutePx: Float,
    editable: Boolean,
    /** Whether a finger may drag it — the caller's answer, not always the block's own. */
    canMove: Boolean,
    /** The span a pinch has it at, start and end, while one is under way. */
    resized: Pair<Int, Int>?,
    selected: Boolean,
    steps: List<Subtask>,
    checkedIds: Set<String>,
    autoScrollPx: () -> Float,
    onTap: () -> Unit,
    /** Takes the minute of the day the press landed on, not the offset inside the block. */
    onLongPress: (Int) -> Unit,
    onDragPointer: (Float?) -> Unit,
    onDragPreview: (Int?) -> Unit,
    onMoved: (Int) -> Unit,
) {
    // Every one of these outlives the composition it arrived in: `pointerInput`
    // does not restart its block when they change, so a gesture would otherwise
    // call last week's lambda with last week's row. That is what silently wrote a
    // pre-edit block back over an edited one — see *Step 12*.
    val currentTap by rememberUpdatedState(onTap)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentDragPointer by rememberUpdatedState(onDragPointer)
    val currentDragPreview by rememberUpdatedState(onDragPreview)
    val currentMoved by rememberUpdatedState(onMoved)
    val block = entry.block
    val imported = block.origin == BlockOrigin.CALENDAR_IMPORT

    // Called off. The row has to stay — expansion runs forward from today and
    // would lay a deleted occurrence down again — so "Absagen" can only be a
    // state, and the screen has to be the thing that shows it. Before it did,
    // pressing the button looked like it had done nothing at all.
    val cancelled = block.isDiscarded
    val movable = editable && canMove && !cancelled

    // How far the finger has travelled, and the minute that travel snaps to. The
    // block follows the *snapped* minute rather than the finger: a drag that
    // lands wherever it was let go cannot pick a time, which is the whole
    // complaint about the free-hand version this replaces.
    //
    // Both of these are held as `State` objects and read through, never as plain
    // vals lifted out of the composition. `Modifier.pointerInput` does not restart
    // its block on recomposition, so a lambda inside it keeps whatever it closed
    // over at launch time — which is exactly how the committed minute used to end
    // up being the block's *original* start while the preview showed the right
    // one, so every move snapped back.
    // The finger's own travel, and what the day has auto-scrolled since the drag
    // began. The block follows the sum of the two, so it stays under the finger
    // while the ground moves — and so it keeps moving when the ground moves and
    // the finger does not.
    val fingerTravel = remember(block.id) { mutableFloatStateOf(0f) }
    val scrollAtStart = remember(block.id) { mutableFloatStateOf(0f) }
    var dragging by remember(block.id) { mutableStateOf(false) }
    val startMinute = rememberUpdatedState(block.startMinute())

    // Where the block sits in the day, so the finger's position on screen can be
    // worked out from where the drag started plus how far it has come.
    val blockTop = remember(block.id) { mutableFloatStateOf(0f) }

    // Zero unless a drag is under way: the auto-scroll counter runs on across
    // drags, so the difference against it only means anything inside one.
    fun travel(): Float =
        if (!dragging) 0f else fingerTravel.floatValue + (autoScrollPx() - scrollAtStart.floatValue)

    fun minuteAt(travel: Float): Int =
        snapToGrid((startMinute.value + travel / minutePx).roundToInt())
            .coerceIn(0, MINUTES_PER_DAY - 1)

    val draggedMinute = minuteAt(travel())
    val dragOffset = if (dragging) (draggedMinute - block.startMinute()) * minutePx else 0f

    // Where it is drawn: its own span, or the one a pinch has pulled it to.
    val shownStart = resized?.first ?: block.startMinute()
    val shownEnd = resized?.second ?: block.endMinute()

    // Reported from composition rather than from the gesture, so an auto-scroll
    // with a still finger still moves the indicator.
    LaunchedEffect(dragging, draggedMinute) {
        currentDragPreview(if (dragging) draggedMinute else null)
    }

    // Three things an accent can say, in the order they override each other: it
    // is called off, it came out of a calendar, or it is of a category. The
    // calendar's ochre is deliberately not one of the three category colours —
    // the point it makes is "this is somebody else's fixture", not a fourth kind
    // of work.
    val accent = when {
        cancelled -> EtaTheme.colors.textMuted
        imported -> EtaTheme.colors.calendar
        else -> colorOf(entry.item)
    }
    val shape = if (block.isMovable) EtaTheme.shapes.draggedBlock else EtaTheme.shapes.fixedBlock
    val height = with(LocalDensity.current) {
        ((shownEnd - shownStart) * minutePx).toDp()
    }
    // Every other week: hatched, leaning left in the even calendar weeks and
    // right in the odd ones, with the name at the top or at the bottom — so two
    // tasks that share an hour on alternating weeks can both be read where the
    // standing week draws them on top of each other.
    val parity = (entry.item.recurrenceRule as? RecurrenceRule.Biweekly)?.parity

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER_WIDTH, end = EtaTheme.spacing.sm)
            .offset { IntOffset(0, (shownStart * minutePx + dragOffset).roundToInt()) }
            .onGloballyPositioned { blockTop.floatValue = it.positionInRoot().y }
            .height(height)
            .background(
                color = accent.copy(alpha = if (imported) 0.10f else 0.16f),
                shape = shape,
            )
            .border(
                width = if (selected) 3.dp else if (imported) 1.dp else 2.dp,
                color = if (imported && !selected) accent.copy(alpha = 0.6f) else accent,
                shape = shape,
            )
            .drawBehind {
                if (parity == null) return@drawBehind
                val step = 9.dp.toPx()
                val lean = if (parity == WeekParity.EVEN) -1f else 1f
                clipRect {
                    // Lines across the whole box at forty-five degrees, started
                    // far enough out on either side that the corners are covered.
                    var x = -size.height
                    while (x < size.width + size.height) {
                        drawLine(
                            color = accent.copy(alpha = 0.45f),
                            start = Offset(x, if (lean > 0) size.height else 0f),
                            end = Offset(x + size.height, if (lean > 0) 0f else size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                        x += step
                    }
                }
            }
            .alpha(if (block.isCompleted || cancelled) 0.55f else 1f)
            .pointerInput(entry.block.id, editable) {
                detectTapGestures(
                    onTap = { currentTap() },
                    // Null rather than a no-op when the day is confirmed: an
                    // unregistered long press is what lets the parent's own
                    // long-press-to-reopen still see it.
                    //
                    // The offset is the press's Y inside this block, so the minute
                    // it names is the block's own start plus however far down the
                    // finger came. Clamped into the block, so the last pixel of a
                    // block still names a minute the block covers.
                    onLongPress = if (editable) {
                        { offset ->
                            val from = startMinute.value
                            val into = (offset.y / minutePx).toInt().coerceAtLeast(0)
                            val last = (block.endMinute() - 1).coerceAtLeast(from)
                            currentLongPress((from + into).coerceAtMost(last))
                        }
                    } else {
                        null
                    },
                )
            }
            .pointerInput(entry.block.id, movable, minutePx) {
                if (!movable) return@pointerInput
                // The finger's position on screen, so the caller can tell when
                // it has reached an edge. Its own accumulator rather than the
                // block's: the block also moves with the auto-scroll, the finger
                // does not.
                var fingerY = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        fingerTravel.floatValue = 0f
                        scrollAtStart.floatValue = autoScrollPx()
                        fingerY = blockTop.floatValue + offset.y
                        dragging = true
                        currentDragPointer(fingerY)
                    },
                    onDragEnd = {
                        // Read from the state, not from a captured `draggedMinute`.
                        val minute = minuteAt(travel())
                        dragging = false
                        fingerTravel.floatValue = 0f
                        // Reported before the pointer is forgotten: where the
                        // finger let go is what decides whether this was a move or
                        // a drop back onto the revolver.
                        currentMoved(minute)
                        currentDragPointer(null)
                    },
                    onDragCancel = {
                        dragging = false
                        fingerTravel.floatValue = 0f
                        currentDragPointer(null)
                    },
                ) { change, amount ->
                    change.consume()
                    fingerTravel.floatValue += amount.y
                    fingerY += amount.y
                    currentDragPointer(fingerY)
                }
            }
            .padding(horizontal = EtaTheme.spacing.md, vertical = EtaTheme.spacing.xs),
    ) {
        Column(
            modifier = Modifier.align(
                if (parity == WeekParity.ODD) Alignment.BottomStart else Alignment.TopStart,
            ),
        ) {
            EtaText(
                text = entry.item.name,
                style = EtaTheme.typography.bodyStrong,
                color = if (cancelled) EtaTheme.colors.textMuted else EtaTheme.colors.textPrimary,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Below about half an hour there is no room for a second line.
            if (height > 34.dp) {
                EtaText(
                    text = buildString {
                        // While dragging, the block says where it is going, not
                        // where it came from.
                        append(
                            when {
                                dragging -> minuteToLocalTime(draggedMinute).formatClock()
                                resized != null -> minuteToLocalTime(shownStart).formatClock()
                                else -> block.start.formatClock()
                            },
                        )
                        append(" · ")
                        append(
                            if (resized != null) {
                                (shownEnd - shownStart).minutes.formatShort()
                            } else {
                                block.effectiveDuration.formatShort()
                            },
                        )
                        if (imported) append(" · Kalender")
                        if (cancelled) append(" · abgesagt")
                        else if (block.isCompleted) append(" · erledigt")
                    },
                    style = EtaTheme.typography.caption,
                    // The line that is changing under the fingers stands out.
                    color = if (resized != null) EtaTheme.colors.accent else EtaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // What the group consists of, in the room the box has left. A step
            // that is ticked off says so — this is the only place in the planner
            // where the day's progress inside a task is visible at all.
            // The count comes before the steps and the note: it is the one number
            // the task is done with today, and it takes the first spare line.
            val counted = entry.item.quantityLabel() != null && height > 58.dp
            if (counted) QuantityText(entry.item)
            if (steps.isNotEmpty() && height > (if (counted) 76.dp else 58.dp)) {
                val room = ((height - (if (counted) 62.dp else 44.dp)) / 15.dp).toInt().coerceAtLeast(1)
                val shown = steps.sortedBy { it.position }.take(room)
                shown.forEach { step ->
                    EtaText(
                        text = stepLabel(step, step.id in checkedIds),
                        style = EtaTheme.typography.caption,
                        color = if (step.id in checkedIds) {
                            EtaTheme.colors.textMuted
                        } else {
                            EtaTheme.colors.textSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (steps.size > shown.size) {
                    EtaText(
                        text = "+${steps.size - shown.size} weitere",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                        maxLines = 1,
                    )
                }
            }

            // The note about today wins the one line a block can spare.
            val note = entry.notesInOrder().firstOrNull()
            if (note != null && steps.isEmpty() && height > (if (counted) 76.dp else 58.dp)) {
                EtaText(
                    text = note,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A pinch under way. [blockId] null means the draft is what is being resized. */
private data class Pinch(val blockId: String?, val from: Int, val to: Int)

/**
 * What two fingers are resizing, or null when it is nothing of ours.
 *
 * The draft, while there is one: it is what the user is in the middle of
 * shaping, wherever the fingers land. Otherwise the block **between** the
 * fingers — the one their midpoint is on — then one a finger touches, and last
 * the block that was tapped, which is how a quarter-hour block is reached at
 * all: tap it, then pinch around it.
 */
private fun pinchTarget(
    draft: TimelineDraft?,
    blocks: List<BlockWithItem>,
    selectedId: String?,
    topMinute: Int,
    bottomMinute: Int,
): Pinch? {
    if (draft != null) return Pinch(null, draft.startMinute, draft.endMinute)
    val hit = blocks.coveringMinute((topMinute + bottomMinute) / 2).lastOrNull()
        ?: blocks.coveringMinute(topMinute).lastOrNull()
        ?: blocks.coveringMinute(bottomMinute).lastOrNull()
        ?: blocks.firstOrNull { it.block.id == selectedId }
        ?: return null
    return Pinch(hit.block.id, hit.block.startMinute(), hit.block.endMinute())
}

/**
 * The box for a task that does not exist yet.
 *
 * Pushed with one finger, pinched with two — the pinch is the timeline's, see
 * [pinchTarget] — and it says its hours the whole time, because choosing them
 * is all it is for. The accent colour over almost no fill, which no real block
 * wears.
 */
@Composable
private fun DraftBlock(
    draft: TimelineDraft,
    minutePx: Float,
    onChange: (TimelineDraft) -> Unit,
) {
    val current by rememberUpdatedState(draft)
    val currentChange by rememberUpdatedState(onChange)
    val height = with(LocalDensity.current) { (draft.durationMinutes * minutePx).toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER_WIDTH, end = EtaTheme.spacing.sm)
            .offsetMinutes(draft.startMinute, minutePx)
            .height(height)
            .background(EtaTheme.colors.accent.copy(alpha = 0.10f), EtaTheme.shapes.draggedBlock)
            .border(2.dp, EtaTheme.colors.accent, EtaTheme.shapes.draggedBlock)
            .pointerInput(minutePx) {
                // Where the box stood when the finger came down, and how far the
                // finger has come since: the box follows the snapped sum, as a
                // dragged block does.
                var origin = 0
                var travel = 0f
                detectDragGestures(
                    onDragStart = {
                        origin = current.startMinute
                        travel = 0f
                    },
                ) { change, amount ->
                    change.consume()
                    travel += amount.y
                    currentChange(current.movedTo(origin + (travel / minutePx).roundToInt()))
                }
            }
            .padding(horizontal = EtaTheme.spacing.md, vertical = EtaTheme.spacing.xs),
    ) {
        EtaText(
            text = buildString {
                append(minuteToLocalTime(draft.startMinute).formatClock())
                append(" – ")
                append(
                    if (draft.endMinute >= MINUTES_PER_DAY) "24:00"
                    else minuteToLocalTime(draft.endMinute).formatClock(),
                )
                append(" · ")
                append(draft.duration.formatShort())
            },
            style = EtaTheme.typography.bodyStrong,
            color = EtaTheme.colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The length a pinch has reached, on a tag at the edge being pulled. */
@Composable
private fun DurationTag(fromMinute: Int, toMinute: Int, minutePx: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offsetMinutes(toMinute, minutePx)
            .padding(end = EtaTheme.spacing.lg),
        contentAlignment = Alignment.TopEnd,
    ) {
        EtaText(
            text = (toMinute - fromMinute).minutes.formatShort(),
            style = EtaTheme.typography.bodyStrong,
            color = EtaTheme.colors.onAccent,
            modifier = Modifier
                .background(EtaTheme.colors.accent, EtaTheme.shapes.small)
                .padding(horizontal = EtaTheme.spacing.sm, vertical = 1.dp),
        )
    }
}

/** One step, ticked or not: "✓ Einkaufen" against "· Einkaufen". */
private fun stepLabel(step: Subtask, done: Boolean): String =
    (if (done) "✓ " else "· ") + step.name

/** Where a dragged bubble would land, shown while the finger is still down. */
@Composable
private fun DropIndicator(minute: Int, minutePx: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offsetMinutes(minute, minutePx)
            .height(2.dp)
            .padding(start = GUTTER_WIDTH)
            .background(EtaTheme.colors.accent),
    )
    Box(
        modifier = Modifier
            .offsetMinutes(minute, minutePx)
            .padding(start = EtaTheme.spacing.sm)
            .background(EtaTheme.colors.accent, EtaTheme.shapes.small)
            .padding(horizontal = EtaTheme.spacing.sm, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        EtaText(
            text = minuteToLocalTime(minute).formatClock(),
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.onAccent,
        )
    }
}

/** Vertical placement by time of day, in the timeline's own coordinates. */
private fun Modifier.offsetMinutes(minute: Int, minutePx: Float): Modifier =
    offset { IntOffset(0, (minute * minutePx).roundToInt()) }
