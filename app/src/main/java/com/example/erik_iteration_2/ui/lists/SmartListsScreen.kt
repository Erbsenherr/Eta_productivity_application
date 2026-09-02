package com.example.erik_iteration_2.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
import com.example.erik_iteration_2.ui.theme.colorOf

/**
 * The "Smart toDos" tab: six lists stacked, and nothing to do on them.
 *
 * A board, not a workbench. Each list is what a phase left behind, so the way to
 * move a card is to run the phase that moves it — anything else would be a second
 * route around the rules.
 */
@Composable
fun SmartListsScreen(
    viewModel: SmartListsViewModel,
    modifier: Modifier = Modifier,
    onOpenPlanner: () -> Unit = {},
    onPlanWeek: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The two long lists start closed: they are history and backlog, not today.
    var collapsed by remember { mutableStateOf(setOf(ListSection.SPERRLISTE, ListSection.ERFOLG)) }

    fun toggle(section: ListSection) {
        collapsed = if (section in collapsed) collapsed - section else collapsed + section
    }

    ErikScreen(modifier = modifier, bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ErikTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md),
        ) {
            Column {
                ErikText(text = "Smart toDos", style = ErikTheme.typography.display)
                ErikText(
                    text = "Verschoben wird in den Planungsphasen.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
            }

            ListCard(
                section = ListSection.SPERRLISTE,
                count = state.locked.size,
                collapsed = ListSection.SPERRLISTE in collapsed,
                onToggle = ::toggle,
                emptyHint = "Nichts gesperrt.",
            ) {
                state.locked.forEach { entry ->
                    ItemLine(
                        item = entry.item,
                        detail = entry.until?.let { "bis ${it.formatLong()}" } ?: "gesperrt",
                        detailColor = ErikTheme.colors.danger,
                    )
                }
            }

            ListCard(
                section = ListSection.SAMMELLISTE,
                count = state.collection.size,
                collapsed = ListSection.SAMMELLISTE in collapsed,
                onToggle = ::toggle,
                emptyHint = "Leer — nichts notiert.",
            ) {
                state.collection.forEach { entry ->
                    ItemLine(
                        item = entry.item,
                        detail = when {
                            !entry.item.isConcretized -> "unvollständig"
                            entry.daysLeft == null -> ""
                            entry.daysLeft <= 0 -> "läuft ab"
                            entry.daysLeft == 1 -> "noch 1 Tag"
                            else -> "noch ${entry.daysLeft} Tage"
                        },
                        detailColor = if (entry.daysLeft != null && entry.daysLeft <= 7) {
                            ErikTheme.colors.danger
                        } else {
                            ErikTheme.colors.textMuted
                        },
                    )
                }
            }

            ListCard(
                section = ListSection.WOCHENLISTE,
                count = state.week.size,
                collapsed = ListSection.WOCHENLISTE in collapsed,
                onToggle = ::toggle,
                emptyHint = "Für diese Woche ist nichts vorgenommen.",
            ) {
                state.week.forEach { item ->
                    ItemLine(item = item, detail = item.estimatedDuration?.formatShort().orEmpty())
                }
                ErikButton(
                    text = "Wochenliste füllen",
                    style = ErikButtonStyle.Secondary,
                    onClick = onPlanWeek,
                )
            }

            ListCard(
                section = ListSection.TAGESLISTE,
                count = state.todayBlocks.size,
                collapsed = ListSection.TAGESLISTE in collapsed,
                onToggle = ::toggle,
                emptyHint = "Heute steht nichts im Plan.",
            ) {
                state.todayBlocks.forEach { BlockLine(it) }
            }

            // Long-pressing this one is how the concept reopens tomorrow's plan.
            ListCard(
                section = ListSection.MORGEN,
                count = if (state.tomorrowConfirmed) state.tomorrowBlocks.size else 0,
                collapsed = ListSection.MORGEN in collapsed,
                onToggle = ::toggle,
                onLongPress = onOpenPlanner,
                emptyHint = "Erscheint, sobald die Planung für morgen bestätigt ist.",
            ) {
                if (!state.tomorrowConfirmed) return@ListCard
                state.tomorrowBlocks.forEach { BlockLine(it) }
                ErikText(
                    text = "Lange drücken, um die Planung wieder zu öffnen.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )
            }

            ListCard(
                section = ListSection.ERFOLG,
                count = state.done.size,
                collapsed = ListSection.ERFOLG in collapsed,
                onToggle = ::toggle,
                emptyHint = "Noch nichts abgehakt.",
            ) {
                state.done.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ErikText(
                            text = entry.name,
                            style = ErikTheme.typography.body,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ErikText(
                            text = "${entry.at.date.formatLong()} · ${entry.at.time.formatClock()}",
                            style = ErikTheme.typography.caption,
                            color = ErikTheme.colors.success,
                        )
                    }
                }
            }

            Spacer(Modifier.size(ErikTheme.spacing.xl))
        }
    }
}

/** The six lists of `Konzept.md`, in the order it names them. */
private enum class ListSection(val title: String, val subtitle: String) {
    SPERRLISTE("Sperrliste", "Ein halbes Jahr gesperrt"),
    SAMMELLISTE("Sammelliste", "Alles Notierte"),
    WOCHENLISTE("Wochenliste", "Für diese Woche vorgenommen"),
    TAGESLISTE("Tagesliste", "Heute"),
    MORGEN("Liste für Morgen", "Bestätigte Planung"),
    ERFOLG("Erfolgsliste", "Abgehakt, mit Datum und Uhrzeit"),
}

@Composable
private fun ListCard(
    section: ListSection,
    count: Int,
    collapsed: Boolean,
    onToggle: (ListSection) -> Unit,
    emptyHint: String,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onToggle(section) },
                    )
                    .then(
                        if (onLongPress != null) {
                            Modifier.pointerInput(section) {
                                detectTapGestures(onLongPress = { onLongPress() })
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    ErikText(text = section.title, style = ErikTheme.typography.heading)
                    ErikText(
                        text = section.subtitle,
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textMuted,
                    )
                }
                ErikText(
                    text = count.toString(),
                    style = ErikTheme.typography.title,
                    color = if (count == 0) ErikTheme.colors.textMuted else ErikTheme.colors.accent,
                )
                Spacer(Modifier.size(ErikTheme.spacing.sm))
                ErikText(
                    text = if (collapsed) "▾" else "▴",
                    style = ErikTheme.typography.label,
                    color = ErikTheme.colors.textMuted,
                )
            }

            if (!collapsed) {
                if (count == 0) {
                    ErikText(
                        text = emptyHint,
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textMuted,
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun ItemLine(
    item: Item,
    detail: String,
    detailColor: androidx.compose.ui.graphics.Color = ErikTheme.colors.textMuted,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colorOf(item.category), CircleShape),
        )
        Spacer(Modifier.size(ErikTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            ErikText(
                text = item.name,
                style = ErikTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.note?.takeIf { it.isNotBlank() }?.let { note ->
                ErikText(
                    text = note,
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (detail.isNotEmpty()) {
            ErikText(text = detail, style = ErikTheme.typography.caption, color = detailColor)
        }
    }
}

@Composable
private fun BlockLine(entry: BlockWithItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colorOf(entry.item.category), CircleShape),
        )
        Spacer(Modifier.size(ErikTheme.spacing.sm))
        ErikText(
            text = entry.item.name,
            style = ErikTheme.typography.body,
            color = if (entry.block.isOpen) {
                ErikTheme.colors.textPrimary
            } else {
                ErikTheme.colors.textMuted
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ErikText(
            text = buildString {
                append(entry.block.start.formatClock())
                if (entry.block.isCompleted) append(" · erledigt")
                if (entry.block.isDiscarded) append(" · ausgefallen")
            },
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textMuted,
        )
    }
}
