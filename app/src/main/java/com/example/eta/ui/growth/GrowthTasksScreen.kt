package com.example.eta.ui.growth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.growth.growthSetting
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.lists.RecurringGroupEditDialog
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.theme.EtaTheme
import kotlin.math.roundToInt

/**
 * The Growth-Tasks tab: the tasks that get longer, in the order that decides
 * which of them wins a slot.
 *
 * Its own tab rather than a section of the Listen tab, because the **order** is
 * what this screen is for. Growth tasks appear in the standing schedule as well
 * — they are standing tasks — but nothing there could express a ranking, and a
 * ranking is what dynamic placement consults.
 */
@Composable
fun GrowthTasksScreen(viewModel: GrowthTasksViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringGroup?>(null) }

    EtaScreen(bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Growth-Tasks", style = EtaTheme.typography.title)
                EtaText(
                    text = "Aufgaben, die mit jedem Abschluss länger werden.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            if (state.groups.isEmpty()) {
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaText(
                        text = "Noch keine. Eine Growth-Task beginnt kurz und wächst bis zu " +
                            "ihrer Zielzeit — für alles, was sich in voller Länge nicht " +
                            "anfangen lässt.",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            } else {
                ReorderableList(
                    groups = state.groups,
                    onOpen = { editing = it },
                    onReorder = viewModel::reorder,
                )
                EtaText(
                    text = "Lange drücken und ziehen ändert die Reihenfolge. Wollen zwei " +
                        "Aufgaben mit dynamischer Zeitsetzung denselben Slot, bekommt ihn " +
                        "die weiter oben.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            EtaButton(text = "Growth-Task erstellen", onClick = { creating = true })
            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }

    if (creating) {
        GrowthTaskCreateDialog(
            definitions = state.definitions,
            foldCandidates = state.foldable.foldCandidatesExcept(),
            onDismiss = { creating = false },
            onCreate = { name, note, attributes ->
                viewModel.create(name, note, attributes)
                creating = false
            },
        )
    }

    editing?.let { group ->
        // The Listen tab's own editor: a growth task is a standing task, and two
        // editors for one thing would be two places for the rules to drift.
        RecurringGroupEditDialog(
            group = group,
            definitions = state.definitions,
            title = "Growth-Task",
            subtasks = state.subtasks[group.representative.id].orEmpty(),
            foldCandidates = state.foldable.foldCandidatesExcept(*group.ids.toTypedArray()),
            onDismiss = { editing = null },
            onSave = { name, note, attributes ->
                viewModel.save(group, name, note, attributes)
                editing = null
            },
        )
    }
}

/**
 * The list, reordered by dragging after a long press.
 *
 * The reorder happens in local state while the finger is down and is written
 * once on release: a write per swap would relay the dynamic placement of three
 * weeks of days at every step of one gesture.
 *
 * Everything the gesture reads goes through a `State` object or
 * `rememberUpdatedState` — the rule step 12 earned twice. A `pointerInput`
 * lambda does not restart on recomposition, so a plain `val` captured from the
 * composition keeps the value it had when the gesture began.
 */
@Composable
private fun ReorderableList(
    groups: List<RecurringGroup>,
    onOpen: (RecurringGroup) -> Unit,
    onReorder: (List<RecurringGroup>) -> Unit,
) {
    // The order being shown: the stored one, except while a drag rearranges it.
    // Keyed on the ids, so a save or an edit elsewhere resets it.
    var order by remember(groups.map { it.representative.id }) { mutableStateOf(groups) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val rowHeight = remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { EtaTheme.spacing.sm.toPx() }

    val currentOrder = rememberUpdatedState(order)
    val commit = rememberUpdatedState(onReorder)
    val open = rememberUpdatedState(onOpen)
    val step = rememberUpdatedState(gap)

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        order.forEachIndexed { index, group ->
            val id = group.representative.id
            val dragging = draggingId == id

            GrowthRow(
                group = group,
                position = index + 1,
                dragging = dragging,
                onClick = { open.value(group) },
                modifier = Modifier
                    .onSizeChanged { if (rowHeight.intValue == 0) rowHeight.intValue = it.height }
                    .offset {
                        IntOffset(0, if (dragging) dragOffset.floatValue.roundToInt() else 0)
                    }
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = id
                                dragOffset.floatValue = 0f
                            },
                            onDrag = { _, amount ->
                                dragOffset.floatValue += amount.y
                                // One row plus the gap between two: what the
                                // finger has to travel for a swap to be meant.
                                val pitch = rowHeight.intValue + step.value
                                if (pitch <= 0f) return@detectDragGesturesAfterLongPress
                                val moved = (dragOffset.floatValue / pitch).roundToInt()
                                if (moved == 0) return@detectDragGesturesAfterLongPress

                                val list = currentOrder.value
                                val from = list.indexOfFirst { it.representative.id == id }
                                if (from < 0) return@detectDragGesturesAfterLongPress
                                val to = (from + moved).coerceIn(0, list.size - 1)
                                if (to == from) return@detectDragGesturesAfterLongPress

                                order = list.toMutableList().apply { add(to, removeAt(from)) }
                                // The row now sits where the finger reached, so
                                // the distance the list moved it comes off the
                                // offset it is still being drawn with.
                                dragOffset.floatValue -= (to - from) * pitch
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset.floatValue = 0f
                                commit.value(currentOrder.value)
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset.floatValue = 0f
                            },
                        )
                    },
            )
        }
    }
}

/** One growth task: where it stands, where it is going, and when it happens. */
@Composable
private fun GrowthRow(
    group: RecurringGroup,
    position: Int,
    dragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = group.representative
    val growth = item.growthSetting
    val interactionSource = remember { MutableInteractionSource() }

    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        color = if (dragging) EtaTheme.colors.surfaceRaised else EtaTheme.colors.surface,
        borderColor = if (dragging) EtaTheme.colors.accent else EtaTheme.colors.border,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EtaText(
                text = "$position.",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textMuted,
            )
            Spacer(Modifier.size(EtaTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                EtaText(
                    text = item.name,
                    style = EtaTheme.typography.bodyStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                EtaText(
                    text = buildString {
                        append(formatWeekdays(group.weekdays))
                        item.startTime?.let {
                            // "ab" rather than a time: with dynamic placement the
                            // hour is the earliest one, not the one it happens at.
                            append(if (growth?.dynamic == true) " · ab " else " · ")
                            append(it.formatClock())
                        }
                    },
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
                growth?.let {
                    EtaText(
                        text = "${item.estimatedDuration?.formatShort() ?: "—"} → " +
                            "${it.target.formatShort()} (+${it.increment.formatShort()}" +
                            (if (it.every > 1) " alle ${it.every} Abschlüsse)" else ")"),
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.accent,
                    )
                }
            }
            if (growth?.dynamic == true) {
                EtaText(
                    text = "dyn.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.accent,
                )
                Spacer(Modifier.size(EtaTheme.spacing.sm))
            }
            EtaText(
                text = "≡",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
}
