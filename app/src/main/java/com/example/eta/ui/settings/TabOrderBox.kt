package com.example.eta.ui.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.example.eta.domain.subtask.moved
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaIconImage
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaTabItem
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme
import kotlin.math.roundToInt

/**
 * "Menü umsortieren" (step 48): the tabs the bar shows, in an order of the
 * user's own.
 *
 * Only the visible tabs are listed — a tab whose feature is off keeps its slot
 * in the stored order, see `reorderVisible`. Nothing is fixed: the dashboard and
 * the settings move like the rest, and the app still opens on the dashboard
 * wherever it stands.
 */
@Composable
internal fun TabOrderBox(
    tabs: List<EtaTabItem>,
    onReorder: (List<String>) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Menü", style = EtaTheme.typography.heading)
            EtaText(
                text = "In welcher Reihenfolge die Reiter in der Leiste stehen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaButton(
                text = "Menü umsortieren",
                style = EtaButtonStyle.Secondary,
                enabled = tabs.size > 1,
                onClick = { editing = true },
            )
        }
    }

    if (editing) {
        TabOrderDialog(
            tabs = tabs,
            onDismiss = { editing = false },
            onConfirm = { order ->
                onReorder(order)
                editing = false
            },
        )
    }
}

/**
 * The list, rearranged by holding a row and dragging it — the gesture the
 * Growth-Tasks tab and the subtask builder use. Everything the gesture reads
 * goes through a state object: a `pointerInput` lambda keeps what it closed over.
 */
@Composable
private fun TabOrderDialog(
    tabs: List<EtaTabItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var rows by remember { mutableStateOf(tabs) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val rowHeight = remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { EtaTheme.spacing.xs.toPx() }
    val current = rememberUpdatedState(rows)
    val step = rememberUpdatedState(gap)

    EtaDialog(title = "Menü umsortieren", onDismiss = onDismiss) {
        EtaText(
            text = "Einen Reiter lange drücken und an seinen Platz ziehen.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            rows.forEachIndexed { index, tab ->
                val dragging = draggingId == tab.id
                EtaSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { if (rowHeight.intValue == 0) rowHeight.intValue = it.height }
                        .offset { IntOffset(0, if (dragging) dragOffset.floatValue.roundToInt() else 0) }
                        .pointerInput(tab.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = tab.id
                                    dragOffset.floatValue = 0f
                                },
                                onDrag = { _, amount ->
                                    dragOffset.floatValue += amount.y
                                    val pitch = rowHeight.intValue + step.value
                                    if (pitch <= 0f) return@detectDragGesturesAfterLongPress
                                    val steps = (dragOffset.floatValue / pitch).roundToInt()
                                    if (steps == 0) return@detectDragGesturesAfterLongPress
                                    val list = current.value
                                    val from = list.indexOfFirst { it.id == tab.id }
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    val to = (from + steps).coerceIn(0, list.size - 1)
                                    if (to == from) return@detectDragGesturesAfterLongPress
                                    rows = list.moved(from, to)
                                    dragOffset.floatValue -= (to - from) * pitch
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset.floatValue = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset.floatValue = 0f
                                },
                            )
                        },
                    color = if (dragging) EtaTheme.colors.surfaceRaised else EtaTheme.colors.surface,
                    borderColor = if (dragging) EtaTheme.colors.accent else EtaTheme.colors.border,
                    contentPadding = EtaTheme.spacing.md,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EtaText(
                            text = "${index + 1}.",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                        Spacer(Modifier.size(EtaTheme.spacing.sm))
                        tab.icon?.let {
                            EtaIconImage(icon = it, color = EtaTheme.colors.accent)
                            Spacer(Modifier.size(EtaTheme.spacing.sm))
                        }
                        EtaText(
                            text = tab.label,
                            style = EtaTheme.typography.body,
                            modifier = Modifier.weight(1f),
                        )
                        EtaText(
                            text = "≡",
                            style = EtaTheme.typography.heading,
                            color = EtaTheme.colors.textMuted,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Übernehmen", onClick = { onConfirm(rows.map { it.id }) })
        }
    }
}
