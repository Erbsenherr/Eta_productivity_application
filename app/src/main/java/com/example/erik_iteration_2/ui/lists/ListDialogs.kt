package com.example.erik_iteration_2.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemType
import com.example.erik_iteration_2.ui.attributes.RecurringAttributeFields
import com.example.erik_iteration_2.ui.attributes.RecurringAttributes
import com.example.erik_iteration_2.ui.attributes.TodoAttributeFields
import com.example.erik_iteration_2.ui.attributes.TodoAttributes
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlinx.datetime.LocalDate

@Composable
private fun ListDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ErikSurface(
            modifier = Modifier.widthIn(max = 380.dp),
            color = ErikTheme.colors.surfaceRaised,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
            ) {
                ErikText(text = title, style = ErikTheme.typography.title)
                content()
            }
        }
    }
}

/**
 * What a tap on a list row opens: the whole name, spelled out.
 *
 * The complaint this answers is that a long name is cut off in the row and there
 * was no way to read the rest of it. Everything the card carries comes with it,
 * since a tap on something unreadable is a request to read all of it.
 *
 * [editable] is what separates the two kinds of list. The Sammelliste and the
 * Wochenliste hold **definitions**, and a wrong duration or priority on one of
 * those is a typo — sending the user through a whole planning phase to fix a typo
 * would be the tail wagging the dog. The other four hold blocks, a ban or history,
 * and those still belong to the phase that produced them.
 *
 * There is no "Schließen": tapping outside already closes it, and a button that
 * repeats a gesture the dialog already has only crowds the two that act.
 */
@Composable
fun ItemDetailDialog(
    item: Item,
    detail: String,
    editable: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    // Deleting a definition takes its whole history with it — `planned_blocks`
    // cascades — so it asks twice rather than once.
    var confirmingDelete by remember(item.id) { mutableStateOf(false) }

    ListDialog(title = item.name, onDismiss = onDismiss) {
        val lines = buildList {
            add(
                when (item.type) {
                    ItemType.TODO -> "ToDo"
                    ItemType.RECURRING -> "Wiederkehrend"
                    ItemType.DEADLINE -> "Deadline"
                    ItemType.SPEND -> "Punkte"
                },
            )
            item.category?.let { add(categoryLabel(it)) }
            item.priority?.let { add(it.formatLong()) }
            item.estimatedDuration?.let { add(it.formatShort()) }
            item.startTime?.let { add("ab ${it.formatClock()}") }
            item.targetDate?.let { add("Ziel: ${it.formatLong()}") }
            if (detail.isNotBlank()) add(detail)
        }

        ErikText(
            text = lines.joinToString(" · "),
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textSecondary,
        )

        item.note?.takeIf { it.isNotBlank() }?.let { note ->
            ErikField(label = "Notiz") {
                ErikText(text = note, style = ErikTheme.typography.body)
            }
        }

        if (!item.isConcretized) {
            ErikText(
                text = if (editable) {
                    "Noch unvollständig — Bearbeiten füllt die fehlenden Angaben aus."
                } else {
                    "Noch unvollständig — die abendliche Planung fragt die fehlenden " +
                        "Angaben ab."
                },
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.warning,
            )
        }

        if (editable) {
            Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikButton(
                    text = if (confirmingDelete) "Wirklich?" else "Löschen",
                    style = ErikButtonStyle.Secondary,
                    onClick = { if (confirmingDelete) onDelete() else confirmingDelete = true },
                )
                Spacer(Modifier.weight(1f))
                ErikButton(text = "Bearbeiten", onClick = onEdit)
            }
        }
    }
}

/**
 * Editing a definition after the fact.
 *
 * Attributes only — nothing here plans, unplans or moves anything, which is what
 * keeps the read-only rule of this tab intact where it actually matters. The
 * fields are the concretizing step's own, so a card finished here is finished by
 * the same rules and cannot come out half-answered.
 *
 * A **recurring** card gets the recurring form and is genuinely concretized on
 * save: several weekdays become several definitions and the occurrences are laid
 * down at once. It therefore leaves the Sammelliste for the standing schedule,
 * which is what finishing a recurring task means — the dialog says so first.
 */
@Composable
fun ItemEditDialog(
    item: Item,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSaveTodo: (name: String, note: String?, attributes: TodoAttributes) -> Unit,
    onSaveRecurring: (name: String, note: String?, attributes: RecurringAttributes) -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var note by remember(item.id) { mutableStateOf(item.note.orEmpty()) }
    var todo by remember(item.id) { mutableStateOf(TodoAttributes.of(item, today)) }
    var recurring by remember(item.id) { mutableStateOf(RecurringAttributes.of(item)) }
    val isRecurring = item.type == ItemType.RECURRING

    ListDialog(title = "Bearbeiten", onDismiss = onDismiss) {
        if (isRecurring) {
            ErikText(
                text = "Wiederkehrend — mit dem Sichern zieht diese Aufgabe aus der " +
                    "Sammelliste in den festen Wochenplan, und die Termine werden sofort " +
                    "angelegt.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.accent,
            )
        }

        ErikField(label = "Name") {
            ErikTextField(value = name, onValueChange = { name = it })
        }

        if (isRecurring) {
            RecurringAttributeFields(value = recurring, onChange = { recurring = it })
        } else {
            TodoAttributeFields(value = todo, onChange = { todo = it }, today = today)
        }

        ErikField(label = "Notiz") {
            ErikTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Was gehört dazu?",
                singleLine = false,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikButton(text = "Abbrechen", style = ErikButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            ErikButton(
                text = "Sichern",
                enabled = !isRecurring || recurring.weekdays.isNotEmpty(),
                onClick = {
                    val trimmed = name.trim().ifEmpty { item.name }
                    val trimmedNote = note.trim().ifBlank { null }
                    if (isRecurring) {
                        onSaveRecurring(trimmed, trimmedNote, recurring)
                    } else {
                        onSaveTodo(trimmed, trimmedNote, todo)
                    }
                },
            )
        }
    }
}

private fun categoryLabel(category: Category): String = when (category) {
    Category.FOKUS -> "Fokus"
    Category.NEBENBEI -> "Nebenbei"
    Category.ACHTSAM -> "Achtsam"
}
