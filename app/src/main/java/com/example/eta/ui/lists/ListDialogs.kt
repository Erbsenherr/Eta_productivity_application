package com.example.eta.ui.lists

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.domain.recurrence.RecurringOverlap
import com.example.eta.domain.reward.yieldOf
import com.example.eta.domain.recurrence.Rhythm
import com.example.eta.domain.recurrence.nextFreeStart
import com.example.eta.domain.recurrence.recurringOverlaps
import com.example.eta.domain.recurrence.slotOccupancy
import com.example.eta.domain.recurrence.weekOccupancy
import com.example.eta.domain.setup.UserSetup
import com.example.eta.ui.attributes.CategoryField
import com.example.eta.ui.attributes.DEFAULT_DURATION
import com.example.eta.ui.attributes.MIN_TASK_DURATION
import com.example.eta.ui.attributes.RecurringAttributeFields
import com.example.eta.domain.growth.growthSetting
import com.example.eta.domain.growth.quantityLabel
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.growthIssuesFor
import com.example.eta.ui.attributes.TodoAttributeFields
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.QuantityText
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDurationPicker
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.components.EtaTrashButton
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatWeekdays
import com.example.eta.ui.subtasks.FoldCandidate
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.ui.subtasks.MorningRoutineSteps
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
private fun ListDialog(
    title: String,
    onDismiss: () -> Unit,
    /** The top right corner, where a destructive action lives rather than in the button row. */
    trailing: (@Composable () -> Unit)? = null,
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
                Row(verticalAlignment = Alignment.Top) {
                    EtaText(
                        text = title,
                        style = EtaTheme.typography.title,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
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
 *
 * The two that act are **Erledigt** and **Bearbeiten** — finishing a card early
 * is the ordinary thing to want here, and there was no way to do it at all
 * without waiting for the evening. Deleting is neither of those and has moved to
 * the wastebasket in the corner: it is the one thing on this screen that destroys
 * something, and it should not sit where a thumb aiming at "Bearbeiten" lands.
 * Both of them ask before they happen; the screen puts the question, so the two
 * confirmations are not a second dialog inside this one.
 */
@Composable
fun ItemDetailDialog(
    item: Item,
    detail: String,
    editable: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {},
    onComplete: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    // A standing task is never finished — checking one off would retire the
    // definition and with it every occurrence still to come. It is ended on the
    // Wiederkehrend list instead, which is where a task itself lives.
    val completable = editable && item.type != ItemType.RECURRING

    ListDialog(
        title = item.name,
        onDismiss = onDismiss,
        trailing = if (editable) {
            { EtaTrashButton(onClick = onDelete) }
        } else {
            null
        },
    ) {
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
            item.targetDate?.let { add("Frei ab: ${it.formatLong()}") }
            item.deadlineAt?.let {
                val at = it.toLocalDateTime(TimeZone.currentSystemDefault())
                add("Deadline: ${at.date.formatLong()} ${at.time.formatClock()}")
            }
            if (detail.isNotBlank()) add(detail)
        }

        EtaText(
            text = lines.joinToString(" · "),
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )

        QuantityText(item)
        item.note?.takeIf { it.isNotBlank() }?.let { note ->
            EtaField(label = "Notiz") {
                EtaText(text = note, style = EtaTheme.typography.body)
            }
        }

        if (!item.isConcretized) {
            EtaText(
                text = if (editable) {
                    "Noch unvollständig — Bearbeiten füllt die fehlenden Angaben aus."
                } else {
                    "Noch unvollständig — die abendliche Planung fragt die fehlenden " +
                        "Angaben ab."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }

        if (editable) {
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = "Bearbeiten",
                    style = EtaButtonStyle.Secondary,
                    onClick = onEdit,
                )
                Spacer(Modifier.weight(1f))
                if (completable) {
                    EtaButton(text = "Erledigt", onClick = onComplete)
                }
            }
        }
    }
}

/**
 * Finishing a card early, and the two answers that may still be owed.
 *
 * A card that was never filled in — a Quick-Add note, caught in three words and
 * never asked anything since — has no category and no length, and those are
 * precisely the two figures the yield is made of. Ticking it off without them
 * would put it in the Erfolgsliste for nothing at all, which is the wrong answer
 * to "I did this": the work happened, and the app has no way to price it unless
 * the user says what it was. So the two are asked here, and only here — every
 * card that already carries them is simply confirmed.
 *
 * The figure is shown while the answers are being given, because it is the
 * consequence of them and there is no reason to make anyone work it out. It is
 * not credited here: `ReevaluationService` remains the only thing that writes
 * HARVEST rows, so this is booked with the rest of the evening.
 */
@Composable
fun CompleteItemDialog(
    item: Item,
    /** Whether the day has already been settled, and so cannot harvest this any more. */
    dayAlreadySettled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Category, Duration) -> Unit,
) {
    val owesAnswers = !item.isConcretized
    var category by remember(item.id) { mutableStateOf(item.category ?: Category.FOKUS) }
    var duration by remember(item.id) {
        mutableStateOf(item.estimatedDuration ?: DEFAULT_DURATION)
    }
    val points = yieldOf(category, duration)
    val pointsVisible = LocalPointsVisible.current

    ListDialog(title = "Erledigt?", onDismiss = onDismiss) {
        EtaText(
            text = "„${item.name}“ wandert in die Erfolgsliste, mit dem heutigen Datum " +
                "und der Uhrzeit von jetzt.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )

        if (owesAnswers) {
            EtaText(
                text = if (pointsVisible) {
                    "Diese Karte ist nie ausgefüllt worden. Wofür sie zählt und wie " +
                        "lange sie gedauert hat, entscheidet die Punkte — also bitte beides " +
                        "noch nachtragen."
                } else {
                    "Diese Karte ist nie ausgefüllt worden. Bitte noch nachtragen, wofür " +
                        "sie zählt und wie lange sie gedauert hat."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
            CategoryField(value = category, onChange = { category = it })
            EtaField(label = "Gedauert hat es") {
                EtaDurationPicker(
                    value = duration,
                    onValueChange = { duration = it },
                    minimum = MIN_TASK_DURATION,
                )
            }
        }

        if (pointsVisible) {
            EtaText(
                text = if (dayAlreadySettled) {
                    "Das wären ${formatPoints(points)} Punkte — heute ist aber schon " +
                        "abgerechnet, also werden sie sofort gutgeschrieben."
                } else {
                    "Das bringt ${formatPoints(points)} Punkte, gebucht heute Abend mit " +
                        "dem Rest des Tages."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.accent,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Erledigt", onClick = { onConfirm(category, duration) })
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
    /** The standing schedule, for warning about overlaps while a recurring note is filled in. */
    definitions: List<Item> = emptyList(),
    /** The setup, for the night the Wochenschema books. */
    setup: UserSetup? = null,
    /** The steps this card already has, so the builder opens on them. */
    subtasks: List<Subtask> = emptyList(),
    /** ToDos this card can swallow as steps. */
    foldCandidates: List<FoldCandidate> = emptyList(),
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var note by remember(item.id) { mutableStateOf(item.note.orEmpty()) }
    var todo by remember(item.id) { mutableStateOf(TodoAttributes.of(item, today, subtasks)) }
    var recurring by remember(item.id) { mutableStateOf(RecurringAttributes.of(item, subtasks)) }
    val isRecurring = item.type == ItemType.RECURRING

    ListDialog(title = "Bearbeiten", onDismiss = onDismiss) {
        if (isRecurring) {
            EtaText(
                text = "Wiederkehrend — mit dem Sichern zieht diese Aufgabe aus der " +
                    "Sammelliste in den festen Wochenplan, und die Termine werden sofort " +
                    "angelegt.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.accent,
            )
        }

        EtaField(label = "Name") {
            EtaTextField(value = name, onValueChange = { name = it })
        }
        // Under the name rather than under the whole form: it is what says more
        // about the task than its name can, and is read together with it.
        EtaField(label = "Notiz") {
            EtaTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Was gehört dazu?",
                singleLine = false,
            )
        }

        if (isRecurring) {
            val overlaps = remember(recurring, definitions) {
                recurringOverlaps(recurring.slots(), definitions, ignoreIds = setOf(item.id))
            }
            val growthIssues = remember(recurring, definitions) {
                growthIssuesFor(
                    attributes = recurring,
                    definitions = definitions,
                    ignoreIds = setOf(item.id),
                )
            }
            val booked = remember(definitions, setup, item.id) {
                weekOccupancy(definitions, setup, ignoreIds = setOf(item.id))
            }
            SchemePreview(booked = booked, attributes = recurring, overlaps = overlaps)
            RecurringAttributeFields(
                value = recurring,
                onChange = { recurring = it },
                overlaps = overlaps,
                findNextFree = { nextFreeStart(it.slots(), booked) },
                allowGrowth = true,
                growthIssues = growthIssues,
                groupName = name,
                onGroupName = { name = it },
                foldCandidates = foldCandidates,
            )
        } else {
            TodoAttributeFields(
                value = todo,
                onChange = { todo = it },
                today = today,
                groupName = name,
                onGroupName = { name = it },
                foldCandidates = foldCandidates,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
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

/**
 * A ToDo made from nothing, by holding down the Sammelliste or the Wochenliste.
 *
 * Created **already concretized**, for the reason the weekly planning's own
 * "Neues ToDo" is: a bare note in the week list would sit there unplannable,
 * since the revolver only offers finished cards. The form is that dialog's form,
 * which is the concretizing step's.
 *
 * [intoWeek] only changes what the dialog says about where the card will land.
 */
@Composable
fun TodoCreateDialog(
    today: LocalDate,
    intoWeek: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, note: String?, attributes: TodoAttributes) -> Unit,
    foldCandidates: List<FoldCandidate> = emptyList(),
) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(TodoAttributes()) }

    ListDialog(title = "Neue Aufgabe", onDismiss = onDismiss) {
        EtaText(
            text = if (intoWeek) {
                "Kommt direkt in die Wochenliste und kann heute Abend eingeplant werden."
            } else {
                "Landet in der Sammelliste."
            },
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.accent,
        )

        EtaField(label = "Name") {
            EtaTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Was steht an?",
            )
        }
        EtaField(label = "Notiz") {
            EtaTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Was gehört dazu?",
                singleLine = false,
            )
        }

        TodoAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            today = today,
            groupName = name,
            onGroupName = { name = it },
            foldCandidates = foldCandidates,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Anlegen",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), note.trim().ifBlank { null }, attributes) },
            )
        }
    }
}

/**
 * A standing task made from nothing — by holding down the Wiederkehrend box, or
 * from the Belohn-o-mat's binding menu.
 *
 * The ordinary recurring form, Wochenschema and overlap warning included, so the
 * hour is chosen with the week's free stretches in sight. Finished on the spot
 * rather than left as a note for the evening: someone who held the box down to
 * make a standing task has the answers now.
 */
@Composable
fun RecurringCreateDialog(
    definitions: List<Item>,
    onDismiss: () -> Unit,
    onCreate: (name: String, note: String?, attributes: RecurringAttributes) -> Unit,
    /** The setup, for the night the Wochenschema books. Null draws no night. */
    setup: UserSetup? = null,
    foldCandidates: List<FoldCandidate> = emptyList(),
) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(RecurringAttributes()) }

    val overlaps = remember(attributes, definitions) {
        recurringOverlaps(attributes.slots(), definitions)
    }
    val growthIssues = remember(attributes, definitions) {
        growthIssuesFor(attributes = attributes, definitions = definitions, order = null)
    }
    val booked = remember(definitions, setup) { weekOccupancy(definitions, setup) }

    ListDialog(title = "Neue wiederkehrende Aufgabe", onDismiss = onDismiss) {
        EtaText(
            text = "Kommt in den festen Wochenplan; die Termine werden sofort angelegt.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.accent,
        )

        EtaField(label = "Name") {
            EtaTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Was kehrt wieder?",
            )
        }
        EtaField(label = "Notiz") {
            EtaTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Was gehört dazu?",
                singleLine = false,
            )
        }

        SchemePreview(booked = booked, attributes = attributes, overlaps = overlaps)
        RecurringAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            overlaps = overlaps,
            findNextFree = { nextFreeStart(it.slots(), booked) },
            allowGrowth = true,
            growthIssues = growthIssues,
            groupName = name,
            onGroupName = { name = it },
            foldCandidates = foldCandidates,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Erstellen",
                enabled = name.isNotBlank() && attributes.weekdays.isNotEmpty(),
                onClick = { onCreate(name.trim(), note.trim().ifBlank { null }, attributes) },
            )
        }
    }
}

/**
 * How a standing task repeats, in one line: "Mo–Fr · 09:00 · 3 h 30 min".
 *
 * The rhythm is only named when it is not the ordinary weekly one.
 */
fun recurringSummary(group: RecurringGroup): String = buildString {
    append(formatWeekdays(group.weekdays))
    when (val rhythm = group.rhythm) {
        Rhythm.Weekly -> Unit
        is Rhythm.Biweekly -> append(" (alle 2 Wochen)")
        is Rhythm.Monthly -> append(" (${rhythm.weekOfMonth}. im Monat)")
    }
    group.representative.startTime?.let { append(" · ${it.formatClock()}") }
    group.representative.estimatedDuration?.let { append(" · ${it.formatShort()}") }
    group.representative.repeatUntil?.let { append(" · bis ${it.formatLong()}") }
    // A growth task is an ordinary standing task and belongs in this list; what
    // it has extra is where its length is going, and the row is the place to say
    // so rather than making the user open it to find out.
    group.representative.growthSetting?.let { append(" → ${it.target.formatShort()}") }
    group.representative.quantityLabel()?.let { append(" · $it") }
}

/**
 * A standing task, tapped in the list.
 *
 * [ownedBySettings] marks the two that hang off the night — Bettfertig machen and
 * the Morgenroutine. They are shown so the list is complete, and changed where
 * the night is: in the settings, which regenerate them from the sleep and wake
 * times. The one exception is the routine's **steps** ([morningSteps] non-null):
 * those are the user's, not derived from anything, and can be changed here too.
 */
@Composable
fun RecurringGroupDialog(
    group: RecurringGroup,
    ownedBySettings: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onEnd: () -> Unit,
    morningSteps: List<SubtaskDraft>? = null,
    onMorningSteps: (List<SubtaskDraft>) -> Unit = {},
) {
    val item = group.representative
    var confirmingEnd by remember(group.ids) { mutableStateOf(false) }
    val pointsVisible = LocalPointsVisible.current

    ListDialog(title = item.name, onDismiss = onDismiss) {
        EtaText(
            text = buildList {
                add("Wiederkehrend")
                (item.category?.let(::categoryLabel) ?: "ohne Punkte".takeIf { pointsVisible })
                    ?.let(::add)
                add(recurringSummary(group))
            }.joinToString(" · "),
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )

        QuantityText(item)
        item.note?.takeIf { it.isNotBlank() }?.let { note ->
            EtaField(label = "Notiz") {
                EtaText(text = note, style = EtaTheme.typography.body)
            }
        }

        if (ownedBySettings) {
            if (morningSteps != null) {
                EtaField(
                    label = "Schritte",
                    hint = "Im Routine-Modus: einer nach dem anderen. Gilt für jeden Morgen.",
                ) {
                    MorningRoutineSteps(steps = morningSteps, onSave = onMorningSteps)
                }
            }
            EtaText(
                text = if (morningSteps != null) {
                    "Uhrzeit und Dauer hängen an deinen Schlafzeiten und werden in den " +
                        "Einstellungen unter „Schlaf und Morgen“ geändert."
                } else {
                    "Hängt an deinen Schlafzeiten und wird in den Einstellungen " +
                        "unter „Schlaf und Morgen“ geändert."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            return@ListDialog
        }

        if (confirmingEnd) {
            EtaText(
                text = "Nach den schon bestätigten Tagen gibt es keine Termine mehr. " +
                    "Erledigtes bleibt in der Erfolgsliste.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.danger,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = if (confirmingEnd) "Wirklich beenden" else "Beenden",
                style = EtaButtonStyle.Secondary,
                onClick = { if (confirmingEnd) onEnd() else confirmingEnd = true },
            )
            Spacer(Modifier.weight(1f))
            EtaButton(text = "Bearbeiten", onClick = onEdit)
        }
    }
}

/**
 * Editing a standing task itself — every future occurrence, not one day of it.
 *
 * The rule the user set, said before it happens: what stands on the confirmed
 * days stays as it is, and every day after that is laid down again from the new
 * answers. The overlap warning is live, against every other standing task.
 */
@Composable
fun RecurringGroupEditDialog(
    group: RecurringGroup,
    definitions: List<Item>,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String?, attributes: RecurringAttributes) -> Unit,
    /** The setup, for the night the Wochenschema books. Null draws no night. */
    setup: UserSetup? = null,
    /** The title, where "Wiederkehrende Aufgabe" is not what this one is. */
    title: String = "Wiederkehrende Aufgabe",
    /** The steps this task already has, so the builder opens on them. */
    subtasks: List<Subtask> = emptyList(),
    /** ToDos this task can swallow as steps. */
    foldCandidates: List<FoldCandidate> = emptyList(),
) {
    val item = group.representative
    var name by remember(group.ids) { mutableStateOf(item.name) }
    var note by remember(group.ids) { mutableStateOf(item.note.orEmpty()) }
    var attributes by remember(group.ids) {
        mutableStateOf(RecurringAttributes.of(item, subtasks).copy(weekdays = group.weekdays))
    }
    val overlaps = remember(attributes, definitions) {
        recurringOverlaps(attributes.slots(group.rhythm), definitions, ignoreIds = group.ids)
    }
    val growthIssues = remember(attributes, definitions) {
        growthIssuesFor(
            attributes = attributes,
            definitions = definitions,
            rhythm = group.rhythm,
            order = item.growthOrder,
            ignoreIds = group.ids,
        )
    }

    ListDialog(title = title, onDismiss = onDismiss) {
        EtaText(
            text = "Gilt für alle künftigen Termine. Was heute — und morgen, wenn die " +
                "Planung schon bestätigt ist — im Plan steht, bleibt so.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.accent,
        )

        EtaField(label = "Name") {
            EtaTextField(value = name, onValueChange = { name = it })
        }
        EtaField(label = "Notiz") {
            EtaTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "Was gehört dazu?",
                singleLine = false,
            )
        }

        val booked = remember(definitions, setup, group.ids) {
            weekOccupancy(definitions, setup, ignoreIds = group.ids)
        }
        SchemePreview(
            booked = booked,
            attributes = attributes,
            overlaps = overlaps,
            rhythm = group.rhythm,
        )
        RecurringAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            allowNoCategory = true,
            overlaps = overlaps,
            findNextFree = { nextFreeStart(it.slots(group.rhythm), booked) },
            // A standing task is exactly what can grow, and this is where one is
            // edited — including switching growth off again.
            allowGrowth = true,
            growthIssues = growthIssues,
            groupName = name,
            onGroupName = { name = it },
            foldCandidates = foldCandidates,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Sichern",
                enabled = attributes.weekdays.isNotEmpty(),
                onClick = {
                    onSave(
                        name.trim().ifEmpty { item.name },
                        note.trim().ifBlank { null },
                        attributes,
                    )
                },
            )
        }
    }
}

/**
 * The Wochenschema inside an editor: the week without this task, and the task as
 * the form now describes it drawn over it.
 *
 * A dialog covers the list the scheme lives in, so consulting it while editing —
 * the reason it was asked for — means bringing it along. It follows the form
 * live, so a time can be moved until the mark sits in the free part of the week.
 */
@Composable
internal fun SchemePreview(
    /** The week without the task being edited — also what the free-slot search reads. */
    booked: Map<DayOfWeek, List<IntRange>>,
    attributes: RecurringAttributes,
    /** What the form collides with: on those weekdays the mark is drawn red. */
    overlaps: List<RecurringOverlap>,
    rhythm: Rhythm = Rhythm.Weekly,
    modifier: Modifier = Modifier,
) {
    val draft = remember(attributes, rhythm) { slotOccupancy(attributes.slots(rhythm)) }
    val clashDays = remember(overlaps) { overlaps.flatMapTo(mutableSetOf()) { it.weekdays } }
    WeekSchemeChart(
        booked = booked,
        modifier = modifier,
        draft = draft,
        height = 112.dp,
        clashDays = clashDays,
    )
}

private fun categoryLabel(category: Category): String = when (category) {
    Category.FOKUS -> "Fokus"
    Category.NEBENBEI -> "Nebenbei"
    Category.ACHTSAM -> "Achtsam"
}
