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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.endMinute
import com.example.erik_iteration_2.domain.planning.minuteToLocalTime
import com.example.erik_iteration_2.domain.planning.notesInOrder
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
    onGeometry: (topInRoot: Float, minutePx: Float) -> Unit = { _, _ -> },
    onBlockLongPress: (BlockWithItem) -> Unit = {},
    onBlockMoved: (PlannedBlock, Int) -> Unit = { _, _ -> },
) {
    val minutePx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() / 60f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT * 24)
            .onGloballyPositioned { onGeometry(it.positionInRoot().y, minutePx) },
    ) {
        SleepShading(sleep, minutePx)
        HourGrid()

        blocks.forEach { entry ->
            TimelineBlock(
                entry = entry,
                minutePx = minutePx,
                editable = editable,
                onLongPress = { onBlockLongPress(entry) },
                onMoved = { minute -> onBlockMoved(entry.block, minute) },
            )
        }

        if (dropIndicatorMinute != null) {
            DropIndicator(minute = dropIndicatorMinute, minutePx = minutePx)
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
    onLongPress: () -> Unit,
    onMoved: (Int) -> Unit,
) {
    val block = entry.block
    val imported = block.origin == BlockOrigin.CALENDAR_IMPORT
    val movable = editable && block.isMovable
    var dragOffset by remember(block.id) { mutableFloatStateOf(0f) }

    val accent = colorOf(entry.item.category)
    val shape = if (block.isMovable) ErikTheme.shapes.draggedBlock else ErikTheme.shapes.fixedBlock
    val height = with(LocalDensity.current) {
        ((block.endMinute() - block.startMinute()) * minutePx).toDp()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER_WIDTH, end = ErikTheme.spacing.sm)
            .offset { IntOffset(0, (block.startMinute() * minutePx + dragOffset).roundToInt()) }
            .height(height)
            .background(
                color = accent.copy(alpha = if (imported) 0.10f else 0.16f),
                shape = shape,
            )
            .border(
                width = if (imported) 1.dp else 2.dp,
                color = if (imported) accent.copy(alpha = 0.6f) else accent,
                shape = shape,
            )
            .alpha(if (block.isCompleted) 0.55f else 1f)
            .pointerInput(entry.block.id, editable) {
                if (!editable) return@pointerInput
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .pointerInput(entry.block.id, movable, minutePx) {
                if (!movable) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        val minute = block.startMinute() + (dragOffset / minutePx).roundToInt()
                        dragOffset = 0f
                        onMoved(minute.coerceIn(0, MINUTES_PER_DAY - 1))
                    },
                    onDragCancel = { dragOffset = 0f },
                ) { change, amount ->
                    change.consume()
                    dragOffset += amount.y
                }
            }
            .padding(horizontal = ErikTheme.spacing.md, vertical = ErikTheme.spacing.xs),
    ) {
        Column {
            ErikText(
                text = entry.item.name,
                style = ErikTheme.typography.bodyStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Below about half an hour there is no room for a second line.
            if (height > 34.dp) {
                ErikText(
                    text = buildString {
                        append(block.start.formatClock())
                        append(" · ")
                        append(block.effectiveDuration.formatShort())
                        if (imported) append(" · Kalender")
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
