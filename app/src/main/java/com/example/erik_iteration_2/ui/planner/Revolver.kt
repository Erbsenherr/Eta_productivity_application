package com.example.erik_iteration_2.ui.planner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
import com.example.erik_iteration_2.ui.theme.colorOf
import kotlin.math.abs

private val CENTRE_SIZE: Dp = 116.dp
private val NEIGHBOUR_SIZE: Dp = 84.dp
val REVOLVER_HEIGHT: Dp = 172.dp

/** How far sideways a swipe must travel before the cylinder turns one notch. */
private const val ROTATE_THRESHOLD_PX = 60f

/** What one chamber of the revolver holds, whichever revolver is loaded. */
data class RevolverEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val accent: Color?,
)

fun Item.toRevolverEntry(): RevolverEntry = RevolverEntry(
    id = id,
    title = name,
    subtitle = estimatedDuration?.formatShort() ?: "ohne Dauer",
    accent = null,
)

/**
 * The revolver from the draft: one chamber facing the user, its neighbours peeking
 * in from the sides, all sitting on an arc.
 *
 * Swiping sideways turns it; dragging the centre chamber downwards pulls that card
 * out and onto the day. The two gestures live on the same circle, so the first
 * direction the finger takes decides which one it is — a rule that has to be made
 * once and then held, or a hesitant drag would do both.
 */
@Composable
fun Revolver(
    entries: List<RevolverEntry>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emptyHint: String = "Nichts zu verplanen.",
    onDragStart: (RevolverEntry, Offset) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onLongPress: (RevolverEntry) -> Unit = {},
) {
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(REVOLVER_HEIGHT)
            .onGloballyPositioned { positionInRoot = it.positionInRoot() },
    ) {
        RevolverArc(Modifier.fillMaxSize())

        if (entries.isEmpty()) {
            ErikText(
                text = emptyHint,
                style = ErikTheme.typography.body,
                color = ErikTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = ErikTheme.spacing.xxl),
            )
            return@Box
        }

        val size = entries.size
        val current = entries[index.mod(size)]
        val previous = entries[(index - 1).mod(size)]
        val next = entries[(index + 1).mod(size)]

        if (size > 1) {
            NeighbourChamber(
                entry = previous,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
                onClick = { onIndexChange(index - 1) },
            )
            NeighbourChamber(
                entry = next,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                onClick = { onIndexChange(index + 1) },
            )
        }

        CentreChamber(
            entry = current,
            enabled = enabled,
            modifier = Modifier.align(Alignment.Center),
            onRotate = { steps -> onIndexChange(index + steps) },
            onLongPress = { onLongPress(current) },
            onPullStart = { local -> onDragStart(current, positionInRoot + local) },
            onPull = onDrag,
            onPullEnd = onDragEnd,
        )
    }
}

/** The heavy arc the chambers ride on, straight out of the paint draft. */
@Composable
private fun RevolverArc(modifier: Modifier) {
    val color = ErikTheme.colors.border
    Canvas(modifier) {
        val radius = size.width * 0.72f
        val centre = Offset(size.width / 2f, size.height * 0.16f)
        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 10.dp.toPx()),
        )
    }
}

@Composable
private fun CentreChamber(
    entry: RevolverEntry,
    enabled: Boolean,
    modifier: Modifier,
    onRotate: (Int) -> Unit,
    onLongPress: () -> Unit,
    onPullStart: (Offset) -> Unit,
    onPull: (Offset) -> Unit,
    onPullEnd: () -> Unit,
) {
    // Whether the current gesture turned out to be a turn or a pull. Decided once,
    // on the first movement, and kept until the finger lifts. [onPullStart] fires
    // at that decision rather than at the touch, so start and end stay a pair.
    var pulling by remember(entry.id) { mutableStateOf(false) }
    var travel by remember(entry.id) { mutableStateOf(Offset.Zero) }
    val scale by animateFloatAsState(if (pulling) 1.06f else 1f, label = "chamberScale")

    Box(
        modifier = modifier
            .size(CENTRE_SIZE)
            .scale(scale)
            .background(ErikTheme.colors.surfaceRaised, CircleShape)
            .border(2.dp, entry.accent ?: ErikTheme.colors.accent, CircleShape)
            .pointerInput(entry.id, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .pointerInput(entry.id, enabled) {
                if (!enabled) return@pointerInput
                // Where the finger went down, so the pull can report an absolute
                // position the moment it turns out to be one.
                var origin = Offset.Zero
                detectDragGestures(
                    onDragStart = { start ->
                        pulling = false
                        travel = Offset.Zero
                        origin = start
                    },
                    onDragEnd = {
                        if (pulling) {
                            onPullEnd()
                        } else if (abs(travel.x) > ROTATE_THRESHOLD_PX) {
                            onRotate(if (travel.x < 0) 1 else -1)
                        }
                        pulling = false
                        travel = Offset.Zero
                    },
                    onDragCancel = {
                        if (pulling) onPullEnd()
                        pulling = false
                        travel = Offset.Zero
                    },
                ) { change, amount ->
                    change.consume()
                    travel += amount
                    if (!pulling && abs(travel.y) > abs(travel.x) && travel.y > 0f) {
                        pulling = true
                        // Announced *here*, not in onDragStart. A start that turns
                        // out to be a turn never ends with `onPullEnd`, so telling
                        // the caller a pull had begun left it holding a ghost — and,
                        // once the day scrolls at the edges, a finger position that
                        // never went away.
                        onPullStart(origin + travel)
                    }
                    if (pulling) onPull(amount)
                }
            }
            .padding(ErikTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs),
        ) {
            ErikText(
                text = entry.title,
                style = ErikTheme.typography.bodyStrong,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            ErikText(
                text = entry.subtitle,
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NeighbourChamber(
    entry: RevolverEntry,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(NEIGHBOUR_SIZE)
            .background(ErikTheme.colors.surface, CircleShape)
            .border(1.dp, ErikTheme.colors.border, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(ErikTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        ErikText(
            text = entry.title,
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The card that follows the finger once a chamber has been pulled out. */
@Composable
fun DragGhost(entry: RevolverEntry, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = (entry.accent ?: ErikTheme.colors.accent).copy(alpha = 0.9f),
                shape = ErikTheme.shapes.draggedBlock,
            )
            .padding(horizontal = ErikTheme.spacing.lg, vertical = ErikTheme.spacing.sm),
    ) {
        Column {
            ErikText(
                text = entry.title,
                style = ErikTheme.typography.bodyStrong,
                color = ErikTheme.colors.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ErikText(
                text = entry.subtitle,
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.onAccent,
                maxLines = 1,
            )
        }
    }
}

/** Colour a revolver entry the way its category colours everything else. */
@Composable
fun Item.toColouredRevolverEntry(): RevolverEntry =
    toRevolverEntry().copy(accent = colorOf(category))
