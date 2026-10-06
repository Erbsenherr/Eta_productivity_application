package com.example.eta.ui.subtasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.domain.subtask.moved
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.theme.EtaTheme
import kotlin.math.roundToInt
import kotlin.time.Duration
import java.util.UUID

/**
 * The group a form is describing: its name, and the steps inside it.
 *
 * The name travels with the list because the builder edits it — a group is a task,
 * and renaming the group renames the task. The forms cannot write it themselves
 * (the name field belongs to the screen around them), so it goes up with the
 * answer and the screen applies it.
 */
data class SubtaskSetting(
    val groupName: String,
    val subtasks: List<SubtaskDraft> = emptyList(),
    /**
     * Tasks the builder swallowed through "Task zur Subtask reduzieren".
     *
     * They are **not** retired here. Nothing in this dialog writes anything, so
     * backing out of the card it belongs to leaves them standing; the save path
     * that writes the group is what retires them, in the same breath.
     */
    val folded: List<FoldCandidate> = emptyList(),
    /**
     * Steps taken back out of the group, to become cards of their own.
     *
     * Only the planner's block dialog offers this and only it acts on it — the
     * other routes leave [SubtaskBuilderDialog]'s `allowRelease` false, because a
     * step that left the list with nobody to catch it would simply be lost.
     */
    val released: List<SubtaskDraft> = emptyList(),
)

/** A task offered to "Task zur Subtask reduzieren". */
data class FoldCandidate(
    val itemId: String,
    val name: String,
    val note: String? = null,
    /** What it added to the group's length. Null for a card that never said. */
    val duration: Duration? = null,
    /** A goal of the current week, rather than something still in the Sammelliste. */
    val inWeek: Boolean = true,
)

/**
 * The cards a screen can offer, minus the ones that must not be offered.
 *
 * [exclude] is the card being edited and anything else already part of it: a task
 * that offered to swallow itself would retire the very row being saved.
 */
fun List<Item>.foldCandidatesExcept(vararg exclude: String?): List<FoldCandidate> {
    val skip = exclude.filterNotNull().toSet()
    return filterNot { it.id in skip }.map {
        FoldCandidate(
            itemId = it.id,
            name = it.name,
            note = it.note,
            duration = it.estimatedDuration,
            inWeek = it.stage == Stage.WEEK,
        )
    }
}

/** What folding these in adds to the group's length. */
fun List<FoldCandidate>.addedDuration(): Duration =
    fold(Duration.ZERO) { sum, part -> sum + (part.duration ?: Duration.ZERO) }

/** One row while it is being edited: a key that survives reordering, and its answers. */
private data class BuilderRow(
    val key: String,
    val id: String?,
    val name: String,
    val note: String?,
)

/**
 * The Subtask-Builder: one menu, reached from every route.
 *
 * The same dialog answers "Tasks gruppieren?" after a merge and "Subtasks
 * erstellen / bearbeiten" in the Extras box, because the two are the same
 * question with a different run-up — and two dialogs for it would be two places
 * for the rules to drift apart.
 *
 * **Nothing here writes anything.** The list comes back on Sichern and the screen
 * that opened it saves it with the card, which is what lets the whole thing be
 * cancelled: a builder that deleted a row the moment it was tapped would leave a
 * half-changed group behind when the user backs out of the card.
 *
 * **Tap opens a row, a long press drags it.** The note asks for the long press to
 * edit, but reordering needs it too, and one gesture cannot do both. The
 * Growth-Tasks tab already reorders with a long press, so that is the one that
 * stays consistent; a tap has nothing else to do here, unlike in the planner where
 * it reveals the name.
 */
@Composable
fun SubtaskBuilderDialog(
    setting: SubtaskSetting,
    onDismiss: () -> Unit,
    onSave: (SubtaskSetting) -> Unit,
    title: String = "Subtasks",
    saveLabel: String = "Sichern",
    /**
     * Tasks that can be folded in as steps. Empty hides the offer, which is what a
     * screen that has no list of them passes.
     */
    candidates: List<FoldCandidate> = emptyList(),
    /** Whether a step may be taken back out as a card of its own. */
    allowRelease: Boolean = false,
    /**
     * What a merge has to ask on top of the list: the group's category, priority,
     * duration and — where both parts carried one — which deadline it keeps.
     *
     * A slot rather than a second dialog, because "Tasks gruppieren?" *is* this
     * menu with a few more questions, and building it twice would be two places
     * for the list and the reordering to drift.
     */
    footer: (@Composable () -> Unit)? = null,
) {
    var name by remember { mutableStateOf(setting.groupName) }
    var rows by remember {
        mutableStateOf(
            setting.subtasks.map {
                BuilderRow(key = it.id ?: UUID.randomUUID().toString(), id = it.id, name = it.name, note = it.note)
            },
        )
    }
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var folded by remember { mutableStateOf(setting.folded) }
    var released by remember { mutableStateOf(setting.released) }
    var reducing by remember { mutableStateOf(false) }

    EtaDialog(title = title, onDismiss = onDismiss) {
        EtaField(label = "Name der Gruppe") {
            EtaTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Wie heißt die Aufgabe?",
            )
        }

        EtaText(
            text = "Subtasks bestehen nur aus Name und Notiz — keine Dauer, keine Kategorie, " +
                "keine Punkte. Antippen zum Bearbeiten, gedrückt halten und ziehen zum Sortieren.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )

        if (rows.isEmpty()) {
            EtaText(
                text = "Noch keine Subtasks.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
        } else {
            SubtaskRows(
                rows = rows,
                onReorder = { rows = it },
                onOpen = { editing = it },
            )
        }

        EtaButton(
            text = "Weitere Subtask hinzufügen",
            style = EtaButtonStyle.Secondary,
            onClick = { adding = true },
        )

        val offered = candidates.filterNot { candidate -> folded.any { it.itemId == candidate.itemId } }
        if (offered.isNotEmpty()) {
            EtaButton(
                text = "Task zur Subtask reduzieren",
                style = EtaButtonStyle.Secondary,
                onClick = { reducing = true },
            )
        }
        if (released.isNotEmpty()) {
            EtaText(
                text = "Wandert beim Sichern in die Wochenliste: " +
                    released.joinToString(", ") { it.name } +
                    " — dort noch ohne Kategorie, Priorität und Dauer.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
        }
        if (folded.isNotEmpty()) {
            EtaText(
                text = "Wird beim Sichern zu Subtasks: " + folded.joinToString(", ") { it.name } +
                    " — diese Tasks verschwinden dann aus ihren Listen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }

        footer?.invoke()

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = saveLabel,
                onClick = {
                    onSave(
                        SubtaskSetting(
                            groupName = name.trim(),
                            subtasks = rows
                                .filter { it.name.isNotBlank() }
                                .map { SubtaskDraft(id = it.id, name = it.name.trim(), note = it.note) },
                            folded = folded,
                            released = released,
                        ),
                    )
                },
            )
        }
    }

    if (reducing) {
        ReduceTasksDialog(
            candidates = candidates.filterNot { c -> folded.any { it.itemId == c.itemId } },
            onDismiss = { reducing = false },
            onConfirm = { chosen ->
                rows = rows + chosen.map {
                    BuilderRow(
                        key = UUID.randomUUID().toString(),
                        id = null,
                        name = it.name,
                        note = it.note,
                    )
                }
                folded = folded + chosen
                reducing = false
            },
        )
    }

    if (adding) {
        SubtaskEditDialog(
            row = null,
            onDismiss = { adding = false },
            onSave = { edited ->
                rows = rows + edited.copy(key = UUID.randomUUID().toString())
                adding = false
            },
        )
    }

    editing?.let { key ->
        rows.firstOrNull { it.key == key }?.let { row ->
            SubtaskEditDialog(
                row = row,
                onDismiss = { editing = null },
                onSave = { edited ->
                    rows = rows.map { if (it.key == key) edited else it }
                    editing = null
                },
                onDelete = {
                    rows = rows.filterNot { it.key == key }
                    editing = null
                },
                onRelease = if (allowRelease) {
                    {
                        rows = rows.filterNot { it.key == key }
                        released = released + SubtaskDraft(name = row.name, note = row.note)
                        editing = null
                    }
                } else {
                    null
                },
            )
        } ?: run { editing = null }
    }
}

/**
 * The list, with the reorder gesture the Growth-Tasks tab uses.
 *
 * Everything the gesture reads goes through a [rememberUpdatedState] or a state
 * object — the rule earned twice in step 12: a `pointerInput` lambda keeps
 * whatever it closed over when it was launched.
 */
@Composable
private fun SubtaskRows(
    rows: List<BuilderRow>,
    onReorder: (List<BuilderRow>) -> Unit,
    onOpen: (String) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val rowHeight = remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { EtaTheme.spacing.xs.toPx() }

    val current = rememberUpdatedState(rows)
    val commit = rememberUpdatedState(onReorder)
    val open = rememberUpdatedState(onOpen)
    val step = rememberUpdatedState(gap)

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        rows.forEachIndexed { index, row ->
            val dragging = draggingKey == row.key
            SubtaskRow(
                row = row,
                position = index + 1,
                dragging = dragging,
                onClick = { open.value(row.key) },
                modifier = Modifier
                    .onSizeChanged { if (rowHeight.intValue == 0) rowHeight.intValue = it.height }
                    .offset { IntOffset(0, if (dragging) dragOffset.floatValue.roundToInt() else 0) }
                    .pointerInput(row.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingKey = row.key
                                dragOffset.floatValue = 0f
                            },
                            onDrag = { _, amount ->
                                dragOffset.floatValue += amount.y
                                val pitch = rowHeight.intValue + step.value
                                if (pitch <= 0f) return@detectDragGesturesAfterLongPress
                                val steps = (dragOffset.floatValue / pitch).roundToInt()
                                if (steps == 0) return@detectDragGesturesAfterLongPress

                                val list = current.value
                                val from = list.indexOfFirst { it.key == row.key }
                                if (from < 0) return@detectDragGesturesAfterLongPress
                                val to = (from + steps).coerceIn(0, list.size - 1)
                                if (to == from) return@detectDragGesturesAfterLongPress

                                commit.value(list.moved(from, to))
                                dragOffset.floatValue -= (to - from) * pitch
                            },
                            onDragEnd = {
                                draggingKey = null
                                dragOffset.floatValue = 0f
                            },
                            onDragCancel = {
                                draggingKey = null
                                dragOffset.floatValue = 0f
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun SubtaskRow(
    row: BuilderRow,
    position: Int,
    dragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        color = if (dragging) EtaTheme.colors.surfaceRaised else EtaTheme.colors.surface,
        contentPadding = EtaTheme.spacing.md,
    ) {
        Row(
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EtaText(
                text = "$position.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            Spacer(Modifier.width(EtaTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                EtaText(text = row.name, style = EtaTheme.typography.body)
                row.note?.takeIf { it.isNotBlank() }?.let {
                    EtaText(
                        text = it,
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** Name and note, which is everything a subtask has. */
@Composable
private fun SubtaskEditDialog(
    row: BuilderRow?,
    onDismiss: () -> Unit,
    onSave: (BuilderRow) -> Unit,
    onDelete: (() -> Unit)? = null,
    /** Takes the step out of the group and back into the week list. */
    onRelease: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(row?.name ?: "") }
    var note by remember { mutableStateOf(row?.note ?: "") }

    EtaDialog(
        title = if (row == null) "Neue Subtask" else "Subtask bearbeiten",
        onDismiss = onDismiss,
    ) {
        EtaField(label = "Name") {
            EtaTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Was ist zu tun?",
            )
        }
        EtaField(label = "Notiz", hint = "Optional.") {
            EtaTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Nichts weiter",
                singleLine = false,
            )
        }
        if (onRelease != null) {
            EtaButton(
                text = "In die Wochenliste",
                style = EtaButtonStyle.Secondary,
                onClick = onRelease,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            if (onDelete != null) {
                EtaButton(text = "Löschen", style = EtaButtonStyle.Secondary, onClick = onDelete)
            } else {
                EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            }
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Sichern",
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        BuilderRow(
                            key = row?.key ?: "",
                            id = row?.id,
                            name = name.trim(),
                            note = note.trim().takeIf { it.isNotBlank() },
                        ),
                    )
                },
            )
        }
    }
}
