package com.example.eta.ui.planner

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
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.ui.attributes.CheckRow
import com.example.eta.ui.attributes.DEFAULT_MARGIN
import com.example.eta.ui.attributes.MIN_MARGIN
import com.example.eta.ui.attributes.TodoAttributeFields
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaExpander
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaHoldButton
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatSignedPoints
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.subtask.drafts
import com.example.eta.ui.subtasks.FoldCandidate
import com.example.eta.ui.subtasks.SubtaskBuilderDialog
import com.example.eta.ui.subtasks.SubtaskSetting
import com.example.eta.ui.theme.EtaTheme
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
        EtaSurface(
            modifier = Modifier.widthIn(max = 380.dp),
            color = EtaTheme.colors.surfaceRaised,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
            ) {
                EtaText(text = title, style = EtaTheme.typography.title)
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
        returnAfter: Duration?,
        breakAfter: Duration?,
        endSound: Boolean,
        pointsPerHour: Double?,
    ) -> Unit,
    onRemove: () -> Unit,
    onCancelBlock: () -> Unit = {},
    /**
     * "Absagen" held down: called off because of something nobody could help,
     * and therefore not charged for. Only offered where there is a charge.
     */
    onCancelExcused: () -> Unit = {},
    onCopyToWeek: () -> Unit = {},
    /**
     * Whether calling this block off is going to cost anything.
     *
     * It does not when the day has not begun yet: deciding today that tomorrow's
     * Sport is not happening hands the hours back to a day that can still be
     * filled. The dialog says which of the two it is, because "Absagen" reads the
     * same either way and only one of them is a consequence.
     */
    cancellationCosts: Boolean = true,
    /** The steps inside this card, and what it may swallow. */
    subtasks: List<Subtask> = emptyList(),
    foldCandidates: List<FoldCandidate> = emptyList(),
    /**
     * Saves the group's steps, at once.
     *
     * The one route where the builder writes rather than handing its answer to a
     * form: the card already exists, so there is nothing to wait for, and the
     * builder's own Sichern is the commit point. Everything else in this dialog
     * still waits for the dialog's own.
     */
    onSubtasks: (SubtaskSetting) -> Unit = {},
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
    var returnAfter by remember(entry.block.id) { mutableStateOf(entry.block.returnAfter) }
    var breakAfter by remember(entry.block.id) { mutableStateOf(entry.block.breakAfter) }
    // On the item, not the block: whether the end of a task announces itself is a
    // property of the kind of thing it is. The row below says so.
    var endSound by remember(entry.block.id) { mutableStateOf(entry.item.endSound) }
    val recurring = entry.item.recurrenceRule != null
    // Custom Earn, Custom Spend and Social: what they are worth is their rate.
    val spend = entry.item.type == ItemType.SPEND
    var rate by remember(entry.block.id) { mutableStateOf(entry.item.pointsPerHour ?: 0.0) }
    var building by remember(entry.block.id) { mutableStateOf(false) }

    PlannerDialog(title = "Bearbeiten", onDismiss = onDismiss) {
        EtaField(label = "Name") {
            EtaTextField(value = name, onValueChange = { name = it })
        }
        // Directly under the name, because it is read together with it: the note
        // about *this* occurrence is what the name alone does not say. The one for
        // every occurrence stays further down — it is standing configuration, like
        // the category, and is edited as rarely.
        EtaField(
            label = if (recurring) "Notiz für diesen Termin" else "Notiz",
            hint = if (recurring) "Gilt nur für den ${entry.block.date.formatLong()}." else null,
        ) {
            EtaTextField(
                value = blockNote,
                onValueChange = { blockNote = it },
                placeholder = "Was ist heute anders?",
                singleLine = false,
            )
        }
        if (spend) {
            // A points entry is priced by its rate, not by a category — a category
            // on one would pay nothing, and offering it hid the one number that
            // does decide what the hour is worth.
            RateField(
                rate = rate,
                onRateChange = { rate = it },
                hint = "Gilt für diesen Eintrag. Positiv schreibt gut, negativ zehrt vom Konto.",
            )
        } else {
            EtaField(
                label = "Kategorie",
                hint = "Gilt für alle künftigen Vorkommen dieser Aufgabe.",
            ) {
                EtaChoice(
                    options = CATEGORY_OPTIONS,
                    selected = category,
                    onSelect = { category = it },
                )
            }
        }
        EtaField(label = "Beginn") {
            EtaTimePicker(value = start, onValueChange = { start = it })
        }
        EtaField(label = "Dauer") {
            EtaDurationPicker(
                value = duration,
                onValueChange = { duration = it },
                minimum = 15.minutes,
            )
        }

        // Behind a fold, like everywhere else a card is filled in: four switches
        // most blocks do not use should not stand between the duration and the
        // Sichern button. The count in the header says whether there is anything
        // inside worth opening.
        val activeExtras = listOf(endSound, travel != null, returnAfter != null, breakAfter != null)
            .count { it }
        EtaExpander(
            label = "Extras",
            hint = "Wege, Pause und der Ton am Ende — für diesen Termin.",
            summary = if (activeExtras == 0) {
                "Keine — antippen zum Öffnen."
            } else {
                "$activeExtras aktiv"
            },
        ) {
            MarginField(
                label = "Anfahrtszeit",
                hint = "Liegt vor der Aufgabe und wird mitverschoben. Gilt für diesen Termin.",
                value = travel,
                onValueChange = { travel = it },
            )
            MarginField(
                label = "Rückweg",
                hint = "Liegt direkt hinter der Aufgabe, vor der Pause. Gilt für diesen Termin.",
                value = returnAfter,
                onValueChange = { returnAfter = it },
            )
            MarginField(
                label = "Pause danach",
                hint = "Liegt ganz am Ende, hinter dem Rückweg. Gilt für diesen Termin.",
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
        }

        if (recurring) {
            EtaField(
                label = "Notiz für alle Termine",
                hint = "Gehört zur Aufgabe selbst und begleitet jedes Vorkommen.",
            ) {
                EtaTextField(
                    value = itemNote,
                    onValueChange = { itemNote = it },
                    placeholder = "Was gilt immer?",
                    singleLine = false,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            // The two ways off the day, and they mean different things.
            //
            // "Vom Tag nehmen" hands a ToDo the user dragged in back to the week
            // list: the task is still going to happen, just not here, and it costs
            // nothing. Only a dragged card can go that way — a recurring
            // occurrence has no week list to return to, and deleting it would not
            // stick anyway, since expansion runs forward from today.
            //
            // "Absagen" says it is not happening. The row stays, marked discarded,
            // which is what takes its hours out of the day and what the evening
            // charges for. Offered for every card, because a ToDo can be called
            // off as readily as a standing task.
            if (entry.block.isMovable) {
                EtaButton(
                    text = "Vom Tag nehmen",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRemove,
                )
            }
            // A hold is the excuse. Where calling off costs nothing there is
            // nothing to excuse, and the button is the plain one.
            if (cancellationCosts) {
                EtaHoldButton(
                    text = "Absagen",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCancelBlock,
                    onHold = onCancelExcused,
                )
            } else {
                EtaButton(
                    text = "Absagen",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCancelBlock,
                )
            }
            EtaText(
                text = if (cancellationCosts) {
                    "Absagen kostet die abgesagten Stunden an Punkten. " +
                        if (entry.block.isMovable) {
                            "»Vom Tag nehmen« nicht — die Aufgabe wandert zurück in die Woche. "
                        } else {
                            ""
                        } +
                        "Gedrückt halten, wenn wegen höherer Gewalt."
                } else {
                    "Der Tag läuft noch nicht — im Voraus abzusagen kostet nichts. " +
                        "Die Stunden sind dann wieder frei."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            // For the case the allotted time ran out: the occurrence keeps its
            // history, a copy carries the rest of the work into the week list.
            EtaButton(
                text = "In die Woche kopieren",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCopyToWeek,
            )
        }

        // The group's own steps. A fold of its own rather than a row inside
        // "Extras", because what is behind it is a whole menu — and unlike
        // everything else in this dialog it writes on its own Sichern, the card
        // being one that already exists.
        EtaExpander(
            label = "Subtasks",
            hint = "Schritte innerhalb der Aufgabe. Gilt für die Aufgabe, nicht nur für heute.",
            summary = when (subtasks.size) {
                0 -> "Keine"
                1 -> "1 Schritt"
                else -> "${subtasks.size} Schritte"
            },
        ) {
            subtasks.sortedBy { it.position }.forEachIndexed { index, step ->
                EtaText(
                    text = "${index + 1}. ${step.name}",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }
            EtaButton(
                text = "Subtasks erstellen / bearbeiten",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { building = true },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = "Abbrechen",
                style = EtaButtonStyle.Secondary,
                onClick = onDismiss,
            )
            Spacer(Modifier.weight(1f))
            EtaButton(
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
                        returnAfter,
                        breakAfter,
                        endSound,
                        if (spend) rate else entry.item.pointsPerHour,
                    )
                },
            )
        }
    }

    if (building) {
        SubtaskBuilderDialog(
            setting = SubtaskSetting(groupName = name, subtasks = subtasks.drafts()),
            candidates = foldCandidates,
            // The one route that offers it: here there is a group to take a step
            // out of, and a week list for it to land in.
            allowRelease = true,
            onDismiss = { building = false },
            onSave = {
                name = it.groupName
                onSubtasks(it)
                building = false
            },
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        CheckRow(
            checked = value != null,
            onCheckedChange = { onValueChange(if (it) DEFAULT_MARGIN else null) },
            label = label,
            hint = hint,
        )
        if (value != null) {
            EtaDurationPicker(
                value = value,
                onValueChange = onValueChange,
                minimum = MIN_MARGIN,
            )
        }
    }
}

/**
 * Calling off the whole day, which is one decision and therefore one dialog.
 *
 * It says what will happen to each half before it happens, because the two are
 * not the same act: the ToDos come back and can be planned again, everything
 * standing is called off and stays on the day as a cancellation. Without the
 * count and the split, "Ganzen Tag absagen" would be a button whose consequences
 * can only be found out by pressing it.
 */
@Composable
fun ClearDayDialog(
    blocks: List<BlockWithItem>,
    costs: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val returning = blocks.count { it.block.isMovable && it.item.type == ItemType.TODO }
    val cancelling = blocks.size - returning
    val cancelledHours = blocks
        .filter { it.block.isMovable.not() || it.item.type != ItemType.TODO }
        .sumOf { it.block.effectiveDuration.inWholeMinutes } / 60.0

    PlannerDialog(title = "Ganzen Tag absagen", onDismiss = onDismiss) {
        EtaText(
            text = "Alles, was heute noch offen steht, wird abgeräumt.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            if (cancelling > 0) {
                EtaText(
                    text = "· $cancelling abgesagt — wiederkehrende Aufgaben, Termine, " +
                        "Punkte-Einträge. Sie bleiben als »abgesagt« am Tag stehen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }
            if (returning > 0) {
                EtaText(
                    text = "· $returning zurück in die Wochenliste — dort an einem anderen " +
                        "Tag wieder verplanbar.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }
        }
        EtaText(
            text = if (costs && cancelledHours > 0.0) {
                "Der Tag läuft bereits: die Absagen kosten rund " +
                    "${"%.1f".format(cancelledHours)} h an Punkten. " +
                    "Der Abendrückblick lässt »höhere Gewalt« gelten."
            } else {
                "Der Tag läuft noch nicht — im Voraus abzusagen kostet nichts."
            },
            style = EtaTheme.typography.caption,
            color = if (costs && cancelledHours > 0.0) {
                EtaTheme.colors.warning
            } else {
                EtaTheme.colors.textMuted
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Tag absagen", onClick = onConfirm)
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
        EtaText(
            text = "Es ist kein Zeitslot frei, der die für »$name« vorgesehene Pause " +
                "zulässt. Trotzdem hinzufügen? Die Pause entfällt dann.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Ohne Pause einplanen", onClick = onPlaceAnyway)
        }
    }
}

/**
 * Which of the blocks under the finger was meant.
 *
 * Blocks are drawn full width at their own minute, so two sharing an hour are
 * stacked and only the topmost can ever receive a gesture — the one underneath
 * could not be opened at all. Rather than making the press pick for the user, it
 * asks: the two are equally plausible targets, and guessing would half the time
 * open the wrong card and then edit it.
 *
 * Offered only when there really is more than one. A single block under the
 * finger opens straight away, as it always did — a dialog between every long
 * press and every edit would be a tax on the ordinary case.
 */
@Composable
fun BlockChoiceDialog(
    candidates: List<BlockWithItem>,
    onDismiss: () -> Unit,
    onChoose: (BlockWithItem) -> Unit,
) {
    PlannerDialog(title = "Welche Aufgabe?", onDismiss = onDismiss) {
        EtaText(
            text = "An dieser Stelle liegen mehrere Aufgaben übereinander.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        candidates.forEach { entry ->
            EtaButton(
                text = buildString {
                    append(entry.item.name)
                    append(" · ")
                    append(entry.block.start.formatClock())
                    append("–")
                    append(minuteToLocalTime(entry.block.endMinute()).formatClock())
                },
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChoose(entry) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
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
    /** ToDos the new card can swallow as steps. */
    foldCandidates: List<FoldCandidate> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (name: String, attributes: TodoAttributes) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(TodoAttributes()) }

    PlannerDialog(title = "Neues ToDo", onDismiss = onDismiss) {
        EtaField(label = "Name") {
            EtaTextField(
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
            groupName = name,
            onGroupName = { name = it },
            foldCandidates = foldCandidates,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
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
        EtaField(label = "Name") {
            EtaTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Wofür?",
            )
        }
        RateField(
            rate = rate,
            onRateChange = { rate = it },
            hint = when (kind) {
                SpendKind.CUSTOM_SPEND -> "Negativ: zehrt vom Konto."
                SpendKind.CUSTOM_EARN -> "Positiv: schreibt gut."
                SpendKind.SOCIAL -> "Sozialzeit ist im Setup schon eingerechnet."
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Einplanen", onClick = { onCreate(name.trim(), rate) })
        }
    }
}

/**
 * The rate of a points entry, in halves.
 *
 * One composable for the two places that ask: creating the entry and editing it
 * afterwards. The second used not to exist, so a rate once chosen could neither
 * be read back nor corrected.
 */
@Composable
private fun RateField(
    rate: Double,
    onRateChange: (Double) -> Unit,
    hint: String,
) {
    EtaField(label = "Punkte pro Stunde", hint = hint) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            EtaButton(
                text = "−0,5",
                style = EtaButtonStyle.Secondary,
                onClick = { onRateChange(rate - RATE_STEP) },
            )
            EtaText(
                text = formatSignedPoints(rate),
                style = EtaTheme.typography.title,
                color = when {
                    rate > 0 -> EtaTheme.colors.success
                    rate < 0 -> EtaTheme.colors.danger
                    else -> EtaTheme.colors.textPrimary
                },
                modifier = Modifier.weight(1f),
            )
            EtaButton(
                text = "+0,5",
                style = EtaButtonStyle.Secondary,
                onClick = { onRateChange(rate + RATE_STEP) },
            )
        }
    }
}

private const val RATE_STEP = 0.5

