package com.example.erik_iteration_2.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.containerEndMinute
import com.example.erik_iteration_2.domain.planning.containerStartMinute
import com.example.erik_iteration_2.domain.planning.endMinute
import com.example.erik_iteration_2.domain.planning.minuteToLocalTime
import com.example.erik_iteration_2.domain.planning.notesInOrder
import com.example.erik_iteration_2.domain.planning.snapToGrid
import com.example.erik_iteration_2.domain.planning.startMinute
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
import com.example.erik_iteration_2.ui.theme.colorOf
import kotlin.math.roundToInt

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
    onBlockLongPress: (BlockWithItem) -> Unit = {},
    onBlockMoved: (blockId: String, minute: Int) -> Unit = { _, _ -> },
) {
    val minutePx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() / 60f }

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT * 24)
            .onGloballyPositioned { onGeometry(it.positionInRoot().y, minutePx) },
    ) {
        SleepShading(sleep, minutePx)
        HourGrid()

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
                selected = entry.block.id == selectedId,
                onTap = {
                    selectedId = if (selectedId == entry.block.id) null else entry.block.id
                },
                onLongPress = { onBlockLongPress(entry) },
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

        val indicator = dropIndicatorMinute ?: movingMinute
        if (indicator != null) {
            DropIndicator(minute = indicator, minutePx = minutePx)
        }

        // Drawn last, so it sits over whatever it has to overlap to be legible.
        selected?.let { NameBubble(entry = it, minutePx = minutePx) }
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
    val accent = colorOf(entry.item.category)
    val from = block.containerStartMinute()
    val to = block.containerEndMinute()
    val height = with(LocalDensity.current) { ((to - from) * minutePx).toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, (from * minutePx + offsetPx).roundToInt()) }
            .padding(start = GUTTER_WIDTH - 6.dp, end = ErikTheme.spacing.sm - 6.dp)
            .height(height)
            .background(accent.copy(alpha = 0.06f), ErikTheme.shapes.medium)
            .border(1.dp, accent.copy(alpha = 0.45f), ErikTheme.shapes.medium),
    ) {
        MarginStrip(
            label = "Anfahrt",
            duration = block.travelBefore,
            fromMinute = 0,
            minutePx = minutePx,
            accent = accent,
        )
        MarginStrip(
            label = "Pause",
            duration = block.breakAfter,
            fromMinute = block.endMinute() - from,
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
            .background(accent.copy(alpha = 0.12f), ErikTheme.shapes.small)
            .padding(horizontal = ErikTheme.spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Below a quarter of an hour there is no room for a word.
        if (height > 14.dp) {
            ErikText(
                text = "$label · $minutes min",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
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
private fun NameBubble(entry: BlockWithItem, minutePx: Float) {
    val block = entry.block
    val accent = colorOf(entry.item.category)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offsetMinutes(block.startMinute(), minutePx)
            .padding(start = GUTTER_WIDTH, end = ErikTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .background(ErikTheme.colors.surfaceRaised, ErikTheme.shapes.medium)
                .border(2.dp, accent, ErikTheme.shapes.medium)
                .padding(horizontal = ErikTheme.spacing.md, vertical = ErikTheme.spacing.sm),
        ) {
            Column {
                ErikText(
                    text = entry.item.name,
                    style = ErikTheme.typography.bodyStrong,
                    textDecoration = if (block.isDiscarded) TextDecoration.LineThrough else null,
                )
                ErikText(
                    text = buildString {
                        append(block.start.formatClock())
                        append(" – ")
                        append(minuteToLocalTime(block.endMinute()).formatClock())
                        append(" · ")
                        append(block.effectiveDuration.formatShort())
                        if (block.isDiscarded) append(" · abgesagt")
                        else if (block.isCompleted) append(" · erledigt")
                    },
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
                if (block.hasMargins) {
                    ErikText(
                        text = buildString {
                            block.travelBefore?.let {
                                append("Anfahrt ${it.inWholeMinutes} min")
                            }
                            if (block.travelBefore != null && block.breakAfter != null) {
                                append(" · ")
                            }
                            block.breakAfter?.let { append("Pause ${it.inWholeMinutes} min") }
                        },
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textMuted,
                    )
                }
                entry.notesInOrder().forEach { note ->
                    ErikText(
                        text = note,
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepShading(sleep: List<IntRange>, minutePx: Float) {
    sleep.forEach { stretch ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offsetMinutes(stretch.first, minutePx)
                .height(with(LocalDensity.current) { ((stretch.last + 1 - stretch.first) * minutePx).toDp() })
                .background(ErikTheme.colors.sleep),
        )
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
                        .background(ErikTheme.colors.border),
                )
                ErikText(
                    text = minuteToLocalTime(hour * 60).formatClock(),
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                    modifier = Modifier.padding(start = ErikTheme.spacing.sm, top = ErikTheme.spacing.xs),
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
    selected: Boolean,
    autoScrollPx: () -> Float,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
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
    val movable = editable && block.isMovable && !cancelled

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

    // Reported from composition rather than from the gesture, so an auto-scroll
    // with a still finger still moves the indicator.
    LaunchedEffect(dragging, draggedMinute) {
        currentDragPreview(if (dragging) draggedMinute else null)
    }

    val accent = if (cancelled) ErikTheme.colors.textMuted else colorOf(entry.item.category)
    val shape = if (block.isMovable) ErikTheme.shapes.draggedBlock else ErikTheme.shapes.fixedBlock
    val height = with(LocalDensity.current) {
        ((block.endMinute() - block.startMinute()) * minutePx).toDp()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER_WIDTH, end = ErikTheme.spacing.sm)
            .offset { IntOffset(0, (block.startMinute() * minutePx + dragOffset).roundToInt()) }
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
            .alpha(if (block.isCompleted || cancelled) 0.55f else 1f)
            .pointerInput(entry.block.id, editable) {
                detectTapGestures(
                    onTap = { currentTap() },
                    // Null rather than a no-op when the day is confirmed: an
                    // unregistered long press is what lets the parent's own
                    // long-press-to-reopen still see it.
                    onLongPress = if (editable) ({ currentLongPress() }) else null,
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
                        currentDragPointer(null)
                        currentMoved(minute)
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
            .padding(horizontal = ErikTheme.spacing.md, vertical = ErikTheme.spacing.xs),
    ) {
        Column {
            ErikText(
                text = entry.item.name,
                style = ErikTheme.typography.bodyStrong,
                color = if (cancelled) ErikTheme.colors.textMuted else ErikTheme.colors.textPrimary,
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Below about half an hour there is no room for a second line.
            if (height > 34.dp) {
                ErikText(
                    text = buildString {
                        // While dragging, the block says where it is going, not
                        // where it came from.
                        append(
                            if (dragging) {
                                minuteToLocalTime(draggedMinute).formatClock()
                            } else {
                                block.start.formatClock()
                            },
                        )
                        append(" · ")
                        append(block.effectiveDuration.formatShort())
                        if (imported) append(" · Kalender")
                        if (cancelled) append(" · abgesagt")
                        else if (block.isCompleted) append(" · erledigt")
                    },
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The note about today wins the one line a block can spare.
            val note = entry.notesInOrder().firstOrNull()
            if (note != null && height > 58.dp) {
                ErikText(
                    text = note,
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Where a dragged bubble would land, shown while the finger is still down. */
@Composable
private fun DropIndicator(minute: Int, minutePx: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offsetMinutes(minute, minutePx)
            .height(2.dp)
            .padding(start = GUTTER_WIDTH)
            .background(ErikTheme.colors.accent),
    )
    Box(
        modifier = Modifier
            .offsetMinutes(minute, minutePx)
            .padding(start = ErikTheme.spacing.sm)
            .background(ErikTheme.colors.accent, ErikTheme.shapes.small)
            .padding(horizontal = ErikTheme.spacing.sm, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        ErikText(
            text = minuteToLocalTime(minute).formatClock(),
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.onAccent,
        )
    }
}

/** Vertical placement by time of day, in the timeline's own coordinates. */
private fun Modifier.offsetMinutes(minute: Int, minutePx: Float): Modifier =
    offset { IntOffset(0, (minute * minutePx).roundToInt()) }
