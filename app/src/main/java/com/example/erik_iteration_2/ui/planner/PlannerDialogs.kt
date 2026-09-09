package com.example.erik_iteration_2.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.ui.attributes.CheckRow
import com.example.erik_iteration_2.ui.attributes.DEFAULT_MARGIN
import com.example.erik_iteration_2.ui.attributes.MIN_MARGIN
import com.example.erik_iteration_2.ui.attributes.TodoAttributeFields
import com.example.erik_iteration_2.ui.attributes.TodoAttributes
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
import kotlinx.datetime.LocalDate
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
 *
 * Taking the card off the day comes in two shapes, and which one is offered
 * follows from where the block came from — see the buttons at the bottom.
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
        travelBefore: Duration?,
        breakAfter: Duration?,
        endSound: Boolean,
    ) -> Unit,
    onRemove: () -> Unit,
    onCancelBlock: () -> Unit = {},
    onUncancelBlock: () -> Unit = {},
    onCopyToWeek: () -> Unit = {},
) {
    var name by remember(entry.block.id) { mutableStateOf(entry.item.name) }
    var category by remember(entry.block.id) { mutableStateOf(entry.item.category) }
    var start by remember(entry.block.id) { mutableStateOf(entry.block.start) }
    var duration by remember(entry.block.id) { mutableStateOf(entry.block.effectiveDuration) }
    var blockNote by remember(entry.block.id) { mutableStateOf(entry.block.note.orEmpty()) }
    var itemNote by remember(entry.block.id) { mutableStateOf(entry.item.note.orEmpty()) }
    // The margins belong to this occurrence: adding a journey to today should not
    // add one to every Tuesday. The definition keeps its own defaults.
    var travel by remember(entry.block.id) { mutableStateOf(entry.block.travelBefore) }
    var breakAfter by remember(entry.block.id) { mutableStateOf(entry.block.breakAfter) }
    // On the item, not the block: whether the end of a task announces itself is a
    // property of the kind of thing it is. The row below says so.
    var endSound by remember(entry.block.id) { mutableStateOf(entry.item.endSound) }
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

        MarginField(
            label = "Anfahrtszeit",
            hint = "Liegt vor der Aufgabe und wird mitverschoben. Gilt für diesen Termin.",
            value = travel,
            onValueChange = { travel = it },
        )
        MarginField(
            label = "Pause danach",
            hint = "Liegt hinter der Aufgabe und wird mitverschoben. Gilt für diesen Termin.",
            value = breakAfter,
            onValueChange = { breakAfter = it },
        )
        CheckRow(
            checked = endSound,
            onCheckedChange = { endSound = it },
            label = "Ton am Ende",
            hint = if (recurring) {
                "Gilt für alle künftigen Vorkommen dieser Aufgabe."
            } else {
                "Meldet sich, wenn die geplante Zeit abgelaufen ist."
            },
        )

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

        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            // A ToDo the user dragged in can simply go: nothing will lay it down
            // again, and it goes back to the week list to be planned elsewhere.
            // A recurring or imported occurrence cannot — expansion runs forward
            // from today and would recreate it — so that one is called off,
            // which leaves the row standing and the schedule intact.
            if (entry.block.isDiscarded) {
                // Already called off. The row is still there — it has to be — so
                // the way back is a button rather than a re-plan.
                ErikButton(
                    text = "Doch einplanen",
                    style = ErikButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onUncancelBlock,
                )
            } else if (entry.block.isMovable) {
                ErikButton(
                    text = "Vom Tag nehmen",
                    style = ErikButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRemove,
                )
            } else {
                ErikButton(
                    text = "Absagen",
                    style = ErikButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCancelBlock,
                )
            }

            // For the case the allotted time ran out: the occurrence keeps its
            // history, a copy carries the rest of the work into the week list.
            ErikButton(
                text = "In die Woche kopieren",
                style = ErikButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCopyToWeek,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikButton(
                text = "Abbrechen",
                style = ErikButtonStyle.Secondary,
                onClick = onDismiss,
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
                        travel,
                        breakAfter,
                        endSound,
                    )
                },
            )
        }
    }
}

/**
 * One margin: off, or a length.
 *
 * A checkbox rather than a duration that may be zero, because "no journey" and "a
 * journey of no minutes" are not the same statement — and the timeline draws a
 * container only where there is something to enclose.
 *
 * Its own copy rather than the shared one from `ui/attributes/`, because these
 * two belong to **this occurrence** while everything in that file belongs to the
 * definition. The hints say which, and that difference is the whole reason the
 * block dialog is not simply the shared form.
 */
@Composable
private fun MarginField(
    label: String,
    hint: String,
    value: Duration?,
    onValueChange: (Duration?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
        CheckRow(
            checked = value != null,
            onCheckedChange = { onValueChange(if (it) DEFAULT_MARGIN else null) },
            label = label,
            hint = hint,
        )
        if (value != null) {
            ErikDurationPicker(
                value = value,
                onValueChange = onValueChange,
                minimum = MIN_MARGIN,
            )
        }
    }
}

/**
 * The one question a full day raises: the task fits, its journey fits, the break
 * does not.
 *
 * Answering yes plans it without the break. The journey is never on the table —
 * it is the time the task takes to reach, and a task planned without it is simply
 * planned wrong.
 */
@Composable
fun BreakDroppedDialog(
    name: String,
    onDismiss: () -> Unit,
    onPlaceAnyway: () -> Unit,
) {
    PlannerDialog(title = "Kein Platz für die Pause", onDismiss = onDismiss) {
        ErikText(
            text = "Es ist kein Zeitslot frei, der die für »$name« vorgesehene Pause " +
                "zulässt. Trotzdem hinzufügen? Die Pause entfällt dann.",
            style = ErikTheme.typography.body,
            color = ErikTheme.colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikButton(text = "Abbrechen", style = ErikButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            ErikButton(text = "Ohne Pause einplanen", onClick = onPlaceAnyway)
        }
    }
}

/**
 * A ToDo made up on the spot, for the slot it was just dropped on.
 *
 * The concretizing step's own form, minus the target date: dropping it on an hour
 * has already answered when it happens, and asking again would invite two
 * different answers. Everything else it can carry — the journey, the break, the
 * sound at the end — it can carry from here too, because a task invented on the
 * spot is no less a task.
 */
@Composable
fun NewTodoDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onCreate: (name: String, attributes: TodoAttributes) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(TodoAttributes()) }

    PlannerDialog(title = "Neues ToDo", onDismiss = onDismiss) {
        ErikField(label = "Name") {
            ErikTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Was ist dir eingefallen?",
            )
        }

        TodoAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            today = today,
            showTargetDate = false,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikButton(text = "Abbrechen", style = ErikButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            ErikButton(
                text = "Einplanen",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), attributes) },
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

