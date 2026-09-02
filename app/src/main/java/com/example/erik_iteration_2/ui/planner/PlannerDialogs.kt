package com.example.erik_iteration_2.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikDurationPicker
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.components.ErikTimePicker
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalTime

private val CATEGORY_OPTIONS: List<Pair<Category?, String>> = listOf(
    Category.FOKUS to "Fokus",
    Category.NEBENBEI to "Nebenbei",
    Category.ACHTSAM to "Achtsam",
    null to "Ohne — bringt keine Punkte",
)

@Composable
private fun PlannerDialog(
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
 * Long-press editing, which `Planungsphase.md` asks for on every card.
 *
 * Name and category belong to the item and therefore change every future
 * occurrence; start and duration belong to this one block. That split is the whole
 * point of the model, so the dialog says so rather than hiding it.
 */
@Composable
fun BlockEditDialog(
    entry: BlockWithItem,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: Category?,
        start: LocalTime,
        duration: Duration,
        blockNote: String,
        itemNote: String,
    ) -> Unit,
    onRemove: () -> Unit,
) {
    var name by remember(entry.block.id) { mutableStateOf(entry.item.name) }
    var category by remember(entry.block.id) { mutableStateOf(entry.item.category) }
    var start by remember(entry.block.id) { mutableStateOf(entry.block.start) }
    var duration by remember(entry.block.id) { mutableStateOf(entry.block.effectiveDuration) }
    var blockNote by remember(entry.block.id) { mutableStateOf(entry.block.note.orEmpty()) }
    var itemNote by remember(entry.block.id) { mutableStateOf(entry.item.note.orEmpty()) }
    val recurring = entry.item.recurrenceRule != null

    PlannerDialog(title = "Bearbeiten", onDismiss = onDismiss) {
        ErikField(label = "Name") {
            ErikTextField(value = name, onValueChange = { name = it })
        }
        ErikField(
            label = "Kategorie",
            hint = "Gilt für alle künftigen Vorkommen dieser Aufgabe.",
        ) {
            ErikChoice(
                options = CATEGORY_OPTIONS,
                selected = category,
                onSelect = { category = it },
            )
        }
        ErikField(label = "Beginn") {
            ErikTimePicker(value = start, onValueChange = { start = it })
        }
        ErikField(label = "Dauer") {
            ErikDurationPicker(
                value = duration,
                onValueChange = { duration = it },
                minimum = 15.minutes,
            )
        }

        // The occurrence note comes first, because it is the one about today.
        ErikField(
            label = if (recurring) "Notiz für diesen Termin" else "Notiz",
            hint = if (recurring) "Gilt nur für den ${entry.block.date.formatLong()}." else null,
        ) {
            ErikTextField(
                value = blockNote,
                onValueChange = { blockNote = it },
                placeholder = "Was ist heute anders?",
                singleLine = false,
            )
        }
        if (recurring) {
            ErikField(
                label = "Notiz für alle Termine",
                hint = "Gehört zur Aufgabe selbst und begleitet jedes Vorkommen.",
            ) {
                ErikTextField(
                    value = itemNote,
                    onValueChange = { itemNote = it },
                    placeholder = "Was gilt immer?",
                    singleLine = false,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikButton(
                text = "Vom Tag nehmen",
                style = ErikButtonStyle.Secondary,
                onClick = onRemove,
            )
            Spacer(Modifier.weight(1f))
            ErikButton(
                text = "Sichern",
                onClick = {
                    onSave(
                        name.trim().ifEmpty { entry.item.name },
                        category,
                        start,
                        duration,
                        blockNote.trim(),
                        // A one-off has no "every occurrence" field; its single note
                        // is the occurrence one, so nothing goes to the item.
                        if (recurring) itemNote.trim() else entry.item.note.orEmpty(),
                    )
                },
            )
        }
    }
}

/**
 * A ToDo made up on the spot, for the slot it was just dropped on.
 *
 * Deliberately short: name, category, how long. Everything else a ToDo normally
 * carries — target date, priority — is already answered by the act of dropping it
 * on tomorrow at a particular hour.
 */
@Composable
fun NewTodoDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, category: Category, duration: Duration) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.FOKUS) }
    var duration by remember { mutableStateOf(1.hours) }

    PlannerDialog(title = "Neues ToDo", onDismiss = onDismiss) {
        ErikField(label = "Name") {
            ErikTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Was ist dir eingefallen?",
            )
        }
        ErikField(label = "Kategorie") {
            ErikChoice(
                options = CATEGORY_OPTIONS.dropLast(1),
                selected = category,
                onSelect = { category = it ?: Category.FOKUS },
            )
        }
        ErikField(label = "Dauer") {
            ErikDurationPicker(
                value = duration,
                onValueChange = { duration = it },
                minimum = 15.minutes,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikButton(text = "Abbrechen", style = ErikButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            ErikButton(
                text = "Einplanen",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), category, duration) },
            )
        }
    }
}

/**
 * The prompt the second revolver owes: a name, and the rate if the default is not
 * what was meant.
 */
@Composable
fun SpendDialog(
    kind: SpendKind,
    onDismiss: () -> Unit,
    onCreate: (name: String, pointsPerHour: Double) -> Unit,
) {
    var name by remember(kind) { mutableStateOf("") }
    var rate by remember(kind) { mutableStateOf(kind.pointsPerHour) }

    PlannerDialog(title = kind.label, onDismiss = onDismiss) {
        ErikField(label = "Name") {
            ErikTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Wofür?",
            )
        }
        ErikField(
            label = "Punkte pro Stunde",
            hint = when (kind) {
                SpendKind.CUSTOM_SPEND -> "Negativ: zehrt vom Konto."
                SpendKind.CUSTOM_EARN -> "Positiv: schreibt gut."
                SpendKind.SOCIAL -> "Sozialzeit ist im Setup schon eingerechnet."
            },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ErikButton(
                    text = "−0,5",
                    style = ErikButtonStyle.Secondary,
                    onClick = { rate -= 0.5 },
                )
                ErikText(
                    text = formatPoints(rate),
                    style = ErikTheme.typography.title,
                    modifier = Modifier.weight(1f),
                )
                ErikButton(
                    text = "+0,5",
                    style = ErikButtonStyle.Secondary,
                    onClick = { rate += 0.5 },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikButton(text = "Abbrechen", style = ErikButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            ErikButton(text = "Einplanen", onClick = { onCreate(name.trim(), rate) })
        }
    }
}

