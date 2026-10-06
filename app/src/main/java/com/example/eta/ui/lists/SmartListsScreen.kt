package com.example.eta.ui.lists

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
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.domain.recurrence.weekOccupancy
import com.example.eta.domain.setup.SETTINGS_OWNED_ITEM_IDS
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.QuantityText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.colorOf

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
    val todaySettled by viewModel.todaySettled.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val booked = remember(state.definitions, setup) { weekOccupancy(state.definitions, setup) }

    // The long lists start closed: history, backlog and the standing week, not today.
    var collapsed by remember {
        mutableStateOf(setOf(ListSection.SPERRLISTE, ListSection.WIEDERKEHREND, ListSection.ERFOLG))
    }

    // A tapped row. The card it holds says what to show; the flag says whether the
    // popup may offer more than reading, which is a property of the *list* it came
    // from rather than of the card.
    var opened by remember { mutableStateOf<OpenedRow?>(null) }
    var editing by remember { mutableStateOf<Item?>(null) }
    // Both of the popup's consequences are asked about before they happen, and
    // the question is put out here rather than inside the popup: a dialog opened
    // from within a dialog is a window on top of a window, and what is being
    // confirmed is that the popup's card is about to leave the list.
    var confirming by remember { mutableStateOf<PendingAction?>(null) }
    // Standing tasks are opened as a group, one entry per task rather than per weekday.
    var openedGroup by remember { mutableStateOf<RecurringGroup?>(null) }
    var editingGroup by remember { mutableStateOf<RecurringGroup?>(null) }

    fun toggle(section: ListSection) {
        collapsed = if (section in collapsed) collapsed - section else collapsed + section
    }

    EtaScreen(modifier = modifier, bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
        ) {
            Column {
                EtaText(text = "Smart toDos", style = EtaTheme.typography.display)
                EtaText(
                    text = "Verschoben wird in den Planungsphasen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
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
                    val detail = entry.until?.let { "bis ${it.formatLong()}" } ?: "gesperrt"
                    ItemLine(
                        item = entry.item,
                        detail = detail,
                        detailColor = EtaTheme.colors.danger,
                        // Read-only: the ban is a rule with a date on it, not a
                        // card whose attributes are up for correction.
                        onOpen = { opened = OpenedRow(entry.item, detail, editable = false) },
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
                    CollectionLine(entry) { detail ->
                        opened = OpenedRow(entry.item, detail, editable = true)
                    }
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
                    val detail = item.estimatedDuration?.formatShort().orEmpty()
                    ItemLine(
                        item = item,
                        detail = detail,
                        onOpen = { opened = OpenedRow(item, detail, editable = true) },
                    )
                }
                EtaButton(
                    text = "Wochenliste füllen",
                    style = EtaButtonStyle.Secondary,
                    onClick = onPlanWeek,
                )
            }

            // The standing schedule. Unlike every list above, editing here is the
            // point: a day holds one occurrence, and this is where the task itself
            // lives — including everything the questionnaire once laid down.
            //
            // Top to bottom: the recurring notes still waiting to be finished, the
            // Wochenschema, then the tasks themselves — so whatever is filled in or
            // moved here is done with the week's free hours in sight.
            ListCard(
                section = ListSection.WIEDERKEHREND,
                count = state.recurring.size + state.incompleteRecurring.size,
                collapsed = ListSection.WIEDERKEHREND in collapsed,
                onToggle = ::toggle,
                emptyHint = "Keine wiederkehrenden Aufgaben.",
            ) {
                state.incompleteRecurring.forEach { entry ->
                    CollectionLine(entry) { detail ->
                        opened = OpenedRow(entry.item, detail, editable = true)
                    }
                }
                WeekSchemeChart(booked = booked)
                state.recurring.forEach { group ->
                    ItemLine(
                        item = group.representative,
                        detail = recurringSummary(group),
                        onOpen = { openedGroup = group },
                    )
                }
            }

            ListCard(
                section = ListSection.TAGESLISTE,
                count = state.todayBlocks.size,
                collapsed = ListSection.TAGESLISTE in collapsed,
                onToggle = ::toggle,
                emptyHint = "Heute steht nichts im Plan.",
            ) {
                state.todayBlocks.forEach { entry ->
                    BlockLine(entry) { opened = OpenedRow(entry.item, blockDetail(entry), false) }
                }
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
                state.tomorrowBlocks.forEach { entry ->
                    BlockLine(entry) { opened = OpenedRow(entry.item, blockDetail(entry), false) }
                }
                EtaText(
                    text = "Lange drücken, um die Planung wieder zu öffnen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
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
                        EtaText(
                            text = entry.name,
                            style = EtaTheme.typography.body,
                            modifier = Modifier.weight(1f),
                            // History has no card behind it to open, so the name
                            // simply gets the room it needs here.
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        EtaText(
                            text = "${entry.at.date.formatLong()} · ${entry.at.time.formatClock()}",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.success,
                        )
                    }
                }
            }

            // Right at the bottom, and the only list that looks past tomorrow.
            ListCard(
                section = ListSection.TERMINE,
                count = state.upcoming.size,
                collapsed = ListSection.TERMINE in collapsed,
                onToggle = ::toggle,
                emptyHint = "Keine Termine an späteren Tagen.",
            ) {
                state.upcoming.forEach { entry ->
                    val detail = appointmentDetail(entry)
                    ItemLine(
                        item = entry.item,
                        detail = detail,
                        detailColor = EtaTheme.colors.calendar,
                        // A block, like the Tagesliste's: the planner owns when it
                        // happens, so there is nothing to correct from here.
                        onOpen = { opened = OpenedRow(entry.item, detail, editable = false) },
                    )
                }
            }

            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }

    opened?.let { row ->
        ItemDetailDialog(
            item = row.item,
            detail = row.detail,
            editable = row.editable,
            onDismiss = { opened = null },
            onEdit = {
                editing = row.item
                opened = null
            },
            onComplete = {
                confirming = PendingAction.Complete(row.item)
                opened = null
            },
            onDelete = {
                confirming = PendingAction.Delete(row.item)
                opened = null
            },
        )
    }

    when (val pending = confirming) {
        null -> Unit
        is PendingAction.Complete -> CompleteItemDialog(
            item = pending.item,
            dayAlreadySettled = todaySettled,
            onDismiss = { confirming = null },
            onConfirm = { category, duration ->
                viewModel.completeEarly(pending.item, category, duration)
                confirming = null
            },
        )
        is PendingAction.Delete -> ConfirmDialog(
            title = "Löschen?",
            message = "„${pending.item.name}“ wird mit allen Terminen entfernt, auch mit " +
                "den schon abgehakten — die verlassen damit die Erfolgsliste. Das lässt " +
                "sich nicht rückgängig machen.",
            confirm = "Löschen",
            onDismiss = { confirming = null },
            onConfirm = {
                viewModel.delete(pending.item)
                confirming = null
            },
        )
    }

    openedGroup?.let { group ->
        RecurringGroupDialog(
            group = group,
            ownedBySettings = group.ids.any { it in SETTINGS_OWNED_ITEM_IDS },
            onDismiss = { openedGroup = null },
            onEdit = {
                editingGroup = group
                openedGroup = null
            },
            onEnd = {
                viewModel.endRecurringGroup(group)
                openedGroup = null
            },
        )
    }

    editingGroup?.let { group ->
        RecurringGroupEditDialog(
            group = group,
            definitions = state.definitions,
            setup = setup,
            subtasks = state.subtasks[group.representative.id].orEmpty(),
            foldCandidates = state.foldable.foldCandidatesExcept(*group.ids.toTypedArray()),
            onDismiss = { editingGroup = null },
            onSave = { name, note, attributes ->
                viewModel.saveRecurringGroup(group, name, note, attributes)
                editingGroup = null
            },
        )
    }

    editing?.let { item ->
        ItemEditDialog(
            item = item,
            today = viewModel.today,
            definitions = state.definitions,
            setup = setup,
            subtasks = state.subtasks[item.id].orEmpty(),
            foldCandidates = state.foldable.foldCandidatesExcept(item.id),
            onDismiss = { editing = null },
            onSaveTodo = { name, note, attributes ->
                viewModel.saveTodo(item, name, note, attributes)
                editing = null
            },
            onSaveRecurring = { name, note, attributes ->
                viewModel.saveRecurring(item, name, note, attributes)
                editing = null
            },
        )
    }
}

/**
 * A Sammelliste entry: incomplete, or how long it has before the ban.
 *
 * Shared by the Sammelliste and the recurring notes at the top of the
 * Wiederkehrend box — those still run on the same one-month clock, wherever they
 * are listed. [onOpen] receives the detail it showed, so the popup repeats it.
 */
@Composable
private fun CollectionLine(entry: CollectionEntry, onOpen: (String) -> Unit) {
    val left = when {
        entry.daysLeft == null -> null
        entry.daysLeft <= 0 -> "läuft ab"
        entry.daysLeft == 1 -> "noch 1 Tag"
        else -> "noch ${entry.daysLeft} Tage"
    }
    val detail = if (!entry.item.isConcretized) {
        listOfNotNull("unvollständig", left?.takeIf { entry.daysLeft!! <= 7 }).joinToString(" · ")
    } else {
        left.orEmpty()
    }
    ItemLine(
        item = entry.item,
        detail = detail,
        detailColor = if (entry.daysLeft != null && entry.daysLeft <= 7) {
            EtaTheme.colors.danger
        } else {
            EtaTheme.colors.textMuted
        },
        onOpen = { onOpen(detail) },
    )
}

/**
 * A row the user tapped.
 *
 * [editable] belongs to the list rather than to the card: the Sammelliste and the
 * Wochenliste hold definitions whose attributes are the user's to correct, while
 * everything else on this screen is a block, a ban or history.
 */
private data class OpenedRow(
    val item: Item,
    val detail: String,
    val editable: Boolean,
)

/** A question the popup handed on: what was asked for, and about which card. */
private sealed interface PendingAction {
    val item: Item

    data class Complete(override val item: Item) : PendingAction

    data class Delete(override val item: Item) : PendingAction
}

/**
 * What an appointment adds to its card.
 *
 * The **date** leads, unlike everywhere else on this screen: every other list is
 * one known day, so the hour alone identifies a row. Here the day is the thing
 * the user came to find out.
 */
private fun appointmentDetail(entry: BlockWithItem): String = buildString {
    append(entry.block.date.formatLong())
    append(" · ")
    append(entry.block.start.formatClock())
    append(" · ")
    append(entry.block.effectiveDuration.formatShort())
}

/** What a block adds to its card: when it stands, and whether it still does. */
private fun blockDetail(entry: BlockWithItem): String = buildString {
    append(entry.block.start.formatClock())
    append(" · ")
    append(entry.block.effectiveDuration.formatShort())
    if (entry.block.isCompleted) append(" · erledigt")
    if (entry.block.isDiscarded) append(" · ausgefallen")
}

/**
 * The six lists of `Konzept.md`, in the order it names them — and two more.
 *
 * [WIEDERKEHREND] is the standing schedule: the one place a recurring task is
 * edited as a task rather than as one day's occurrence of it.
 *
 * [TERMINE] is not one of the concept's: it is what the calendar integration
 * made necessary. Every other list on this screen is about today, tomorrow, or a
 * backlog with no date at all, so an appointment three weeks out was in the
 * database, drawn in the planner on a day nobody has opened yet, and visible
 * nowhere. Last in the order because it is the one that looks furthest ahead.
 */
private enum class ListSection(val title: String, val subtitle: String) {
    SPERRLISTE("Sperrliste", "Ein halbes Jahr gesperrt"),
    SAMMELLISTE("Sammelliste", "Alles Notierte"),
    WOCHENLISTE("Wochenliste", "Für diese Woche vorgenommen"),
    WIEDERKEHREND("Wiederkehrende Aufgaben", "Der feste Wochenplan"),
    TAGESLISTE("Tagesliste", "Heute"),
    MORGEN("Liste für Morgen", "Bestätigte Planung"),
    ERFOLG("Erfolgsliste", "Abgehakt, mit Datum und Uhrzeit"),
    TERMINE("Termine", "Aus dem Kalender, für spätere Tage"),
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

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
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
                    EtaText(text = section.title, style = EtaTheme.typography.heading)
                    EtaText(
                        text = section.subtitle,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
                EtaText(
                    text = count.toString(),
                    style = EtaTheme.typography.title,
                    color = if (count == 0) EtaTheme.colors.textMuted else EtaTheme.colors.accent,
                )
                Spacer(Modifier.size(EtaTheme.spacing.sm))
                EtaText(
                    text = if (collapsed) "▾" else "▴",
                    style = EtaTheme.typography.label,
                    color = EtaTheme.colors.textMuted,
                )
            }

            if (!collapsed) {
                if (count == 0) {
                    EtaText(
                        text = emptyHint,
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                }
                content()
            }
        }
    }
}

/**
 * One card in a list.
 *
 * Tap and long-press both open the same popup: the note asked for both gestures,
 * and having them differ would only be a thing to remember. A row is the only
 * place a long name can be cut off, so this is where reading it starts.
 */
@Composable
private fun ItemLine(
    item: Item,
    detail: String,
    detailColor: androidx.compose.ui.graphics.Color = EtaTheme.colors.textMuted,
    onOpen: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().pointerInput(item.id) {
            detectTapGestures(onTap = { onOpen() }, onLongPress = { onOpen() })
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colorOf(item.category), CircleShape),
        )
        Spacer(Modifier.size(EtaTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            EtaText(
                text = item.name,
                style = EtaTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            QuantityText(item)
            item.note?.takeIf { it.isNotBlank() }?.let { note ->
                EtaText(
                    text = note,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (detail.isNotEmpty()) {
            EtaText(text = detail, style = EtaTheme.typography.caption, color = detailColor)
        }
    }
}

@Composable
private fun BlockLine(entry: BlockWithItem, onOpen: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().pointerInput(entry.block.id) {
            detectTapGestures(onTap = { onOpen() }, onLongPress = { onOpen() })
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colorOf(entry.item.category), CircleShape),
        )
        Spacer(Modifier.size(EtaTheme.spacing.sm))
        EtaText(
            text = entry.item.name,
            style = EtaTheme.typography.body,
            color = if (entry.block.isOpen) {
                EtaTheme.colors.textPrimary
            } else {
                EtaTheme.colors.textMuted
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        EtaText(
            text = buildString {
                append(entry.block.start.formatClock())
                if (entry.block.isCompleted) append(" · erledigt")
                if (entry.block.isDiscarded) append(" · ausgefallen")
            },
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}
